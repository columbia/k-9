package com.fsck.k9.ui.e3.delete

import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
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
import com.fsck.k9.ui.R
import org.openintents.openpgp.util.OpenPgpApi
import java.security.MessageDigest
import java.util.*

class E3DeleteMessageCreator(context: Context, private val resources: Resources) : SingleLiveEvent<E3DeleteMessage>() {

    // Returns null if no upload is performed
    fun createE3DeleteMessage(openPgpApi: OpenPgpApi, account: Account, e3DeleteDeviceRequests: Set<E3DeleteDeviceRequest>): E3DeleteMessage {
        val beautifulKeyId = KeyFormattingUtils.beautifyKeyId(account.e3Key)
        val e3PublicKeyManager = E3PublicKeyManager(openPgpApi)

        // TODO: E3 handle multiple PendingIntent
        val armoredKey = e3PublicKeyManager.requestPgpKey(openPgpApi, account.e3Key, true, false)
        val armoredKeyBytes = armoredKey.resultData.toByteArray()
        val e3KeyDigest = KeyFormattingUtils.beautifyHex(Hex.encodeHex(e3Digester.digest(armoredKeyBytes)))

        val deleteMessage = createE3DeleteMessage(
                openPgpApi,
                account,
                armoredKey.identity,
                beautifulKeyId,
                e3KeyDigest,
                e3DeleteDeviceRequests
        )

        return E3DeleteMessage(deleteMessage, e3DeleteDeviceRequests)
    }

    private fun createE3DeleteMessage(openPgpApi: OpenPgpApi,
                                      account: Account,
                                      keyUserIdentity: String,
                                      keyId: String,
                                      e3KeyDigest: String,
                                      e3DeleteDeviceRequests: Set<E3DeleteDeviceRequest>): Message {
        try {
            val address = Address.parse(account.getIdentity(0).email)[0]
            val subjectText = resources.getString(R.string.e3_device_delete_msg_subject)
            val requestingDevice = String.format(resources.getString(R.string.e3_device_delete_msg_user_id), keyUserIdentity, keyId)
            val devicesRequestedToDelete = e3DeleteDeviceRequests.map { it.keyName }.joinToString()
            val plainText = String.format(resources.getString(R.string.e3_device_delete_msg_body), requestingDevice, devicesRequestedToDelete)
            val htmlText = String.format(resources.getString(R.string.e3_device_delete_msg_body_html), requestingDevice, devicesRequestedToDelete)

            val messageBody = MimeMultipart.newInstance()
            val plainBodyPart = MimeBodyPart(TextBody(plainText), "text/plain")
            val htmlBodyPart = MimeBodyPart(TextBody(htmlText), "text/html")

            messageBody.setSubType("alternative")
            messageBody.addBodyPart(plainBodyPart)
            messageBody.addBodyPart(htmlBodyPart)

            val messageWrapper = MimeMultipart.newInstance()
            messageWrapper.addBodyPart(MimeBodyPart(messageBody))

            val message = MimeMessage()
            MimeMessageHelper.setBody(message, messageWrapper)

            val nowDate = Date()

            message.setFlag(Flag.X_DOWNLOADED_FULL, true)
            message.subject = subjectText

            message.setHeader(E3Constants.MIME_E3_NAME, requestingDevice)
            message.setHeader(E3Constants.MIME_E3_DIGEST, e3KeyDigest)
            message.setHeader(E3Constants.MIME_E3_TIMESTAMP, System.currentTimeMillis().toString())
            message.setHeader(E3Constants.MIME_E3_UID, account.uuid)
            addDeletedE3PublicKeysToHeader(message, e3DeleteDeviceRequests)

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

    private fun addDeletedE3PublicKeysToHeader(message: MimeMessage, e3DeleteDeviceRequests: Set<E3DeleteDeviceRequest>) {
        for (deleteRequest in e3DeleteDeviceRequests) {
            message.addHeader(E3Constants.MIME_E3_DELETE, deleteRequest.keyId.toString())
        }
    }

    companion object {
        val e3Digester: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

data class E3DeleteMessage(val deleteMessage: Message, val deleteRequests: Set<E3DeleteDeviceRequest>)

data class E3DeleteDeviceRequest(val keyId: Long, val keyName: String)