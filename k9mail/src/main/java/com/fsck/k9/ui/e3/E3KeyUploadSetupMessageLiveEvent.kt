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

        val armoredKey = requestPgpKey(openPgpApi, account, true)
        val key = requestPgpKey(openPgpApi, account, false)
        val armoredKeyBytes = armoredKey.resultData.toByteArray()
        val keyBytes = key.resultData.toByteArray()

        // TODO: E3 fix the PGP fingerprint and handle multiple PendingIntent
        val e3KeyDigest = KeyFormattingUtils.beautifyHex(Hex.encodeHex(e3Digester.digest(keyBytes)))
        val fingerprintDigester = MessageDigest.getInstance("SHA-1")
        fingerprintDigester.update(0x99.toByte())
        fingerprintDigester.update((keyBytes.size shr 8).toByte())
        fingerprintDigester.update(keyBytes.size.toByte())
        fingerprintDigester.update(keyBytes)
        val fingerprint = KeyFormattingUtils.beautifyHex(Hex.encodeHex(fingerprintDigester.digest()))

        val setupMessage = messageCreator.createE3KeyUploadMessage(armoredKeyBytes, address, account.name, beautifulKeyId, e3KeyDigest, fingerprint)

        return E3KeyUploadMessage(setupMessage, armoredKey.pendingIntent)
    }

    private fun requestPgpKey(openPgpApi: OpenPgpApi, account: Account, armored: Boolean): KeyResult {
        val intent = Intent(OpenPgpApi.ACTION_GET_KEY)
        intent.putExtra(OpenPgpApi.EXTRA_KEY_ID, account.e3Key)
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, armored)
        val baos = ByteArrayOutputStream()
        val result = openPgpApi.executeApi(intent, null as InputStream?, baos)
        return KeyResult(result.getParcelableExtra(OpenPgpApi.RESULT_INTENT), baos)
    }

    companion object {
        val e3Digester: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

data class E3KeyUploadMessage(val keyUploadMessage: Message, val pendingIntentForGetKey: PendingIntent)

data class KeyResult(val pendingIntent: PendingIntent, val resultData: ByteArrayOutputStream)