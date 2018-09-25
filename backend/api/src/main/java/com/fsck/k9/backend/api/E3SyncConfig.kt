package com.fsck.k9.backend.api

import android.text.TextUtils
import com.fsck.k9.mail.Message

data class E3SyncConfig(
        val accountEmail: String,
        val e3Provider: String?,
        val e3Mode: E3ModeBackend,
        val e3KeyId: Long?,
        val encryptSyncListener: EncryptSyncListener<Message>
) {

    companion object {
        @JvmStatic val MIME_E3_NAME: String = "X-E3-NAME"
    }

    enum class E3ModeBackend {
        STANDALONE,
        PASSIVE
    }

    fun isE3ProviderConfigured(): Boolean {
        return !TextUtils.isEmpty(e3Provider)
    }
}
