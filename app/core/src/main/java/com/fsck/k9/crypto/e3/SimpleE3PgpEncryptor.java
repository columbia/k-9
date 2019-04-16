package com.fsck.k9.crypto.e3;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.annotation.NonNull;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.filter.EOLConvertingOutputStream;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMessageHelper;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.TextBody;

import org.apache.james.mime4j.util.MimeUtil;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.OpenPgpDataSource;

import java.io.IOException;
import java.io.OutputStream;

import timber.log.Timber;

public class SimpleE3PgpEncryptor {
    private final Long pgpKeyId;
    private final OpenPgpApi openPgpApi;

    public SimpleE3PgpEncryptor(final OpenPgpApi openPgpApi, final Long pgpKeyId) {
        this.openPgpApi = openPgpApi;
        this.pgpKeyId = pgpKeyId;
    }

    public MimeMessage encrypt(final MimeMessage originalMessage, final String[] recipients) throws MessagingException {
        Intent pgpApiIntent = new Intent(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);

        long[] selfEncryptIds = { pgpKeyId };
        pgpApiIntent.putExtra(OpenPgpApi.EXTRA_KEY_IDS, selfEncryptIds);
        pgpApiIntent.putExtra(OpenPgpApi.EXTRA_USER_IDS, recipients);
        pgpApiIntent.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, pgpKeyId);
        pgpApiIntent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        pgpApiIntent.putExtra(OpenPgpApi.EXTRA_REQUEST_ENCRYPT_ON_RECEIPT, true);

        MimeBodyPart bodyPart = originalMessage.toBodyPart();
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
                MimeMultipart multipartEncrypted = MimeMultipart.newInstance();
                multipartEncrypted.setSubType("encrypted");
                multipartEncrypted.addBodyPart(new MimeBodyPart(new TextBody("Version: 1"), "application/pgp-encrypted"));

                MimeBodyPart encryptedPart = new MimeBodyPart(pgpResultTempBody, "application/octet-stream; name=\"encrypted.asc\"");
                encryptedPart.addHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "inline; filename=\"encrypted.asc\"");

                multipartEncrypted.addBodyPart(encryptedPart);
                MimeMessageHelper.setBody(originalMessage, multipartEncrypted);

                String contentType = String.format(
                        "multipart/encrypted; boundary=\"%s\";\r\n  protocol=\"application/pgp-encrypted\"",
                        multipartEncrypted.getBoundary());
                originalMessage.setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType);

                Timber.d("SimpleE3PgpEncryptor successfully encrypted");

                return originalMessage;

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

    @NonNull
    private OpenPgpDataSource createOpenPgpDataSourceFromBodyPart(final MimeBodyPart bodyPart) {
        return new OpenPgpDataSource() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
                try {
                    bodyPart.writeTo(os);
                } catch (MessagingException e) {
                    throw new IOException(e);
                }
            }
        };
    }
}