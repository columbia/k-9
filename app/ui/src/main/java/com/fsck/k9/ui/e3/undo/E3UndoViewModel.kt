package com.fsck.k9.ui.e3.undo

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import com.fsck.k9.Account
import com.fsck.k9.crypto.e3.E3UndoEncryptionManager

internal class E3UndoViewModel(
        val e3UndoLiveEvent: E3UndoLiveEvent) : ViewModel() {
    fun getExistingWorkInfo(account: Account): LiveData<List<WorkInfo>> {
        return E3UndoEncryptionManager.INSTANCE.getCurrentLiveData(account)
    }

    fun cancelExistingWork(account: Account) {
        return E3UndoEncryptionManager.INSTANCE.cancelUndo(account)
    }
}