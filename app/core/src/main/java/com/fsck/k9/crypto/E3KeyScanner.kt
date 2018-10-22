package com.fsck.k9.crypto

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.SimpleMessagingListener
import com.fsck.k9.mail.Address
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.search.LocalSearch
import com.fsck.k9.search.SearchSpecification
import timber.log.Timber
import java.util.ArrayList
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue

class E3KeyScanner(private val context: Context) {

    fun scanRemote(account: Account, tempEnableRemoteSearch: Boolean): E3KeyScanResult {
        var flippedRemoteSearch = false
        try {
            val address = Address.parse(account.getIdentity(0).email)[0]
            val controller = MessagingController.getInstance(context)

            if (!account.allowRemoteSearch() && tempEnableRemoteSearch) {
                Timber.d("Temporarily enabling remote search")
                account.setAllowRemoteSearch(true)
                flippedRemoteSearch = true
            }

            val search = LocalSearch()

            search.isManualSearch = true
            search.addAccountUuid(account.uuid)
            search.and(SearchSpecification.SearchCondition(SearchSpecification.SearchField.SENDER, SearchSpecification.Attribute.CONTAINS, address.address))
            search.and(SearchSpecification.SearchCondition(SearchSpecification.SearchField.SUBJECT, SearchSpecification.Attribute.CONTAINS, "E3"))
            //search.and(SearchCondition(SearchField.MESSAGE_CONTENTS, Attribute.CONTAINS, "E3"))

            val queue = SynchronousQueue<List<LocalMessage>>()

            // Search remote first which will add them to the database locally (if user allowed remote search)
            val folderId = account.inboxFolder
            val searchString = search.remoteSearchArguments
            val listener = E3ScanRemoteListener()
            controller.searchRemoteMessages(account.uuid, folderId, searchString, null, null, listener)

            // Do local search now which should include any remote search results
            val localListener = E3ScanLocalMessageInfoHolderListener(queue)
            controller.searchLocalMessages(search, localListener)

            val holders = queue.take()

            for (holder: LocalMessage in holders) {
                Timber.i("Found message from ${holder.from[0]}")
            }

            if (holders.isEmpty()) {
                Timber.w("Scanned but found no E3 keys in ${address.address}")
            }

            return E3KeyScanResult(holders)
        } finally {
            if (flippedRemoteSearch) {
                Timber.d("Resetting remote search to false")
                account.setAllowRemoteSearch(false)
            }
        }
    }
}

data class E3KeyScanResult(val results: List<LocalMessage>)

class E3ScanLocalMessageInfoHolderListener(
        private val outputQueue: BlockingQueue<List<LocalMessage>>
) : SimpleMessagingListener() {
    private val collectedResults = ArrayList<LocalMessage>()

    override fun listLocalMessagesAddMessages(account: Account, folderServerId: String?, messages: List<LocalMessage>) {
        Timber.i("adding discovered message")
        collectedResults.addAll(messages)
    }

    override fun searchStats(stats: AccountStats) {
        try {
            outputQueue.put(collectedResults)
        } catch (e: InterruptedException) {
            Timber.e(e, "Unable to return message list back to caller")
        }

    }
}

class E3ScanRemoteListener : SimpleMessagingListener() {
    override fun remoteSearchFailed(folderServerId: String, err: String) {
        Timber.e("Remote search failed for $folderServerId: $err")
    }

    override fun remoteSearchStarted(folder: String) {
        Timber.d("Starting remote search in folder: $folder")
    }

    override fun remoteSearchServerQueryComplete(folderServerId: String, numResults: Int, maxResults: Int) {
        Timber.d("Remote search server query complete, got $numResults results")
    }
}