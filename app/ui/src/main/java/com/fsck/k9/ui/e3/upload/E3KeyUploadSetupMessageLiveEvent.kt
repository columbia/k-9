package com.fsck.k9.ui.e3.upload

import com.fsck.k9.Account
import com.fsck.k9.helper.SingleLiveEvent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi

class E3KeyUploadSetupMessageLiveEvent(private val keyUploadMessageCreator: E3KeyUploadMessageCreator) : SingleLiveEvent<E3KeyUploadMessage>() {

    fun loadE3KeyUploadMessageAsync(openPgpApi: OpenPgpApi, account: Account) {
        GlobalScope.launch(Dispatchers.Main) {
            val setupMessage = bg {
                keyUploadMessageCreator.loadE3KeyUploadMessage(openPgpApi, account, null)
            }

            value = setupMessage.await()
        }
    }
}