package com.fsck.k9.ui.e3.undo

import android.content.Context
import androidx.work.Operation
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.MessagingControllerCommands.*
import com.fsck.k9.controller.SimpleMessagingListener
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.crypto.e3.E3UndoEncryptionManager
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

class E3UndoLiveEvent(context: Context) : SingleLiveEvent<E3UndoResult>() {
    private val e3Toggler = E3EnableDisableToggler(context)

    fun undoE3Async(account: Account) {
        val e3Provider = account.e3Provider!!
        e3Toggler.setE3DisabledState(account)

        GlobalScope.launch(Dispatchers.Main) {
            val launchUndoResult = bg {
                undoE3(account, e3Provider)
            }

            value = try {
                val undoOperation = launchUndoResult.await()

                if (undoOperation != null) {
                    E3UndoResult.Success(undoOperation)
                } else {
                    E3UndoResult.NoneFound("no E3 encrypted emails found")
                }
            } catch (e: Exception) {
                E3UndoResult.Failure(e)
            }
        }
    }

    private fun undoE3(account: Account, cryptoProvider: String): Operation? {
        return E3UndoEncryptionManager.INSTANCE.startUndo(account, cryptoProvider)
    }
}

sealed class E3UndoResult {
    data class Success(val undoOperation: Operation) : E3UndoResult()
    data class Failure(val exception: Exception) : E3UndoResult()
    data class NoneFound(val message: String): E3UndoResult()
}