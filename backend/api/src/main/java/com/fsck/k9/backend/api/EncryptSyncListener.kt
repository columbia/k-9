package com.fsck.k9.backend.api

/**
 * TODO: E3 figure out a better way to deal with ImapSync
 */
interface EncryptSyncListener<T> {
    fun asyncEncryptSync(message: T, listener: SyncUpdatedListener)
}