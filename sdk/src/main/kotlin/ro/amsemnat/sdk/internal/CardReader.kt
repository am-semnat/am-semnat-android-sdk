package ro.amsemnat.sdk.internal

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.ChipAuthenticationInfo
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo
import org.jmrtd.lds.LDSFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.COMFile
import org.jmrtd.lds.icao.DG11File
import org.jmrtd.lds.icao.DG14File
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.DG7File
import ro.amsemnat.sdk.AmSemnatError
import ro.amsemnat.sdk.AmSemnatLogger
import ro.amsemnat.sdk.DataGroup
import ro.amsemnat.sdk.ReadProgress
import ro.amsemnat.sdk.RomanianIdentity
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.PublicKey

internal suspend fun readIdentityFromIsoDep(
    isoDep: IsoDep,
    can: String,
    pin1: String,
    dataGroups: Set<DataGroup>,
    onProgress: ((ReadProgress) -> Unit)?,
    logger: AmSemnatLogger?,
): RomanianIdentity = withContext(Dispatchers.IO) {
    try {
        val passportService = openPassportService(isoDep)

        onProgress?.invoke(ReadProgress.PACE_ESTABLISHING)
        val paceInfo = try {
            passportService.readPaceInfo().also { pace ->
                passportService.doPACE(
                    PACEKeySpec.createCANKey(can),
                    pace.objectIdentifier,
                    PACEInfo.toParameterSpec(pace.parameterId),
                    pace.parameterId,
                )
                passportService.sendSelectApplet(true)
            }
        } catch (e: AmSemnatError) {
            throw e
        } catch (e: Exception) {
            logger?.debug("PACE failed: ${e.message}")
            throw AmSemnatError.PaceAuthFailed
        }

        var rawDg1: ByteArray? = null
        var rawDg2: ByteArray? = null
        var rawDg14: ByteArray? = null
        var rawSod: ByteArray? = null
        var mrzFields: MrzFields? = null
        var faceImage: ByteArray? = null
        var signatureImage: ByteArray? = null
        var dg11Place: String? = null
        var dg11Address: String? = null

        // Read COM and intersect the caller's requested DGs with what the card
        // actually advertises. Prevents firing a progress event for a DG that
        // doesn't exist on the card (e.g. Romanian CEI cards don't ship DG11)
        // and mirrors the iOS vendored reader's pre-filter behavior. If COM
        // can't be read, fall back to the caller's set unfiltered — better
        // to attempt and fail than drop everything.
        val presentDGs = try {
            val comBytes = passportService
                .getInputStream(PassportService.EF_COM, PassportService.DEFAULT_MAX_BLOCKSIZE)
                .readBytes()
            COMFile(ByteArrayInputStream(comBytes)).tagList.toSet()
        } catch (e: Exception) {
            logger?.debug("COM read failed; reading all requested DGs without filter: ${e.message}")
            null
        }
        val effectiveDGs = if (presentDGs == null) dataGroups else dataGroups.filter { it.comTag in presentDGs }.toSet()
        val skipped = dataGroups - effectiveDGs
        if (skipped.isNotEmpty()) logger?.debug("DGs not listed in COM, skipping: $skipped")

        for (group in effectiveDGs) {
            try {
                when (group) {
                    DataGroup.DG1 -> {
                        onProgress?.invoke(ReadProgress.READING_DG1)
                        val (raw, fields) = readDG1(passportService)
                        rawDg1 = raw
                        mrzFields = fields
                    }
                    DataGroup.DG2 -> {
                        onProgress?.invoke(ReadProgress.READING_DG2)
                        val (raw, face) = readDG2(passportService)
                        rawDg2 = raw
                        faceImage = face
                    }
                    DataGroup.DG7 -> {
                        onProgress?.invoke(ReadProgress.READING_DG7)
                        signatureImage = readDG7(passportService)
                    }
                    DataGroup.DG11 -> {
                        onProgress?.invoke(ReadProgress.READING_DG11)
                        val (place, addr) = readDG11(passportService)
                        dg11Place = place
                        dg11Address = addr
                    }
                    DataGroup.DG14 -> {
                        onProgress?.invoke(ReadProgress.READING_DG14)
                        rawDg14 = readRawDataGroup(passportService, PassportService.EF_DG14)
                    }
                }
            } catch (e: Exception) {
                logger?.debug("Failed reading $group: ${e.message}")
            }
        }

        try {
            val sodRaw = readRawDataGroup(passportService, PassportService.EF_SOD)
            rawSod = stripTlvHeader(sodRaw)
        } catch (e: Exception) {
            logger?.debug("SOD read failed: ${e.message}")
        }

        var chipAuthenticated = false
        if (rawDg14 != null || DataGroup.DG14 in effectiveDGs) {
            onProgress?.invoke(ReadProgress.CHIP_AUTHENTICATING)
            try {
                val dg14Bytes = rawDg14 ?: readRawDataGroup(passportService, PassportService.EF_DG14).also {
                    rawDg14 = it
                }
                val dg14SecurityInfos = DG14File(ByteArrayInputStream(dg14Bytes)).securityInfos

                val chipAuthInfo = dg14SecurityInfos
                    .filterIsInstance<ChipAuthenticationInfo>()
                    .firstOrNull()

                val chipAuthPubKeyInfo = dg14SecurityInfos
                    .filterIsInstance<ChipAuthenticationPublicKeyInfo>()
                    .firstOrNull()

                if (chipAuthPubKeyInfo != null) {
                    val keyId: BigInteger? = chipAuthInfo?.keyId ?: chipAuthPubKeyInfo.keyId
                    val oid: String = chipAuthInfo?.objectIdentifier
                        ?: chipAuthPubKeyInfo.objectIdentifier
                    val publicKeyOID: String = chipAuthPubKeyInfo.objectIdentifier
                    val publicKey: PublicKey = chipAuthPubKeyInfo.subjectPublicKey

                    passportService.doEACCA(keyId, oid, publicKeyOID, publicKey)
                    chipAuthenticated = true
                }
            } catch (e: Exception) {
                logger?.debug("Chip authentication failed: ${e.message}")
            }
        }

        var eData = EData()
        if (pin1.isNotEmpty()) {
            onProgress?.invoke(ReadProgress.READING_EDATA)
            try {
                eData = EDataReader(isoDep, can, pin1, paceInfo, logger).readEData()
            } catch (e: EDataPinException) {
                throw AmSemnatError.PinVerifyFailed(e.retriesRemaining)
            } catch (e: EDataPinBlockedException) {
                throw AmSemnatError.PinBlocked
            } catch (e: Exception) {
                logger?.debug("eDATA read failed: ${e.message}")
            }
        }

        onProgress?.invoke(ReadProgress.COMPLETE)

        // Date normalization: MRZ dates are YYMMDD; eDATA dates are DDMMYYYY.
        // Both get expanded to YYYY-MM-DD. MRZ wins when parseable — checksum-
        // protected, and matches the eDATA value on CEI cards anyway.
        val dateOfBirth = isoDateFromMrzSixDigit(mrzFields?.dateOfBirth, DateWindow.BIRTH)
            ?: isoDateFromEDataDDMMYYYY(eData.dateOfBirth)
        val dateOfExpiry = isoDateFromMrzSixDigit(mrzFields?.dateOfExpiry, DateWindow.FUTURE)
            ?: isoDateFromEDataDDMMYYYY(eData.dateOfExpiry)
        val issuingDate = isoDateFromEDataDDMMYYYY(eData.issuingDate)

        RomanianIdentity(
            cnp = eData.cnp ?: mrzFields?.cnp,
            firstName = eData.firstName ?: mrzFields?.firstName,
            lastName = eData.lastName ?: mrzFields?.lastName,
            dateOfBirth = dateOfBirth,
            sex = eData.sex ?: mrzFields?.sex,
            nationality = eData.nationality ?: mrzFields?.nationality,
            documentNumber = eData.documentNumber ?: mrzFields?.documentNumber,
            dateOfExpiry = dateOfExpiry,
            placeOfBirth = eData.placeOfBirth ?: dg11Place,
            address = eData.address ?: dg11Address,
            issuingAuthority = eData.issuingAuthority,
            issuingDate = issuingDate,
            faceImage = faceImage,
            signatureImage = signatureImage,
            chipAuthenticated = chipAuthenticated,
            rawSod = rawSod,
            rawDg1 = rawDg1,
            rawDg2 = rawDg2,
            rawDg14 = rawDg14,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: AmSemnatError) {
        throw e
    } catch (e: TagLostException) {
        throw AmSemnatError.TagLost
    } catch (e: Exception) {
        logger?.error("Read failed", e)
        throw AmSemnatError.ReadFailed(e.message ?: "unknown error")
    }
}

private data class MrzFields(
    val cnp: String?,
    val firstName: String?,
    val lastName: String?,
    val dateOfBirth: String?,
    val sex: String?,
    val nationality: String?,
    val documentNumber: String?,
    val dateOfExpiry: String?,
)

private fun readDG1(service: PassportService): Pair<ByteArray, MrzFields> {
    val raw = service.getInputStream(PassportService.EF_DG1, PassportService.DEFAULT_MAX_BLOCKSIZE).readBytes()
    val mrz = DG1File(ByteArrayInputStream(raw)).mrzInfo

    val cnp = mrz.optionalData1?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    val sex = mrz.gender?.toString()?.take(1)

    return raw to MrzFields(
        cnp = cnp,
        firstName = mrz.secondaryIdentifier,
        lastName = mrz.primaryIdentifier,
        dateOfBirth = mrz.dateOfBirth,
        sex = sex,
        nationality = mrz.nationality,
        documentNumber = mrz.documentNumber,
        dateOfExpiry = mrz.dateOfExpiry,
    )
}

private fun readDG2(service: PassportService): Pair<ByteArray, ByteArray?> {
    val raw = service.getInputStream(PassportService.EF_DG2, PassportService.DEFAULT_MAX_BLOCKSIZE).readBytes()
    val face = DG2File(ByteArrayInputStream(raw)).faceInfos.firstOrNull()?.faceImageInfos?.firstOrNull()
    val image = face?.imageInputStream?.readBytes()
    return raw to image
}

private fun readDG7(service: PassportService): ByteArray? {
    val stream = service.getInputStream(PassportService.EF_DG7, PassportService.DEFAULT_MAX_BLOCKSIZE)
    return DG7File(stream).images.firstOrNull()?.imageInputStream?.readBytes()
}

private fun readDG11(service: PassportService): Pair<String?, String?> {
    val stream = service.getInputStream(PassportService.EF_DG11, PassportService.DEFAULT_MAX_BLOCKSIZE)
    val dg11 = DG11File(stream)
    val place = dg11.placeOfBirth?.joinToString(", ")?.takeIf { it.isNotEmpty() }
    val address = try {
        dg11.permanentAddress?.joinToString(", ")?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
    return place to address
}

private fun readRawDataGroup(service: PassportService, fileId: Short): ByteArray =
    service.getInputStream(fileId, PassportService.DEFAULT_MAX_BLOCKSIZE).readBytes()

// Strip a single-byte BER tag + BER length header from the outer SOD TLV.
// ICAO 9303 SOD is `77 <len> <CMS SignedData>`, so the content is everything
// past the tag/length prefix.
private fun stripTlvHeader(tlvBytes: ByteArray): ByteArray {
    var offset = 1
    val lenByte = tlvBytes[offset].toInt() and 0xFF
    offset++
    val contentLen: Int
    if (lenByte < 0x80) {
        contentLen = lenByte
    } else {
        val numLenBytes = lenByte and 0x7F
        contentLen = (0 until numLenBytes).fold(0) { acc, i ->
            (acc shl 8) or (tlvBytes[offset + i].toInt() and 0xFF)
        }
        offset += numLenBytes
    }
    return tlvBytes.copyOfRange(offset, offset + contentLen)
}
