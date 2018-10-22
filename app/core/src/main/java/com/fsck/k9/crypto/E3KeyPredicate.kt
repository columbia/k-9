package com.fsck.k9.crypto

import com.fsck.k9.mailstore.LocalMessage

class E3KeyPredicate {
    fun apply(localMessage: LocalMessage) : Boolean {
        return localMessage.headerNames.containsAll(requiredHeaders)
    }

    companion object {
        private val requiredHeaders = listOf(
                E3Constants.MIME_E3_VERIFICATION,
                E3Constants.MIME_E3_NAME
        )
    }
}
