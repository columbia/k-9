package com.fsck.k9.ui.e3.undo

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.fsck.k9.Account
import com.fsck.k9.Preferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

class E3UndoPresenter internal constructor(
        private val lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val viewModel: E3UndoViewModel,
        private val view: E3UndoActivity
) {
    private lateinit var account: Account

    init {
        viewModel.e3UndoLiveEvent.observe(lifecycleOwner,
                Observer { msg -> msg?.let { onEventE3Undo(it) } })
    }

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)

        view.setAddress(account.identities[0].email)
        view.sceneBegin()

        viewModel.getExistingWorkInfo(account).observe(lifecycleOwner,
                Observer { listOfWorkInfo -> listOfWorkInfo?.let { onEventExistingWorkInfo(it) } })
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }

    fun onClickUndo() {
        view.sceneLaunchingUndo()

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
                Timber.d("WorkManager undo Operation successfully launched")
            }
            is E3UndoResult.Failure -> {
                Timber.e(result.exception, "Error while undoing E3 encryption")
                view.setLoadingStateSendingFailed()
                view.sceneSendError()
            }
            is E3UndoResult.NoneFound -> {
                view.setLoadingStateFinished()
                view.sceneFinishedNoMessages()
                Timber.d("No E3 encrypted emails were found, so no undo work was launched")
            }
        }
    }

    private fun onEventExistingWorkInfo(workInfoList: List<WorkInfo>?) {
        if (workInfoList == null || workInfoList.isEmpty()) {
            Timber.d("Found no existing E3 undo workers")
            return
        }

        Timber.d("Found existing E3 undo workers: $workInfoList")
        view.sceneUndoing()
    }
}