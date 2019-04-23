package com.fsck.k9.backend.imap


import com.fsck.k9.mail.Flag
import com.fsck.k9.mail.store.imap.ImapStore
import com.fsck.k9.mail.store.imap.UidSearchCommandBuilder


internal class CommandSearch(private val imapStore: ImapStore) {

    fun search(
            folderServerId: String,
            query: String?,
            requiredFlags: Set<Flag>?,
            forbiddenFlags: Set<Flag>?,
            searchHeaders: Boolean
    ): List<String> {
        val folder = imapStore.getFolder(folderServerId)
        try {

            val searchCommand = UidSearchCommandBuilder()
                    .queryString(query)
                    .performFullTextSearch(imapStore.storeConfig.isRemoteSearchFullText)
                    .performHeaderSearch(searchHeaders)
                    .requiredFlags(requiredFlags)
                    .forbiddenFlags(forbiddenFlags)
                    .build()

            return folder.search(searchCommand, requiredFlags, forbiddenFlags)
                    .sortedWith(UidReverseComparator())
                    .map { it.uid }
        } finally {
            folder.close()
        }
    }
}
