package com.fsck.k9.crypto.e3

import com.fsck.k9.Account
import com.fsck.k9.mailstore.LocalMessage
import org.openintents.openpgp.util.OpenPgpApi

class E3KeyPredicate(private val openPgpApi: OpenPgpApi, private val account: Account) {

    fun apply(localMessage: LocalMessage): Boolean {
        return localMessage.headerNames.containsAll(requiredHeaders)
                && isFresh(localMessage)
                && !isOwnKey(localMessage)
                && hasTrustedSignature(localMessage)
    }

    private fun isOwnKey(localMessage: LocalMessage): Boolean {
        val uuidHeader = localMessage.getHeader(E3Constants.MIME_E3_UID)[0]
        return uuidHeader != null
                && localMessage.getHeader(E3Constants.MIME_E3_UID)[0].toLowerCase() == account.uuid.toLowerCase()
    }

    private fun isFresh(localMessage: LocalMessage): Boolean {
        val timestamp = localMessage.getHeader(E3Constants.MIME_E3_TIMESTAMP)[0].toLongOrNull()

        return timestamp != null && (timestamp <= System.currentTimeMillis() + SIXTY_SECONDS_MS)
    }

    private fun hasTrustedSignature(localMessage: LocalMessage): Boolean {
        val e3Verifier = E3HeaderSigner(openPgpApi)
        return e3Verifier.verifyE3Headers(localMessage)
    }

    companion object {
        private val requiredHeaders = listOf(
                E3Constants.MIME_E3_VERIFICATION,
                E3Constants.MIME_E3_NAME,
                E3Constants.MIME_E3_TIMESTAMP,
                E3Constants.MIME_E3_UID
        )
        private const val SIXTY_SECONDS_MS: Long = 60 * 1000L

        @JvmStatic
        fun applyNonStrict(localMessage: LocalMessage): Boolean {
            return localMessage.headerNames.containsAll(requiredHeaders)
        }
    }
}
