package com.fsck.k9.crypto.e3

import android.content.Context
import androidx.work.*
import com.fsck.k9.Account
import com.fsck.k9.DI
import com.fsck.k9.backend.BackendManager
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.api.BackendFolder
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mailstore.LocalFolder
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.search.LocalSearch
import com.fsck.k9.search.SearchSpecification
import timber.log.Timber
import java.lang.Exception
import java.util.concurrent.SynchronousQueue

class E3UndoEncryptionManager(private val context: Context) {

    fun startUndoWorker(account: Account) {
        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

        val e3EncryptedMessageIds = scanForUids(account)
        val messageIdBatches = batchUids(e3EncryptedMessageIds)
        val workRequests = mutableListOf<OneTimeWorkRequest>()

        for (batch in messageIdBatches) {
            val inputData = workDataOf(WORKER_INPUT_KEY to batch)
            val undoWorkRequest = OneTimeWorkRequestBuilder<UndoWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .build()

            workRequests.add(undoWorkRequest)
        }

        WorkManager.getInstance().beginWith(workRequests).enqueue()
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
            return backend.search(folderServerId, SEARCH_STRING, null, null)
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

    companion object {
        private const val SEARCH_STRING = "${E3Constants.MIME_E3_ENCRYPTED_HEADER} \"\""
        const val WORKER_INPUT_KEY = "message_ids"
        const val WORKER_OUTPUT_KEY = "message_ids"
    }
}

class UndoWorker(private val appContext: Context,
                 private val account: Account,
                 workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        try {
            val msgServerIds = inputData.getStringArray(E3UndoEncryptionManager.WORKER_INPUT_KEY)!!.asList()

            val address = Address.parse(account.getIdentity(0).email)[0]
            val controller = MessagingController.getInstance(appContext)

            val folderServerId = account.inboxFolder
            val localStore = account.localStore
            val localFolder = localStore.getFolder(folderServerId)
                    ?: throw MessagingException("Folder not found")

            // Download messages locally if they aren't already
            val nonlocalMsgIds = localFolder.extractNewMessages(msgServerIds)
            loadSearchResultsSynchronous(account, nonlocalMsgIds, localFolder)

            val outputData = workDataOf(E3UndoEncryptionManager.WORKER_OUTPUT_KEY to msgServerIds)
            return Result.success(outputData)
        } catch (e: Exception) {
            // Never use exceptions for codeflow (do as I say, not as I do)
            return Result.failure()
        }
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