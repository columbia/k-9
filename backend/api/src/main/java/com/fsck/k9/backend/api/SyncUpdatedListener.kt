package com.fsck.k9.backend.api

import com.fsck.k9.mail.Message

/**
 * TODO: E3 figure out a better way to deal with ImapSync
 */
interface SyncUpdatedListener {
    fun updateWithNewMessage(message: Message)
}