package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.content.Intent
import com.fsck.k9.Account
import com.fsck.k9.crypto.E3KeyUploadMessageCreator
import com.fsck.k9.crypto.KeyFormattingUtils
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.filter.Hex
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

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
        val beautifulKeyId = KeyFormattingUtils.beautifyKeyId(account.e3Key)

        val intentFullKey = Intent(OpenPgpApi.ACTION_GET_KEY)
        intentFullKey.putExtra(OpenPgpApi.EXTRA_KEY_ID, account.e3Key)
        intentFullKey.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)
        val baos = ByteArrayOutputStream()
        val fullKeyResult = openPgpApi.executeApi(intentFullKey, null as InputStream?, baos)

        val keyBytes = baos.toByteArray()
        val keyDigest = Hex.encodeHex(digester.digest(keyBytes))

        val setupMessage = messageCreator.createE3KeyUploadMessage(keyBytes, address, account.name, beautifulKeyId, keyDigest)

        val pi: PendingIntent = fullKeyResult.getParcelableExtra(OpenPgpApi.RESULT_INTENT)

        return E3KeyUploadMessage(setupMessage, pi)
    }

    companion object {
        val digester: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

data class E3KeyUploadMessage(val keyUploadMessage: Message, val pendingIntentForGetKey: PendingIntent)