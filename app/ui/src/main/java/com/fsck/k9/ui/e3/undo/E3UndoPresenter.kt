package com.fsck.k9.ui.e3.undo

import android.app.PendingIntent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openintents.openpgp.OpenPgpApiManager
import timber.log.Timber

class E3UndoPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val openPgpApiManager: OpenPgpApiManager,
        private val preferences: Preferences,
        private val viewModel: E3UndoViewModel,
        private val view: E3UndoActivity
) {
    private lateinit var account: Account
    private lateinit var pendingIntentForGetKey: PendingIntent

    init {
        viewModel.e3UndoLiveEvent.observe(lifecycleOwner, Observer { msg -> msg?.let { onEventE3Undo(it) } })
    }

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)

        openPgpApiManager.setOpenPgpProvider(account.e3Provider, E3OpenPgpPresenterCallback(openPgpApiManager, view))

        view.setAddress(account.identities[0].email)
        view.sceneBegin()
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }

    fun onClickUndo() {
        view.sceneUndoing()

        GlobalScope.launch(Dispatchers.Main) {
            view.uxDelay()
            view.setLoadingStateUndoing()

            viewModel.e3UndoLiveEvent.undoE3Async(account)
        }
    }

    private fun onEventE3Undo(result: E3UndoResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3UndoResult.Success -> {
                view.setLoadingStateFinished()
                view.sceneFinished()
                Timber.d("Removed E3 encryption from ${result.decryptedUids.size} messages")
            }
            is E3UndoResult.NoneFound -> {
                view.setLoadingStateFinished()
                view.sceneFinishedNoMessages()
            }
            is E3UndoResult.Failure -> {
                Timber.e(result.exception, "Error while undoing E3 encryption")
                view.setLoadingStateSendingFailed()
                view.sceneSendError()
            }
        }
    }
}