package com.fsck.k9.ui.e3.upload

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.fsck.k9.Account
import com.fsck.k9.K9
import com.fsck.k9.crypto.KeyFormattingUtils
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Flag
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.filter.Hex
import com.fsck.k9.mail.internet.*
import com.fsck.k9.mailstore.BinaryMemoryBody
import com.fsck.k9.ui.R
import com.fsck.k9.ui.crypto.PgpWordList
import org.openintents.openpgp.util.OpenPgpApi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.*

class E3KeyUploadMessageCreator(context: Context, private val resources: Resources) : SingleLiveEvent<E3KeyUploadMessage>() {

    private val wordList: PgpWordList = PgpWordList(context)

    fun loadE3KeyUploadMessage(openPgpApi: OpenPgpApi, account: Account): E3KeyUploadMessage {
        val beautifulKeyId = KeyFormattingUtils.beautifyKeyId(account.e3Key)

        // TODO: E3 handle multiple PendingIntent
        val armoredKey = requestPgpKey(openPgpApi, account, true, true)
        val armoredKeyBytes = armoredKey.resultData.toByteArray()
        val e3KeyDigest = KeyFormattingUtils.beautifyHex(Hex.encodeHex(e3Digester.digest(armoredKeyBytes)))
        val randomWords = wordList.getRandomWords(E3Constants.E3_VERIFICATION_PHRASE_LENGTH)
                .joinToString(E3Constants.E3_VERIFICATION_PHRASE_DELIMITER)

        val setupMessage = createE3KeyUploadMessage(armoredKeyBytes,
                account,
                armoredKey.identity,
                beautifulKeyId,
                e3KeyDigest,
                armoredKey.fingerprint,
                randomWords
        )

        return E3KeyUploadMessage(setupMessage, armoredKey.pendingIntent, randomWords, armoredKey)
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

    /**
     * Caller is responsible for managing the lifetime/memory of [KeyFingerprint.qrBitmap].
     *
     * @param pgpKeyData should be an ASCII armored PGP key
     */
    private fun createE3KeyUploadMessage(pgpKeyData: ByteArray, account: Account, keyUserIdentity: String,
                                 keyId: String, e3KeyDigest: String, pgpFingerprint: KeyFingerprint,
                                 verificationPhrase: String): Message {
        try {
            val address = Address.parse(account.getIdentity(0).email)[0]
            val subjectText = resources.getString(R.string.e3_key_upload_msg_subject)
            val beautifiedFingerprint = KeyFormattingUtils.beautifyHex(pgpFingerprint.hexString)
            val keyName = String.format(resources.getString(R.string.e3_key_upload_msg_user_id), keyUserIdentity, keyId)
            val plainText = String.format(resources.getString(R.string.e3_key_upload_msg_body), verificationPhrase, keyName, Build.MODEL, beautifiedFingerprint, e3KeyDigest)
            val htmlText = String.format(resources.getString(R.string.e3_key_upload_msg_body_html), verificationPhrase, keyName, Build.MODEL, beautifiedFingerprint, e3KeyDigest)

            val messageBody = MimeMultipart.newInstance()
            val plainBodyPart = MimeBodyPart(TextBody(plainText), "text/plain")
            val htmlBodyPart = MimeBodyPart(TextBody(htmlText), "text/html")
            val keyAttachment: MimeBodyPart = createKeyAttachment(pgpKeyData)

            messageBody.setSubType("alternative")
            messageBody.addBodyPart(plainBodyPart)
            messageBody.addBodyPart(htmlBodyPart)

            val messageWrapper = MimeMultipart.newInstance()
            messageWrapper.addBodyPart(MimeBodyPart(messageBody))
            messageWrapper.addBodyPart(keyAttachment)

            if (pgpFingerprint.qrBitmap != null) {
                val baos = ByteArrayOutputStream()
                pgpFingerprint.qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val qrData = Base64.encode(baos.toByteArray(), Base64.DEFAULT)
                val qrKeyAttachment = MimeBodyPart(BinaryMemoryBody(qrData, "base64"))

                qrKeyAttachment.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "image/png")
                qrKeyAttachment.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "attachment; filename=\"e3_key_qr_code.png\"")

                messageWrapper.addBodyPart(qrKeyAttachment)
            }

            val message = MimeMessage()
            MimeMessageHelper.setBody(message, messageWrapper)

            val nowDate = Date()

            message.setFlag(Flag.X_DOWNLOADED_FULL, true)
            message.subject = subjectText

            message.setHeader(E3Constants.MIME_E3_NAME, keyName)
            message.setHeader(E3Constants.MIME_E3_DIGEST, e3KeyDigest)
            message.setHeader(E3Constants.MIME_E3_VERIFICATION, verificationPhrase)
            message.setHeader(E3Constants.MIME_E3_TIMESTAMP, System.currentTimeMillis().toString())

            message.internalDate = nowDate
            message.addSentDate(nowDate, K9.hideTimeZone())
            message.setFrom(address)
            message.setRecipients(Message.RecipientType.TO, arrayOf(address))

            return message
        } catch (e: MessagingException) {
            throw AssertionError(e)
        }
    }

    private fun createKeyAttachment(pgpKeyData: ByteArray): MimeBodyPart {
        val keyAttachment = MimeBodyPart(BinaryMemoryBody(pgpKeyData, "7bit"))
        keyAttachment.setHeader(MimeHeader.HEADER_CONTENT_TYPE, E3Constants.CONTENT_TYPE_PGP_KEYS)
        keyAttachment.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "attachment; filename=\"e3_key.asc\"")
        return keyAttachment
    }

    companion object {
        val e3Digester: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

data class E3KeyUploadMessage(val keyUploadMessage: Message, val pendingIntentForGetKey: PendingIntent,
                              val verificationPhrase: String,
                              val keyResult: KeyResult)

data class KeyResult(val pendingIntent: PendingIntent, val resultData: ByteArrayOutputStream,
                     val identity: String, val fingerprint: KeyFingerprint)

data class KeyFingerprint(val hexString: String?, val qrBitmap: Bitmap?)