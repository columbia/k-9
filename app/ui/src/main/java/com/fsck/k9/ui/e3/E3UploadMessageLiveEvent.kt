package com.fsck.k9.ui.e3

import com.fsck.k9.Account
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Message

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg

class E3UploadMessageLiveEvent(
        private val messagingController: MessagingController
) : SingleLiveEvent<E3UploadMessageResult>() {

    fun sendMessageAsync(account: Account, e3UploadMessage: E3UploadMessage) {
        GlobalScope.launch(Dispatchers.Main) {
            val setupMessage = bg {
                messagingController.sendMessageBlocking(account, e3UploadMessage.message)
            }

            delay(2000)

            try {
                setupMessage.await()
                value = E3UploadMessageResult.Success(e3UploadMessage)
            } catch (e: Exception) {
                value = E3UploadMessageResult.Failure(e)
            }
        }
    }
}

abstract class E3UploadMessage(val message: Message)

sealed class E3UploadMessageResult {
    data class Success(val sentMessage: E3UploadMessage): E3UploadMessageResult()

    data class Failure(val exception: Exception): E3UploadMessageResult()
}