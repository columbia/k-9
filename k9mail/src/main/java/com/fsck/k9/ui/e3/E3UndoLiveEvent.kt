package com.fsck.k9.ui.e3

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.activity.FolderInfoHolder
import com.fsck.k9.activity.MessageInfoHolder
import com.fsck.k9.autocrypt.AutocryptOperations
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.SimpleMessagingListener
import com.fsck.k9.crypto.E3Constants
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mailstore.CryptoResultAnnotation
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.search.LocalSearch
import com.fsck.k9.search.SearchSpecification
import com.fsck.k9.ui.crypto.MessageCryptoAnnotations
import com.fsck.k9.ui.crypto.MessageCryptoCallback
import com.fsck.k9.ui.crypto.MessageCryptoHelper
import com.fsck.k9.ui.crypto.OpenPgpApiFactory
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import timber.log.Timber
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue

class E3UndoLiveEvent(private val context: Context) : SingleLiveEvent<E3UndoResult>() {

    fun undoE3Async(account: Account) {
        launch(UI) {
            val scanResult = bg {
                undoE3(account)
            }

            value = try {
                val decryptedUids = scanResult.await()
                if (!decryptedUids.isEmpty()) {
                    E3UndoResult.Success(decryptedUids)
                } else {
                    E3UndoResult.NoneFound("no E3 encrypted emails were found")
                }
            } catch (e: Exception) {
                E3UndoResult.Failure(e)
            }
        }
    }

    private fun undoE3(account: Account): List<String> {
        val address = Address.parse(account.getIdentity(0).email)[0]
        val controller = MessagingController.getInstance(context)

        val search = LocalSearch()
        search.isManualSearch = true
        search.addAccountUuid(account.uuid)
        search.and(SearchSpecification.SearchCondition(SearchSpecification.SearchField.FLAG, SearchSpecification.Attribute.CONTAINS, "E3"))

        val queue = SynchronousQueue<List<MessageInfoHolder>>()
        val msgHelper = MessageHelper.getInstance(context)

        val localListener = E3UndoLocalMessageInfoHolderListener(context, msgHelper, queue)
        controller.searchLocalMessages(search, localListener)

        val holders: List<MessageInfoHolder> = queue.take()

        if (holders.isEmpty()) {
            Timber.w("Found no E3 messages in ${address.address}")
            return Collections.emptyList()
        }

        return decryptInBatches(account, holders)
    }

    private fun decryptInBatches(account: Account, messageHolders: List<MessageInfoHolder>, batchSize: Int = 10): List<String> {
        val decryptedUids = ArrayList<String>()
        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(FetchProfile.Item.BODY)

        // Convert input List of MessageInfoHolder to batches of List of LocalMessage that are E3 encrypted
        val chunkedMessages = messageHolders.filter { it -> it.message.headerNames.contains(E3Constants.MIME_E3_ENCRYPTED_HEADER) }
                .chunked(batchSize) { it.map { it.message } }

        for (batch: List<LocalMessage> in chunkedMessages) {
            if (batch.isEmpty()) {
                continue
            }

            val folder = batch[0].folder

            folder.fetch(batch, fp, null)
            decryptBatch(account, batch)
        }

        return decryptedUids
    }

    private fun decryptBatch(account: Account, messageBatch: List<LocalMessage>): BlockingQueue<MessageCryptoAnnotations?> {
        val e3Provider = account.e3Provider!!
        val decryptedResults = ArrayBlockingQueue<MessageCryptoAnnotations?>(messageBatch.size)

        for (message in messageBatch) {
            val cryptoHelper = MessageCryptoHelper(context, account, OpenPgpApiFactory(),
                    AutocryptOperations.getInstance(), e3Provider, account.e3Key)

            val cryptoCallback = object : MessageCryptoCallback {
                override fun onCryptoHelperProgress(current: Int, max: Int) {
                }

                override fun onCryptoOperationsFinished(annotations: MessageCryptoAnnotations?) {
                    Timber.d("Decrypt E3 message completed: ${message.subject}, $annotations")
                    val cryptoResultAnnotation = annotations!!.get(message)
                    if (cryptoResultAnnotation.errorType == CryptoResultAnnotation.CryptoError.OPENPGP_OK) {
                        decryptedResults.add(annotations)
                    } else {
                        Timber.e("Got annotations with non-OK CryptoError: ${cryptoResultAnnotation.errorType}")
                    }
                }

                override fun startPendingIntentForCryptoHelper(si: IntentSender?, requestCode: Int,
                                                               fillIntent: Intent?, flagsMask: Int,
                                                               flagValues: Int, extraFlags: Int) {
                    Timber.d("IntentSender=$si, requestCode=$requestCode, Intent=$fillIntent")
                }
            }

            cryptoHelper.asyncStartOrResumeProcessingMessage(message, cryptoCallback, null, !account.openPgpHideSignOnly)
        }

        return decryptedResults
    }
}

sealed class E3UndoResult {
    data class Success(val decryptedUids: List<String>) : E3UndoResult()
    data class Failure(val exception: Exception) : E3UndoResult()
    data class NoneFound(val msg: String) : E3UndoResult()
}

class E3UndoLocalMessageInfoHolderListener(private val context: Context,
                                           private val messageHelper: MessageHelper,
                                           private val outputQueue: BlockingQueue<List<MessageInfoHolder>>) : SimpleMessagingListener() {
    private val collectedResults = java.util.ArrayList<MessageInfoHolder>()

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