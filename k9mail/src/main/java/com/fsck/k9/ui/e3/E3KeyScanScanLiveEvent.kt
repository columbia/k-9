package com.fsck.k9.ui.e3

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.activity.FolderInfoHolder
import com.fsck.k9.activity.MessageInfoHolder
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.SimpleMessagingListener
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Message
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.search.LocalSearch
import com.fsck.k9.search.SearchSpecification.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import timber.log.Timber
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue

class E3KeyScanScanLiveEvent(private val context: Context) : SingleLiveEvent<E3KeyScanResult>() {

    fun scanRemoteE3KeysAsync(account: Account, listener: E3KeyScanListener?) {
        launch(UI) {
            val scanResult = bg {
                scanRemote(account, listener)
            }

            value = scanResult.await()
        }
    }

    private fun scanRemote(account: Account, keyScanListener: E3KeyScanListener?): E3KeyScanResult {
        try {
            val address = Address.parse(account.getIdentity(0).email)[0]
            val controller = MessagingController.getInstance(context)
            val search = LocalSearch()

            search.isManualSearch = true
            search.addAccountUuid(account.uuid)
            search.and(SearchCondition(SearchField.SENDER, Attribute.CONTAINS, address.address))
            search.and(SearchCondition(SearchField.SUBJECT, Attribute.CONTAINS, "E3"))
            //search.and(SearchCondition(SearchField.MESSAGE_CONTENTS, Attribute.CONTAINS, "E3"))

            val queue = SynchronousQueue<List<MessageInfoHolder>>()
            val msgHelper = MessageHelper.getInstance(context)

            // Search remote first which will add them to the database locally (if user allowed remote search)
            val folderId = account.inboxFolder
            val searchString = search.remoteSearchArguments
            val listener = E3ScanRemoteListener()
            controller.searchRemoteMessages(account.uuid, folderId, searchString, null, null, listener)

            // Do local search now which should include any remote search results
            val localListener = E3ScanLocalMessageInfoHolderListener(context, msgHelper, queue)
            controller.searchLocalMessages(search, localListener)

            val holders = queue.take()

            for (holder: MessageInfoHolder in holders) {
                Timber.i("Found message from ${holder.senderAddress}")
            }

            if (holders.isEmpty()) {
                Timber.w("Scanned but found no E3 keys in ${address.address}")
            }

            return E3KeyScanResult(holders)
        } finally {
            keyScanListener?.keySearchFinished()
        }
    }
}

data class E3KeyScanResult(val results: List<MessageInfoHolder>)

class E3ScanLocalMessageInfoHolderListener(private val context: Context,
                                           private val messageHelper: MessageHelper,
                                           private val outputQueue: BlockingQueue<List<MessageInfoHolder>>) : SimpleMessagingListener() {
    private val collectedResults = ArrayList<MessageInfoHolder>()

    override fun listLocalMessagesAddMessages(account: Account, folderServerId: String?, messages: List<LocalMessage>) {
        Timber.i("adding discovered message")
        for (message in messages) {
            val messageInfoHolder = MessageInfoHolder()
            val messageFolder = message.folder
            val messageAccount = message.account

            val folderInfoHolder = FolderInfoHolder(context, messageFolder, messageAccount)
            messageHelper.populate(messageInfoHolder, message, folderInfoHolder, messageAccount)

            collectedResults.add(messageInfoHolder)
        }
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

    override fun remoteSearchFinished(folderServerId: String, numResults: Int, maxResults: Int, extraResults: List<Message>?) {
        Timber.d("Remote search finished, got $numResults results")
    }

    override fun remoteSearchServerQueryComplete(folderServerId: String, numResults: Int, maxResults: Int) {
        Timber.d("Remote search server query complete, got $numResults results")
    }
}