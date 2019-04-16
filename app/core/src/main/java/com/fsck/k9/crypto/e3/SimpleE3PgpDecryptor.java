package com.fsck.k9.crypto.e3;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.annotation.NonNull;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.filter.EOLConvertingOutputStream;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMessageHelper;

import org.apache.james.mime4j.util.MimeUtil;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.OpenPgpDataSource;

import java.io.IOException;
import java.io.OutputStream;

import timber.log.Timber;

public class SimpleE3PgpDecryptor {
    private final Long pgpKeyId;
    private final OpenPgpApi openPgpApi;

    public SimpleE3PgpDecryptor(final OpenPgpApi openPgpApi, final Long pgpKeyId) {
        this.openPgpApi = openPgpApi;
        this.pgpKeyId = pgpKeyId;
    }

    public MimeMessage decrypt(final MimeMessage encryptedMessage, final String accountEmail) throws MessagingException, IOException {
        Intent pgpApiIntent = buildDecryptIntent(encryptedMessage, accountEmail);

        MimeBodyPart bodyPart = encryptedMessage.toBodyPart();
        OpenPgpDataSource dataSource = createOpenPgpDataSourceFromBodyPart(bodyPart);
        BinaryTempFileBody pgpResultTempBody = null;
        OutputStream outputStream = null;
        try {
            pgpResultTempBody = new BinaryTempFileBody(MimeUtil.ENC_7BIT); // MimeUtil.ENC_8BIT
            outputStream = new EOLConvertingOutputStream(pgpResultTempBody.getOutputStream());
        } catch (IOException e) {
            throw new MessagingException("could not allocate temp file for storage!", e);
        }

        final Intent result = openPgpApi.executeApi(pgpApiIntent, dataSource, outputStream);

        final int resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR);

        switch (resultCode) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                MimeMessage decryptedMessage = MimeMessage.parseMimeMessage(pgpResultTempBody.getInputStream(), true);
                Body decryptedBody = decryptedMessage.getBody();

                MimeMessageHelper.setBody(encryptedMessage, decryptedBody);

                encryptedMessage.setHeader(MimeHeader.HEADER_CONTENT_TYPE, decryptedMessage.getContentType());
                encryptedMessage.removeHeader(E3Constants.MIME_E3_ENCRYPTED_HEADER);
                encryptedMessage.setFlag(Flag.E3, false);

                Timber.d("SimpleE3PgpDecryptor successfully decrypted");

                return encryptedMessage;

            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                PendingIntent returnedPendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                if (returnedPendingIntent == null) {
                    throw new MessagingException("openpgp api needs user interaction, but returned no pendingintent!");
                }

                throw new MessagingException("openpgp api needs user interaction!");

            case OpenPgpApi.RESULT_CODE_ERROR:
                OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                if (error == null) {
                    throw new MessagingException("internal openpgp api error");
                }

                throw new MessagingException(error.getMessage());
        }

        throw new IllegalStateException("unreachable code segment reached");
    }

    private Intent buildDecryptIntent(MimeMessage encryptedMessage, String accountEmail) {
        Intent pgpApiIntent = new Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        Address[] from = encryptedMessage.getFrom();
        if (from.length > 0) {
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_SENDER_ADDRESS, from[0].getAddress());
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_ENCRYPT_ON_RECEIPT_ADDRESS, accountEmail);
        }

        pgpApiIntent.putExtra(OpenPgpApi.EXTRA_SUPPORT_OVERRIDE_CRYPTO_WARNING, true);

        if (encryptedMessage.isSet(Flag.E3)) {
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
        }

        return pgpApiIntent;
    }

    @NonNull
    private OpenPgpDataSource createOpenPgpDataSourceFromBodyPart(final MimeBodyPart bodyPart) {
        return new OpenPgpDataSource() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
                try {
                    Multipart multipartEncryptedMultipart = (Multipart) bodyPart.getBody();
                    BodyPart encryptionPayloadPart = multipartEncryptedMultipart.getBodyPart(1);
                    Body encryptionPayloadBody = encryptionPayloadPart.getBody();
                    encryptionPayloadBody.writeTo(os);
                } catch (MessagingException e) {
                    Timber.e(e, "MessagingException while writing message to crypto provider");
                }
            }
        };
    }
}
