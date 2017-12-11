package com.fsck.k9.mail.e3.smime;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.E3Utils;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.message.MessageHeaderParser;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;

import timber.log.Timber;

/**
 * Created by mauzel on 12/11/2017.
 */

public class SMIMEDecryptFunction implements Function<Part, ByteSource> {
    private final E3Utils e3Utils;
    private final KeyStore.PrivateKeyEntry keyEntry;

    static {
        System.loadLibrary("e3-jni");
    }

    public SMIMEDecryptFunction(final KeyStore.PrivateKeyEntry keyEntry, final E3Utils e3Utils) {
        this.e3Utils = e3Utils;
        this.keyEntry = keyEntry;
    }

    public ByteSource apply(final Part originalMessage) {
        final String contentType = originalMessage.getHeader("Content-Type")[0];
        Preconditions.checkArgument(contentType.contains("pkcs"),
                "cannot decrypt a message without the SMIME mime type");

        final Body encryptedBody = originalMessage.getBody();
        final InputStream encryptedBodyInputStream;

        try {
            encryptedBodyInputStream = encryptedBody.getInputStream();
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        }

        final long startDecrypt = System.currentTimeMillis();
        final long startPrepareEnvelopedData = System.currentTimeMillis();

        final File envelopedFile;
        final File decryptedFile;

        try {
            envelopedFile = e3Utils.getTempFile("decrypted", "enveloped-data");
            decryptedFile = e3Utils.getTempFile("decrypted", "plain");
            writeToEnvelopeFile(envelopedFile, contentType, encryptedBodyInputStream);

            if (envelopedFile.length() < 1) {
                Timber.d(E3Constants.LOG_TAG, "Message did not have data available. If this happens, the quickest workaround is to increase Fetching messages size");
                throw new IllegalArgumentException("Message for decryption had no data available");
            }

            E3Utils.logDuration(E3Constants.MEASURE_LOG_TAG, "OpenSSL startPrepareEnvelopedData", startPrepareEnvelopedData);

            final byte[] privateKeyDer = keyEntry.getPrivateKey().getEncoded();
            final byte[] certDer = keyEntry.getCertificate().getEncoded();

            final long startDecryptJNI = System.currentTimeMillis();
            final int decryptResult = cmsDecryptJNI(certDer, privateKeyDer, envelopedFile.getAbsolutePath(), decryptedFile.getAbsolutePath());

            if (decryptResult != 0) {
                throw new RuntimeException("Failed to do JNI decrypt and verify, return code: " + decryptResult);
            }

            Timber.d(E3Constants.LOG_TAG, "Got good JNI decrypt and verify result for " + decryptedFile.getAbsolutePath());
            E3Utils.logDuration(E3Constants.MEASURE_LOG_TAG, "OpenSSL startDecryptJNI", startDecryptJNI);

            E3Utils.logDuration(E3Constants.MEASURE_LOG_TAG, "OpenSSL decrypt", startDecrypt);
            final long startParseMimeMessage = System.currentTimeMillis();

            final ByteArrayOutputStream fullDecrypted = new ByteArrayOutputStream();
            final ByteArrayOutputStream originalHeadersOut = new ByteArrayOutputStream();
            InputStream originalHeaders = null;
            try {
                originalMessage.writeHeaderTo(originalHeadersOut);
                originalHeaders = new ByteArrayInputStream(originalHeadersOut.toByteArray());

                final Part intermediaryPart = new MimeBodyPart();
                MessageHeaderParser.parse(intermediaryPart, originalHeaders);

                intermediaryPart.removeHeader(MimeHeader.HEADER_CONTENT_DISPOSITION);
                intermediaryPart.removeHeader(MimeHeader.HEADER_CONTENT_TYPE);
                intermediaryPart.writeHeaderTo(fullDecrypted);

                // TODO: Make this more efficient using streaming
                fullDecrypted.write(FileUtils.readFileToByteArray(decryptedFile));

                final byte[] fullDecryptedBytes = fullDecrypted.toByteArray();
                Timber.d(E3Constants.LOG_TAG, new String(fullDecryptedBytes));

                return ByteSource.wrap(fullDecryptedBytes);
            } finally {
                E3Utils.logDuration(E3Constants.MEASURE_LOG_TAG, "Parse MIME message", startParseMimeMessage);
                Closeables.close(fullDecrypted, true);
                Closeables.close(originalHeaders, true);
                Closeables.close(originalHeadersOut, true);
            }
        } catch (final IOException | CertificateEncodingException | MessagingException e) {
            throw new RuntimeException(e);
        } finally {
            Timber.d(E3Constants.LOG_TAG, "Finished parse...");
        }
    }

    private void writeToEnvelopeFile(final File envelopedFile, final String contentType, final InputStream encryptedBodyInputStream) throws IOException {
        final OutputStream envelopedFileStream = new FileOutputStream(envelopedFile);

        try {
            envelopedFileStream.write("MIME-Version: 1.0".getBytes(Charsets.UTF_8));
            envelopedFileStream.write(E3Constants.CRLF);
            envelopedFileStream.write("Content-Disposition: attachment; filename=\"smime.p7m\"".getBytes(Charsets.UTF_8));

            envelopedFileStream.write(E3Constants.CRLF);
            envelopedFileStream.write("Content-Transfer-Encoding: base64".getBytes(Charsets
                    .UTF_8));
            envelopedFileStream.write(E3Constants.CRLF);
            envelopedFileStream.write("Content-Type: ".getBytes(Charsets.UTF_8));
            envelopedFileStream.write(contentType.getBytes(Charsets.UTF_8));
            envelopedFileStream.write(E3Constants.CRLF);
            envelopedFileStream.write(E3Constants.CRLF);
            ByteStreams.copy(Preconditions.checkNotNull(encryptedBodyInputStream, "null encryptedBodyInputStream"), envelopedFileStream);
        } finally {
            Closeables.close(envelopedFileStream, true);
        }
    }

    private native int cmsDecryptJNI(final byte[] certDer, final byte[] privKeyDer, final String envelopedLoc, final String outputLoc);
}
