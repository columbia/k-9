package com.fsck.k9.crypto

import android.content.res.Resources
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
    fun createE3KeyUploadMessage(data: ByteArray, address: Address): Message {
        try {
            val subjectText = resources.getString(R.string.e3_key_upload_msg_subject)
            val messageText = resources.getString(R.string.e3_key_upload_msg_body)

            val textBodyPart = MimeBodyPart(TextBody(messageText))
            val dataBodyPart = MimeBodyPart(BinaryMemoryBody(data, "7bit"))
            dataBodyPart.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "application/autocrypt-setup")
            dataBodyPart.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "attachment; filename=\"autocrypt-setup-message\"")

            val messageBody = MimeMultipart.newInstance()
            messageBody.addBodyPart(textBodyPart)
            messageBody.addBodyPart(dataBodyPart)

            val message = MimeMessage()
            MimeMessageHelper.setBody(message, messageBody)

            val nowDate = Date()

            message.setFlag(Flag.X_DOWNLOADED_FULL, true)
            message.subject = subjectText
            message.setHeader("Autocrypt-Setup-Message", "v1")
            message.internalDate = nowDate
            message.addSentDate(nowDate, K9.hideTimeZone())
            message.setFrom(address)
            message.setRecipients(Message.RecipientType.TO, arrayOf(address))

            return message
        } catch (e: MessagingException) {
            throw AssertionError(e)
        }

    }
}