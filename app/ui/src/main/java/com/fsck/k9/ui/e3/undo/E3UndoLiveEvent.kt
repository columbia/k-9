package com.fsck.k9.ui.e3.undo

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.MessagingControllerCommands.*
import com.fsck.k9.controller.SimpleMessagingListener
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.crypto.e3.SimpleE3PgpDecryptor
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.*
import com.fsck.k9.mail.internet.*
import com.fsck.k9.mailstore.LocalFolder
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.mailstore.MessageCryptoAnnotations
import com.fsck.k9.search.LocalSearch
import com.fsck.k9.search.SearchSpecification
import com.fsck.k9.ui.e3.E3EnableDisableToggler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import timber.log.Timber
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue

class E3UndoLiveEvent(private val context: Context) : SingleLiveEvent<E3UndoResult>() {
    private val messagingController = MessagingController.getInstance(context)
    private val fetchProfile = FetchProfile()
    private val e3Toggler = E3EnableDisableToggler(context)

    init {
        fetchProfile.add(FetchProfile.Item.ENVELOPE)
        fetchProfile.add(FetchProfile.Item.BODY)
    }

    fun undoE3Async(account: Account) {
        e3Toggler.setE3DisabledState(account)

        GlobalScope.launch(Dispatchers.Main) {
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

        val queue = SynchronousQueue<List<LocalMessage>>()
        val msgHelper = MessageHelper.getInstance(context)

        val localListener = E3UndoLocalMessageInfoHolderListener(context, msgHelper, queue)
        controller.searchLocalMessages(search, localListener)

        val holders: List<LocalMessage> = queue.take()

        if (holders.isEmpty()) {
            Timber.w("Found no E3 messages in ${address.address}")
            return Collections.emptyList()
        }

        return decryptInBatches(account, holders)
    }

    private fun decryptInBatches(account: Account, messageHolders: List<LocalMessage>, batchSize: Int = 10): List<String> {
        val decryptedUids = ArrayList<String>()

        // Convert input to batches of List of LocalMessage that are E3 encrypted
        val chunkedMessages = messageHolders.filter { it -> it.headerNames.contains(E3Constants.MIME_E3_ENCRYPTED_HEADER) }
                .chunked(batchSize)

        for (batch: List<LocalMessage> in chunkedMessages) {
            if (batch.isEmpty()) {
                continue
            }

            val folder = batch[0].folder

            folder.fetch(batch, fetchProfile, null)
            decryptBatch(account, batch)
        }

        return decryptedUids
    }

    private fun decryptBatch(account: Account, messageBatch: List<LocalMessage>): BlockingQueue<MessageCryptoAnnotations?> {
        val e3Provider = account.e3Provider!!
        val decryptedResults = ArrayBlockingQueue<MessageCryptoAnnotations?>(messageBatch.size)

        for (message in messageBatch) {
            val pgpServiceConnection = OpenPgpServiceConnection(context, e3Provider, object : OpenPgpServiceConnection.OnBound {
                override fun onBound(service: IOpenPgpService2) {
                    val openPgpApi = OpenPgpApi(context, service)
                    val decryptor = SimpleE3PgpDecryptor(openPgpApi, account.e3Key)

                    try {
                        val originalUid = message.uid
                        val decryptedMessage = decryptor.decrypt(message as MimeMessage, account.email)

                        Thread({
                            Timber.d("Synchronizing decrypted E3 message: ${message.subject} (originalUid=$originalUid, decryptedMessageUid=${decryptedMessage.uid}")
                            synchronizeDecrypted(account, message.folder, decryptedMessage, originalUid)
                        }).start()
                    } catch (e: MessagingException) {
                        Timber.e("Failed to decrypt message: ${message.subject}, likely because E3 encrypted using an unavailable key!", e)
                    }
                }

                override fun onError(e: Exception) {
                    Timber.e("Got error while binding to OpenPGP service", e)
                }
            })
            pgpServiceConnection.bindToService()
        }

        return decryptedResults
    }

    private fun synchronizeDecrypted(account: Account,
                                     localFolder: LocalFolder,
                                     decryptedMessage: MimeMessage,
                                     originalUid: String): LocalMessage {
        try {
            // Store the decrypted message locally
            val localMessage = synchronizeMessageLocally(localFolder, decryptedMessage)
            localMessage.setFlag(Flag.E3, false)
            localFolder.fetch(listOf(localMessage), fetchProfile, null)
            Timber.d("Synchronized message locally with uid=${localMessage.uid}")

            val trashFolder = account.trashFolder
            val folderIsTrash = trashFolder == localFolder.name
            val uidSingleton = listOf(originalUid)

            if (!folderIsTrash) {
                // First: Set \Deleted on the original (encrypted) message
                queueSetFlag(account, localFolder.name, true, Flag.DELETED, uidSingleton)

                // Second: Move original to Gmail's trash folder
                queueMoveOrCopy(account, localFolder.name, trashFolder, false, uidSingleton)
            }

            // Third: Append decrypted remotely
            Timber.d("Pending APPEND: ${localFolder.name}, ${decryptedMessage.uid}")
            val appendCmd = PendingAppend.create(localFolder.serverId, decryptedMessage.uid)
            queuePendingCommand(account, appendCmd)
            messagingController.processPendingCommandsSynchronous(account)

            if (!folderIsTrash) {
                // Fourth: Queue empty trash (expunge) command
                val emptyTrashCmd = PendingEmptyTrash.create()
                queuePendingCommand(account, emptyTrashCmd)
            }

            // Final: Run all the queued commands
            messagingController.processPendingCommandsSynchronous(account)

            return localMessage
        } catch (e: MessagingException) {
            throw RuntimeException("Failed to replace encrypted email with plaintext email", e)
        }
    }

    @Throws(MessagingException::class)
    private fun synchronizeMessageLocally(localFolder: LocalFolder, message: MimeMessage): LocalMessage {
        return localFolder.storeSmallMessage(message, Runnable { })
    }

    private fun queueSetFlag(account: Account, folderServerId: String,
                             newState: Boolean, flag: Flag, uids: List<String>) {
        val command = PendingSetFlag.create(folderServerId, newState, flag, uids)
        queuePendingCommand(account, command)
        messagingController.processPendingCommandsSynchronous(account)
    }

    private fun queuePendingCommand(account: Account, command: PendingCommand) {
        try {
            val localStore = account.localStore
            localStore.addPendingCommand(command)
        } catch (e: Exception) {
            throw RuntimeException("Unable to enqueue pending command", e)
        }
    }

    private fun queueMoveOrCopy(account: Account, srcFolder: String, destFolder: String, isCopy: Boolean,
                                uids: List<String>) {
        val command = PendingMoveOrCopy.create(srcFolder, destFolder, isCopy, uids)
        queuePendingCommand(account, command)
    }
}

sealed class E3UndoResult {
    data class Success(val decryptedUids: List<String>) : E3UndoResult()
    data class Failure(val exception: Exception) : E3UndoResult()
    data class NoneFound(val msg: String) : E3UndoResult()
}

class E3UndoLocalMessageInfoHolderListener(private val context: Context,
                                           private val messageHelper: MessageHelper,
                                           private val outputQueue: BlockingQueue<List<LocalMessage>>) : SimpleMessagingListener() {
    private val collectedResults = java.util.ArrayList<LocalMessage>()

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