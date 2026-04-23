package ro.amsemnat.sdk

/**
 * Flat snapshot of the personal data read from a Romanian CEI eID card, merging fields from the
 * MRZ (DG1), the face and displayed-signature images (DG2, DG7), personal-details groups (DG11),
 * and the national eDATA applet.
 *
 * **All string fields are nullable.** Consumers choose which data groups to read via the
 * `dataGroups` argument on [AmSemnat.readIdentity]; fields sourced from groups that were not
 * requested — or that are absent on a given card — come back as `null`.
 *
 * Field provenance:
 * - MRZ (DG1) → [documentNumber], [dateOfExpiry], [sex], [nationality], and seed values for [firstName]/[lastName]/[dateOfBirth]
 * - DG2 → [faceImage]
 * - DG7 → [signatureImage]
 * - DG11 → [placeOfBirth], [address] (and higher-quality [firstName]/[lastName])
 * - National eDATA applet → [cnp], [issuingAuthority], [issuingDate], canonical address
 *
 * @property cnp 13-digit national identification number. Requires the national eDATA applet (PIN1).
 * @property firstName Given name(s). UTF-8 from DG11 when present, otherwise MRZ-derived.
 * @property lastName Family name. UTF-8 from DG11 when present, otherwise MRZ-derived.
 * @property dateOfBirth ISO-8601 `YYYY-MM-DD` when expandable from MRZ/DG11, otherwise `null`.
 * @property sex `"M"` or `"F"` as recorded in the MRZ.
 * @property nationality Three-letter country code from the MRZ (e.g. `"ROU"`).
 * @property documentNumber Card number from the MRZ.
 * @property dateOfExpiry ISO-8601 `YYYY-MM-DD` derived from the MRZ.
 * @property placeOfBirth Free-form locality string from DG11.
 * @property address Free-form postal address from DG11 or the eDATA applet.
 * @property issuingAuthority Issuer name from the eDATA applet.
 * @property issuingDate ISO-8601 `YYYY-MM-DD` issuance date from the eDATA applet.
 * @property faceImage Raw encoded face image bytes from DG2 (JPEG or JPEG 2000).
 * @property signatureImage Raw encoded displayed-signature image bytes from DG7.
 * @property chipAuthenticated `true` iff chip authentication succeeded using DG14. When `false`
 *   the card's identity claims have not been cryptographically bound to the chip.
 * @property rawSod DER bytes of the LDS Security Object. Pair with [rawDg1]/[rawDg2]/[rawDg14] and
 *   pass to [AmSemnat.verifyPassiveOffline] to run passive authentication later.
 * @property rawDg1 Full TLV bytes of DG1 as read (including the outer tag/length), suitable for hashing.
 * @property rawDg2 Full TLV bytes of DG2 as read.
 * @property rawDg14 Full TLV bytes of DG14 as read.
 */
data class RomanianIdentity(
    val cnp: String?,
    val firstName: String?,
    val lastName: String?,
    val dateOfBirth: String?,
    val sex: String?,
    val nationality: String?,
    val documentNumber: String?,
    val dateOfExpiry: String?,
    val placeOfBirth: String?,
    val address: String?,
    val issuingAuthority: String?,
    val issuingDate: String?,
    val faceImage: ByteArray?,
    val signatureImage: ByteArray?,
    val chipAuthenticated: Boolean,
    val rawSod: ByteArray?,
    val rawDg1: ByteArray?,
    val rawDg2: ByteArray?,
    val rawDg14: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RomanianIdentity) return false
        return cnp == other.cnp &&
            firstName == other.firstName &&
            lastName == other.lastName &&
            dateOfBirth == other.dateOfBirth &&
            sex == other.sex &&
            nationality == other.nationality &&
            documentNumber == other.documentNumber &&
            dateOfExpiry == other.dateOfExpiry &&
            placeOfBirth == other.placeOfBirth &&
            address == other.address &&
            issuingAuthority == other.issuingAuthority &&
            issuingDate == other.issuingDate &&
            faceImage.contentEquals(other.faceImage) &&
            signatureImage.contentEquals(other.signatureImage) &&
            chipAuthenticated == other.chipAuthenticated &&
            rawSod.contentEquals(other.rawSod) &&
            rawDg1.contentEquals(other.rawDg1) &&
            rawDg2.contentEquals(other.rawDg2) &&
            rawDg14.contentEquals(other.rawDg14)
    }

    override fun hashCode(): Int {
        var result = cnp?.hashCode() ?: 0
        result = 31 * result + (firstName?.hashCode() ?: 0)
        result = 31 * result + (lastName?.hashCode() ?: 0)
        result = 31 * result + (dateOfBirth?.hashCode() ?: 0)
        result = 31 * result + (sex?.hashCode() ?: 0)
        result = 31 * result + (nationality?.hashCode() ?: 0)
        result = 31 * result + (documentNumber?.hashCode() ?: 0)
        result = 31 * result + (dateOfExpiry?.hashCode() ?: 0)
        result = 31 * result + (placeOfBirth?.hashCode() ?: 0)
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + (issuingAuthority?.hashCode() ?: 0)
        result = 31 * result + (issuingDate?.hashCode() ?: 0)
        result = 31 * result + (faceImage?.contentHashCode() ?: 0)
        result = 31 * result + (signatureImage?.contentHashCode() ?: 0)
        result = 31 * result + chipAuthenticated.hashCode()
        result = 31 * result + (rawSod?.contentHashCode() ?: 0)
        result = 31 * result + (rawDg1?.contentHashCode() ?: 0)
        result = 31 * result + (rawDg2?.contentHashCode() ?: 0)
        result = 31 * result + (rawDg14?.contentHashCode() ?: 0)
        return result
    }
}
