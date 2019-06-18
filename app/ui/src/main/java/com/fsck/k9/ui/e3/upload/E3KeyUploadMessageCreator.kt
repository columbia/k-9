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
import com.fsck.k9.crypto.e3.*
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
import com.fsck.k9.ui.e3.E3UploadMessage
import com.fsck.k9.ui.e3.verify.E3DigestsAndResponses
import org.openintents.openpgp.util.OpenPgpApi
import java.io.*
import java.security.MessageDigest
import java.util.*

class E3KeyUploadMessageCreator(context: Context, private val resources: Resources) : SingleLiveEvent<E3KeyUploadMessage>() {

    private val wordList: PgpWordList = PgpWordList(context)

    // Invoked in response to receiving and verifying an uploaded key, so now we want to upload our key
    fun loadAllE3KeysUploadMessage(openPgpApi: OpenPgpApi, account: Account, e3KeyDigestsAndResponses: E3DigestsAndResponses): E3KeyUploadMessage? {
        // TODO: E3 implement getting and adding all keys
        return loadE3KeyUploadMessage(openPgpApi, account, e3KeyDigestsAndResponses)
    }

    // Returns null if no upload is performed
    fun loadE3KeyUploadMessage(openPgpApi: OpenPgpApi, account: Account,
                               e3KeyDigestsAndResponses: E3DigestsAndResponses?
    ): E3KeyUploadMessage? {
        val e3PublicKeyManager = E3PublicKeyManager(openPgpApi)
        val beautifulKeyId = KeyFormattingUtils.beautifyKeyId(account.e3Key)

        // TODO: E3 handle multiple PendingIntent
        val armoredKey = e3PublicKeyManager.requestPgpKey(openPgpApi, account.e3Key, true, true)
        val armoredKeyBytes = armoredKey.resultData.toByteArray()
        val e3KeyDigest = KeyFormattingUtils.beautifyHex(Hex.encodeHex(e3Digester.digest(armoredKeyBytes)))
        val randomWords = wordList.getRandomWords(E3Constants.E3_VERIFICATION_PHRASE_LENGTH)
                .joinToString(E3Constants.E3_VERIFICATION_PHRASE_DELIMITER)
        val e3PublicKeys: Set<KeyResult> = requestKnownE3PublicKeys(openPgpApi, e3PublicKeyManager)

        // TODO: E3 ensure that all E3 key digests are compared in a way accounting for spaces/lowercase/uppercase

        // Upload only if we are manually uploading or if we received a manually uploaded key (aka no RESPONSE TO header)
        if (e3KeyDigestsAndResponses == null
                || e3KeyDigestsAndResponses.verifiedE3KeyDigests.isEmpty() // If empty, means we're not uploading in response to verifying keys
                || e3KeyDigestsAndResponses.responseToKeyDigests.isEmpty() // If empty, means we're responding to an uploaded key, but not a RESPONSE TO one
        ) {
            val setupMessage = createE3KeyUploadMessage(
                    openPgpApi,
                    armoredKeyBytes,
                    account,
                    armoredKey.identity,
                    beautifulKeyId,
                    e3KeyDigest,
                    armoredKey.fingerprint!!,
                    randomWords,
                    e3KeyDigestsAndResponses?.verifiedE3KeyDigests,
                    e3PublicKeys
            )

            return E3KeyUploadMessage(setupMessage, armoredKey.pendingIntent, randomWords, armoredKey)
        }

        return null
    }

    private fun requestKnownE3PublicKeys(openPgpApi: OpenPgpApi, e3PublicKeyManager: E3PublicKeyManager): Set<KeyResult> {
        val intent = Intent(OpenPgpApi.ACTION_GET_ENCRYPT_ON_RECEIPT_PUBLIC_KEYS)
        val keyIdsResult = openPgpApi.executeApi(intent, null as InputStream?, null)

        val eorKeyIds = keyIdsResult.getLongArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)
        val eorKeyResults = mutableSetOf<KeyResult>()

        for (keyId: Long in eorKeyIds) {
            eorKeyResults.add(e3PublicKeyManager.requestPgpKey(openPgpApi, keyId, true, true))
        }

        return eorKeyResults
    }

    /**
     * Caller is responsible for managing the lifetime/memory of [KeyFingerprint.qrBitmap].
     *
     * TODO: E3 Make this into a builder class some day...
     *
     * @param pgpKeyData should be an ASCII armored PGP key
     */
    private fun createE3KeyUploadMessage(openPgpApi: OpenPgpApi,
                                         pgpKeyData: ByteArray,
                                         account: Account,
                                         keyUserIdentity: String,
                                         keyId: String,
                                         e3KeyDigest: String,
                                         pgpFingerprint: KeyFingerprint,
                                         verificationPhrase: String,
                                         initialUploadedE3KeyDigests: Set<String>?,
                                         e3PublicKeys: Set<KeyResult>): Message {
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
                pgpFingerprint.qrBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, baos)
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
            message.setHeader(E3Constants.MIME_E3_UID, account.uuid)
            addE3PublicKeysToHeader(message, e3PublicKeys)

            if (initialUploadedE3KeyDigests != null && !initialUploadedE3KeyDigests.isEmpty()) {
                val receivedE3KeyDigests = initialUploadedE3KeyDigests.joinToString(E3Constants.E3_KEY_DIGEST_DELIMITER)
                message.setHeader(E3Constants.MIME_E3_RESPONSE_TO, receivedE3KeyDigests)
            }

            message.internalDate = nowDate
            message.addSentDate(nowDate, K9.hideTimeZone())
            message.setFrom(address)
            message.setRecipients(Message.RecipientType.TO, arrayOf(address))

            val e3HeaderSigner = E3HeaderSigner(openPgpApi)
            val e3KeyEmailParser = E3KeyEmailParser()
            val parsedE3KeyEmail = e3KeyEmailParser.parseKeyEmail(message)

            val e3HeaderSignature = e3HeaderSigner.signE3Headers(account.e3Key, parsedE3KeyEmail)
            val foldedB64E3HeaderSignature = KeyFormattingUtils.foldBase64KeyData(e3HeaderSignature!!.toByteArray(Charsets.UTF_8))
            message.setHeader(E3Constants.MIME_E3_SIGNATURE, foldedB64E3HeaderSignature)

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

    private fun addE3PublicKeysToHeader(message: MimeMessage, e3PublicKeys: Set<KeyResult>) {
        for (keyResult in e3PublicKeys) {
            val foldedBase64Key = KeyFormattingUtils.foldBase64KeyData(keyResult.resultData.toByteArray())
            message.addHeader(E3Constants.MIME_E3_KEYS, foldedBase64Key)
        }
    }

    companion object {
        val e3Digester: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

data class E3KeyUploadMessage(val keyUploadMessage: Message,
                              val pendingIntentForGetKey: PendingIntent,
                              val verificationPhrase: String,
                              val keyResult: KeyResult): E3UploadMessage(keyUploadMessage)