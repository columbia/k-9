package com.fsck.k9.ui.e3.undo

import android.content.Context
import androidx.work.Operation
import com.fsck.k9.Account
import com.fsck.k9.DI
import com.fsck.k9.backend.BackendManager
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.crypto.e3.E3UndoEncryptionManager
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.ui.e3.E3EnableDisableToggler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber

class E3UndoLiveEvent(context: Context) : SingleLiveEvent<E3UndoResult>() {
    private val e3Toggler = E3EnableDisableToggler(context)

    fun undoE3Async(account: Account) {
        val e3Provider = account.savedE3Provider!!
        e3Toggler.setE3DisabledState(account)

        GlobalScope.launch(Dispatchers.IO) {
            val launchUndoResult = async {
                val e3EncryptedMessageIds = scanForUids(account)
                undoE3(account, e3Provider, e3EncryptedMessageIds)
            }

            try {
                val undoOperation = launchUndoResult.await()

                if (undoOperation != null) {
                    postValue(E3UndoResult.Success(undoOperation))
                } else {
                    postValue(E3UndoResult.NoneFound("no E3 encrypted emails found"))
                }
            } catch (e: Exception) {
                postValue(E3UndoResult.Failure(e))
            }
        }
    }

    private fun scanForUids(account: Account): List<String> {
        Timber.d("Starting scanning for E3 encrypted messages")
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
            Timber.d("Finished scanning for E3 encrypted messages")
        }
    }

    private fun getBackend(account: Account): Backend {
        return DI.get(BackendManager::class.java).getBackend(account)
    }

    private fun undoE3(account: Account, cryptoProvider: String, messageIds: List<String>): Operation? {
        return E3UndoEncryptionManager.INSTANCE.startUndo(account, cryptoProvider, messageIds)
    }

    companion object {
        private const val SEARCH_STRING = "${E3Constants.MIME_E3_ENCRYPTED_HEADER} \"\""
    }
}

sealed class E3UndoResult {
    data class Success(val undoOperation: Operation) : E3UndoResult()
    data class Failure(val exception: Exception) : E3UndoResult()
    data class NoneFound(val message: String): E3UndoResult()
}