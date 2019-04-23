package com.fsck.k9.crypto.e3

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import com.fsck.k9.Account
import com.fsck.k9.DI
import com.fsck.k9.Preferences
import com.fsck.k9.backend.BackendManager
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.api.SyncUpdatedListener
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.mailstore.LocalFolder
import com.fsck.k9.mailstore.LocalMessage
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import timber.log.Timber
import java.lang.Exception
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class E3UndoEncryptionManager private constructor() {

    fun startUndo(account: Account): Operation? {
        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

        val e3EncryptedMessageIds = scanForUids(account)

        Timber.d("Found ${e3EncryptedMessageIds.size} E3 encrypted emails to undo")

        if (e3EncryptedMessageIds.isEmpty()) {
            return null
        }

        val messageIdBatches = batchUids(e3EncryptedMessageIds)
        val workRequests = mutableListOf<OneTimeWorkRequest>()

        for (batch: List<String> in messageIdBatches) {
            val inputData = Data.Builder()
                    .putStringArray(WORKER_INPUT_KEY_MESSAGE_IDS, batch.toTypedArray())
                    .putString(WORKER_INPUT_KEY_ACCOUNT_UUID, account.uuid)
                    .build()
            val undoWorkRequest = OneTimeWorkRequestBuilder<UndoWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag(getTag(account))
                    .build()

            workRequests.add(undoWorkRequest)
        }

        return WorkManager.getInstance().enqueueUniqueWork(getTag(account), ExistingWorkPolicy.REPLACE, workRequests)
    }

    fun cancelUndo(account: Account) {
        WorkManager.getInstance().cancelUniqueWork(getTag(account))
    }

    fun getCurrentLiveData(account: Account): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance().getWorkInfosForUniqueWorkLiveData(getTag(account))
    }

    private fun scanForUids(account: Account): List<String> {
        var flippedRemoteSearch = false
        try {
            if (!account.allowRemoteSearch()) {
                Timber.d("Temporarily enabling remote search")
                account.setAllowRemoteSearch(true)
                flippedRemoteSearch = true
            }

            // Let's try bypassing the dumb SearchField thing
            val folderServerId = account.inboxFolder
            val backend = getBackend(account)

            // Find remote messages with E3 encryption
            return backend.searchHeaders(folderServerId, SEARCH_STRING, null, null)
        } finally {
            if (flippedRemoteSearch) {
                Timber.d("Resetting remote search to false")
                account.setAllowRemoteSearch(false)
            }
        }
    }

    private fun batchUids(allMessageIds: List<String>): List<List<String>> {
        return listOf(allMessageIds)
    }

    private fun getBackend(account: Account): Backend {
        return DI.get(BackendManager::class.java).getBackend(account)
    }

    private fun getTag(account: Account): String {
        return "$WORKER_TAG_SUFFIX_UNDO.${account.uuid}"
    }

    companion object {
        @JvmStatic
        val INSTANCE = E3UndoEncryptionManager()

        private const val SEARCH_STRING = "${E3Constants.MIME_E3_ENCRYPTED_HEADER} \"\""
        const val WORKER_INPUT_KEY_MESSAGE_IDS = "message_ids"
        const val WORKER_INPUT_KEY_ACCOUNT_UUID = "account_uuid"
        const val WORKER_OUTPUT_KEY = "message_ids"
        const val WORKER_TAG_SUFFIX_UNDO = "undo_e3"
    }
}

class UndoWorker(appContext: Context,
                 workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        try {
            val msgServerIds = inputData.getStringArray(E3UndoEncryptionManager.WORKER_INPUT_KEY_MESSAGE_IDS)!!.asList()
            val accountUuid = inputData.getString(E3UndoEncryptionManager.WORKER_INPUT_KEY_ACCOUNT_UUID)!!

            val account = Preferences.getPreferences(applicationContext).getAccount(accountUuid)

            val folderServerId = account.inboxFolder
            val localStore = account.localStore
            val localFolder = localStore.getFolder(folderServerId)
                    ?: throw MessagingException("Folder not found")

            // Download messages locally if they aren't already
            val nonLocalMsgIds = localFolder.extractNewMessages(msgServerIds)
            loadSearchResultsSynchronous(account, nonLocalMsgIds, localFolder)

            Timber.d("Got nonLocalMsgIds: ${nonLocalMsgIds.joinToString(",")}")

            // Only decrypt messages which have E3 encryption
            val allMessagesWithE3 = localFolder.getMessagesByUids(msgServerIds).filter { it.headerNames.contains(E3Constants.MIME_E3_ENCRYPTED_HEADER) }
            val decryptedStoredLocally = decryptBatch(account, allMessagesWithE3)

            // Remove any messages which we didn't have locally before to save space?
            //localFolder.destroyMessages(nonLocalMsgs)
            val outputData = workDataOf(E3UndoEncryptionManager.WORKER_OUTPUT_KEY to msgServerIds)
            return Result.success(outputData)
        } catch (e: Exception) {
            // Never use exceptions for codeflow (do as I say, not as I do)
            return Result.failure()
        }
    }

    private fun decryptBatch(account: Account, messageBatch: List<LocalMessage>): BlockingQueue<Message> {
        val messagingController = MessagingController.getInstance(applicationContext)
        val e3Provider = account.e3Provider!!
        val decryptedStoredLocally = ArrayBlockingQueue<Message>(messageBatch.size)

        val syncUpdatedListener = object : SyncUpdatedListener {
            override fun updateWithNewMessage(message: Message) {
                decryptedStoredLocally.add(message)
            }
        }

        for (message in messageBatch) {
            val pgpServiceConnection = OpenPgpServiceConnection(applicationContext, e3Provider, object : OpenPgpServiceConnection.OnBound {
                override fun onBound(service: IOpenPgpService2) {
                    val openPgpApi = OpenPgpApi(applicationContext, service)
                    val decryptor = SimpleE3PgpDecryptor(openPgpApi, account.e3Key)

                    try {
                        val decryptedMessage = decryptor.decrypt(message as MimeMessage, account.email)

                        Thread {
                            Timber.d("Synchronizing decrypted E3 message: ${message.subject} (originalUid=${message.uid}, decryptedMessageUid=${decryptedMessage.uid}")
                            messagingController.replaceExistingMessage(account, message.folder, message, decryptedMessage, syncUpdatedListener)
                        }.start()
                    } catch (e: MessagingException) {
                        Timber.e(e, "Failed to decrypt message: ${message.subject}, likely because E3 encrypted using an unavailable key!")
                    }
                }

                override fun onError(e: Exception) {
                    Timber.e(e, "Got error while binding to OpenPGP service")
                }
            })
            pgpServiceConnection.bindToService()
        }

        return decryptedStoredLocally
    }

    @Throws(MessagingException::class)
    private fun loadSearchResultsSynchronous(account: Account, messageServerIds: List<String>, localFolder: LocalFolder) {
        val fetchProfile = FetchProfile()
        fetchProfile.add(FetchProfile.Item.FLAGS)
        fetchProfile.add(FetchProfile.Item.ENVELOPE)
        fetchProfile.add(FetchProfile.Item.BODY)

        val backend = getBackend(account)
        val folderServerId = localFolder.serverId

        for (messageServerId in messageServerIds) {
            val localMessage = localFolder.getMessage(messageServerId)

            if (localMessage == null) {
                val message = backend.fetchMessage(folderServerId, messageServerId, fetchProfile)
                localFolder.appendMessages(listOf(message))
            }
        }
    }

    private fun getBackend(account: Account): Backend {
        return DI.get(BackendManager::class.java).getBackend(account)
    }
}