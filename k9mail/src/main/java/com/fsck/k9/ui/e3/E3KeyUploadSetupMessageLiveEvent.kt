package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import com.fsck.k9.Account
import com.fsck.k9.crypto.E3KeyUploadMessageCreator
import com.fsck.k9.crypto.KeyFormattingUtils
import com.fsck.k9.helper.SingleLiveEvent
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
        val beautifulKeyId = KeyFormattingUtils.beautifyKeyId(account.e3Key)

        // TODO: E3 handle multiple PendingIntent
        val armoredKey = requestPgpKey(openPgpApi, account, true, true)
        val armoredKeyBytes = armoredKey.resultData.toByteArray()
        val e3KeyDigest = KeyFormattingUtils.beautifyHex(Hex.encodeHex(e3Digester.digest(armoredKeyBytes)))

        val setupMessage = messageCreator.createE3KeyUploadMessage(armoredKeyBytes,
                account,
                armoredKey.identity,
                beautifulKeyId,
                e3KeyDigest,
                armoredKey.fingerprint
        )

        armoredKey.fingerprint.qrBitmap?.recycle()

        return E3KeyUploadMessage(setupMessage, armoredKey.pendingIntent)
    }

    private fun requestPgpKey(openPgpApi: OpenPgpApi, account: Account, armored: Boolean, fingerprint: Boolean): KeyResult {
        val intent = Intent(OpenPgpApi.ACTION_GET_KEY)
        intent.putExtra(OpenPgpApi.EXTRA_KEY_ID, account.e3Key)
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, armored)
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_FINGERPRINT, fingerprint)
        val baos = ByteArrayOutputStream()
        val result = openPgpApi.executeApi(intent, null as InputStream?, baos)

        val resultIntent = result.getParcelableExtra<PendingIntent>(OpenPgpApi.RESULT_INTENT)
        val fingerprintString = result.getStringExtra(OpenPgpApi.RESULT_FINGERPRINT)
        val fingerprintBitmap = result.getParcelableExtra<Bitmap>(OpenPgpApi.RESULT_FINGERPRINT_QR)
        val identity = result.getStringExtra(OpenPgpApi.RESULT_USER_ID)

        return KeyResult(resultIntent, baos, identity, KeyFingerprint(fingerprintString, fingerprintBitmap))
    }

    companion object {
        val e3Digester: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

data class E3KeyUploadMessage(val keyUploadMessage: Message, val pendingIntentForGetKey: PendingIntent)

data class KeyResult(val pendingIntent: PendingIntent, val resultData: ByteArrayOutputStream,
                     val identity: String, val fingerprint: KeyFingerprint)

data class KeyFingerprint(val hexString: String?, val qrBitmap: Bitmap?)