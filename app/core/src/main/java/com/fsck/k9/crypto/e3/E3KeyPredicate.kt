package com.fsck.k9.crypto.e3

import com.fsck.k9.mailstore.LocalMessage

class E3KeyPredicate {
    fun apply(localMessage: LocalMessage): Boolean {
        return localMessage.headerNames.containsAll(requiredHeaders) && isFresh(localMessage)
    }

    private fun isFresh(localMessage: LocalMessage): Boolean {
        val timestamp = localMessage.getHeader(E3Constants.MIME_E3_TIMESTAMP)[0].toLongOrNull()

        return timestamp != null && (timestamp <= System.currentTimeMillis() + SIXTY_SECONDS_MS)
    }

    companion object {
        private val requiredHeaders = listOf(
                E3Constants.MIME_E3_VERIFICATION,
                E3Constants.MIME_E3_NAME,
                E3Constants.MIME_E3_TIMESTAMP
        )
        private const val SIXTY_SECONDS_MS: Long = 60 * 1000L
    }
}
