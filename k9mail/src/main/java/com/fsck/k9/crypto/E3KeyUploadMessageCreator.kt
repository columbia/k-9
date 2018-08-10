package com.fsck.k9.crypto

import android.content.res.Resources
import android.os.Build
import com.fsck.k9.K9
import com.fsck.k9.R
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Flag
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.internet.*
import com.fsck.k9.mailstore.BinaryMemoryBody
import java.util.*

class E3KeyUploadMessageCreator(val resources: Resources) {
    fun createE3KeyUploadMessage(data: ByteArray, toAndFromAddress: Address, accountName: String, keyId: String, e3KeyDigest: String, pgpFingerprint: String?): Message {
        try {
            val subjectText = resources.getString(R.string.e3_key_upload_msg_subject)
            var messageText = resources.getString(R.string.e3_key_upload_msg_body)
            val keyName = String.format(resources.getString(R.string.e3_key_upload_msg_key_id), accountName, keyId)

            messageText = String.format(messageText, keyName, Build.MODEL, e3KeyDigest)

            if (pgpFingerprint != null) {
                // TODO: E3 fix PGP fingerprint
                //messageText += String.format(resources.getString(R.string.e3_key_upload_msg_pgp_fingerprint), pgpFingerprint)
            }

            val textBodyPart = MimeBodyPart(TextBody(messageText))
            val dataBodyPart = MimeBodyPart(BinaryMemoryBody(data, "7bit"))
            dataBodyPart.setHeader(MimeHeader.HEADER_CONTENT_TYPE, E3Constants.CONTENT_TYPE_PGP_KEYS)
            dataBodyPart.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "attachment; filename=\"e3_key.asc\"")

            val messageBody = MimeMultipart.newInstance()
            messageBody.addBodyPart(textBodyPart)
            messageBody.addBodyPart(dataBodyPart)

            val message = MimeMessage()
            MimeMessageHelper.setBody(message, messageBody)

            val nowDate = Date()

            message.setFlag(Flag.X_DOWNLOADED_FULL, true)
            message.subject = subjectText

            message.setHeader(E3Constants.MIME_E3_NAME, keyName)
            message.setHeader(E3Constants.MIME_E3_DIGEST, e3KeyDigest)

            message.internalDate = nowDate
            message.addSentDate(nowDate, K9.hideTimeZone())
            message.setFrom(toAndFromAddress)
            message.setRecipients(Message.RecipientType.TO, arrayOf(toAndFromAddress))

            return message
        } catch (e: MessagingException) {
            throw AssertionError(e)
        }

    }
}