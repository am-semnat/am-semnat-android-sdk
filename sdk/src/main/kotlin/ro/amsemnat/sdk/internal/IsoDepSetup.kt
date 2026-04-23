package ro.amsemnat.sdk.internal

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CardService
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import ro.amsemnat.sdk.AmSemnatError

internal const val ISO_DEP_TIMEOUT = 10_000

internal fun openPassportService(isoDep: IsoDep): PassportService {
    if (!isoDep.isConnected) isoDep.connect()
    isoDep.timeout = ISO_DEP_TIMEOUT

    val cardService = CardService.getInstance(isoDep)
    cardService.open()

    return PassportService(
        cardService,
        PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
        PassportService.DEFAULT_MAX_BLOCKSIZE,
        false,
        false,
    ).also { it.open() }
}

internal fun PassportService.readPaceInfo(): PACEInfo {
    val cardAccessFile = CardAccessFile(
        getInputStream(PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE)
    )
    return cardAccessFile.securityInfos.filterIsInstance<PACEInfo>().firstOrNull()
        ?: throw AmSemnatError.PaceAuthFailed
}
