package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.content.Intent
import com.fsck.k9.Account
import com.fsck.k9.crypto.E3KeyUploadMessageCreator
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Message
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi
import java.io.ByteArrayOutputStream
import java.io.InputStream

class E3KeyUploadSetupMessageLiveEvent(val messageCreator: E3KeyUploadMessageCreator) : SingleLiveEvent<E3KeyUploadMessage>() {
    fun loadE3KeyUploadMessageAsync(openPgpApi: OpenPgpApi, account: Account) {
        launch(UI) {
            val setupMessage = bg {
                loadE3KeyUploadMessage(openPgpApi, account)
            }

            value = setupMessage.await()
        }
    }

    private fun loadE3KeyUploadMessage(openPgpApi: OpenPgpApi, account: Account): E3KeyUploadMessage {
        val address = Address.parse(account.getIdentity(0).email)[0]

        val intent = Intent(OpenPgpApi.ACTION_GET_KEY)
        intent.putExtra(OpenPgpApi.EXTRA_KEY_ID, account.e3Key)
        val baos = ByteArrayOutputStream()

        val result = openPgpApi.executeApi(intent, null as InputStream?, baos)

        val keyData = baos.toByteArray()
        val pi: PendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)

        val setupMessage = messageCreator.createE3KeyUploadMessage(keyData, address)

        return E3KeyUploadMessage(setupMessage, pi)
    }
}

data class E3KeyUploadMessage(val keyUploadMessage: Message, val showTransferCodePi: PendingIntent)