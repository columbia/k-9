package com.fsck.k9.ui.e3

import android.app.PendingIntent
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Transport
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg

class E3KeyUploadMessageUploadLiveEvent : SingleLiveEvent<E3KeyUploadMEssageUploadResult>() {
    fun sendMessageAsync(transport: Transport, setupMsg: E3KeyUploadMessage) {
        launch(UI) {
            val setupMessage = bg {
                transport.sendMessage(setupMsg.keyUploadMessage)
            }

            delay(2000)

            try {
                setupMessage.await()
                value = E3KeyUploadMEssageUploadResult.Success(setupMsg.showTransferCodePi)
            } catch (e: Exception) {
                value = E3KeyUploadMEssageUploadResult.Failure(e)
            }
        }
    }
}

sealed class E3KeyUploadMEssageUploadResult {
    data class Success(val showTransferCodePi: PendingIntent) : E3KeyUploadMEssageUploadResult()
    data class Failure(val exception: Exception) : E3KeyUploadMEssageUploadResult()
}