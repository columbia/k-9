package com.fsck.k9.ui.e3

import android.app.PendingIntent
import com.fsck.k9.Account
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Transport
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg

class E3KeyUploadMessageUploadLiveEvent(
        private val messagingController: MessagingController
) : SingleLiveEvent<E3KeyUploadMessageUploadResult>() {

    fun sendMessageAsync(account: Account, setupMsg: E3KeyUploadMessage) {
        launch(UI) {
            val setupMessage = bg {
                messagingController.sendMessageBlocking(account, setupMsg.keyUploadMessage)
            }

            delay(2000)

            try {
                setupMessage.await()
                value = E3KeyUploadMessageUploadResult.Success(setupMsg.pendingIntentForGetKey, setupMsg)
            } catch (e: Exception) {
                value = E3KeyUploadMessageUploadResult.Failure(e)
            }
        }
    }
}

sealed class E3KeyUploadMessageUploadResult {
    data class Success(val pendingIntentForGetKey: PendingIntent,
                       val sentMessage: E3KeyUploadMessage) : E3KeyUploadMessageUploadResult()
    data class Failure(val exception: Exception) : E3KeyUploadMessageUploadResult()
}