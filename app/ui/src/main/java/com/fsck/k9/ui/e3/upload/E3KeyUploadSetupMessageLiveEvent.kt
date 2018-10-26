package com.fsck.k9.ui.e3.upload

import com.fsck.k9.Account
import com.fsck.k9.helper.SingleLiveEvent
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi

class E3KeyUploadSetupMessageLiveEvent(private val keyUploadMessageCreator: E3KeyUploadMessageCreator) : SingleLiveEvent<E3KeyUploadMessage>() {

    fun loadE3KeyUploadMessageAsync(openPgpApi: OpenPgpApi, account: Account) {
        launch(UI) {
            val setupMessage = bg {
                keyUploadMessageCreator.loadE3KeyUploadMessage(openPgpApi, account)
            }

            value = setupMessage.await()
        }
    }
}