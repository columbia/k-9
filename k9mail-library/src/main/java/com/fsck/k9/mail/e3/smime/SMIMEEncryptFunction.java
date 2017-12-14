package com.fsck.k9.mail.e3.smime;

import android.util.Log;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.body.DeferredFileBody;
import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.E3Utils;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeUtility;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.CertificateEncodingException;
import java.util.Date;
import java.util.List;

/**
 * Encrypts a cleartext {@link MimeMessage} into an S/MIME format {@link MimeMessage}.
 * <p>
 * Relies on JNI code because we want to take advantage of openssl.
 * <p>
 * Created on 12/14/2017.
 *
 * @author koh
 */
public class SMIMEEncryptFunction implements Function<MimeMessage, MimeMessage> {
    private static final String SMIME_CONTENT_TYPE = "application/pkcs7-mime; name=\"smime.p7m\";" +
            "" + " smime-type=enveloped-data";
    private static final String SMIME_CONTENT_TRANSFER_ENCODING = "base64";
    private static final String SMIME_CONTENT_DISPOSITION = "attachment; filename=\"smime.p7m\"";
    private static final String MIME_HEADER_PARAM_BOUNDARY = "boundary";

    private final E3Utils e3Utils;
    private final PrivateKeyEntry keyEntry;

    static {
        System.loadLibrary("e3-jni");
    }

    public SMIMEEncryptFunction(final PrivateKeyEntry keyEntry, final E3Utils e3Utils) {
        this.e3Utils = e3Utils;
        this.keyEntry = keyEntry;
    }

    @Override
    public MimeMessage apply(final MimeMessage original) {
        final String[] contentType = original.getHeader(MimeHeader.HEADER_CONTENT_TYPE);

        for (final String smimeHeuristic : E3Constants.SMIME_CONTENT_TYPE_HEURISTICS) {
            Preconditions.checkArgument(!contentType[0].contains(smimeHeuristic), "MimeMessage is" +
                    " already encrypted? " + contentType[0]);
        }

        // Makes a new copy of the original message (yes, should usually avoid using clone())
        final MimeMessage returnMessage = original.clone();
        final Body unencryptedBody = original.getBody();
        InputStream bodyInputStream;

        try {
            bodyInputStream = unencryptedBody.getInputStream();
        } catch (final UnsupportedOperationException e) {
            bodyInputStream = null;
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        }

        try {
            final String boundary;
            final DeferredFileBody encryptedBody;

            if (bodyInputStream == null && unencryptedBody instanceof Multipart) {
                // Encrypt each Part of Multipart messages
                final List<BodyPart> bodyParts = ((Multipart) unencryptedBody).getBodyParts();
                boundary = MimeUtility.getHeaderParameter(contentType[0],
                        MIME_HEADER_PARAM_BOUNDARY);

                Log.d(E3Constants.LOG_TAG, "Starting encryptMultipartBody");
                encryptedBody = encryptMultipartBody(original, bodyParts, boundary);
                Log.d(E3Constants.LOG_TAG, "Ending encryptMultipartBody");
            } else {
                // TODO: Figure this out where it's not a Multipart
                //smimeMessage = encryptSinglePartCMS(message, bodyInputStream);
                boundary = null;
                encryptedBody = null;
            }

            returnMessage.setFlag(Flag.E3, true);
            returnMessage.setBody(encryptedBody);

            if (!Strings.isNullOrEmpty(boundary)) {
                returnMessage.setHeader(MimeHeader.HEADER_CONTENT_TYPE, SMIME_CONTENT_TYPE + "; " +
                        "boundary=\"" + boundary + "\"");
            } else {
                returnMessage.setHeader(MimeHeader.HEADER_CONTENT_TYPE, SMIME_CONTENT_TYPE);
            }

            returnMessage.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION,
                    SMIME_CONTENT_DISPOSITION);
            returnMessage.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING,
                    SMIME_CONTENT_TRANSFER_ENCODING);

            returnMessage.setInternalDate(new Date()); // Set internal date to now
            return returnMessage;
        } catch (final MessagingException | CertificateEncodingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DeferredFileBody encryptMultipartBody(final MimeMessage message, final List<BodyPart>
            bodyParts, final String boundary) throws IOException, MessagingException,
            CertificateEncodingException {
        final File mimeFile = buildMIMEMessage(message.getHeader(MimeHeader.HEADER_CONTENT_TYPE)
                [0], bodyParts, boundary);
        final File signedFile = e3Utils.getTempFile("signed", "signed-plaintext");
        final File envelopedFile = e3Utils.getTempFile("encrypted", "enveloped-data");
        final byte[] privateKeyDer = keyEntry.getPrivateKey().getEncoded();
        final byte[] certDer = keyEntry.getCertificate().getEncoded();

        try {
            // TODO: Combine sign and encrypt into one JNI call
            final int signResult = cmsSignJNI(certDer, privateKeyDer, mimeFile.getAbsolutePath(),
                    signedFile.getAbsolutePath());

            if (signResult != 0) {
                throw new RuntimeException("Failed to do JNI signing, return code: " + signResult);
            } else {
                Log.d(E3Constants.LOG_TAG, "Got good JNI sign result for " + signedFile
                        .getAbsolutePath());
            }

            final int encryptResult = cmsEncryptJNI(certDer, signedFile.getAbsolutePath(),
                    envelopedFile.getAbsolutePath());

            if (encryptResult != 0) {
                throw new RuntimeException("Failed to do JNI encryption, return code: " +
                        encryptResult);
            } else {
                Log.d(E3Constants.LOG_TAG, "Got good JNI encrypt result for " + envelopedFile
                        .getAbsolutePath());
            }

            return buildSMIMEMessageBody(envelopedFile, SMIME_CONTENT_TRANSFER_ENCODING);
        } finally {
            if (!mimeFile.delete()) {
                Log.e(E3Constants.LOG_TAG, "Failed to delete mimeFile: " + mimeFile
                        .getAbsolutePath());
            }

            if (!signedFile.delete()) {
                Log.e(E3Constants.LOG_TAG, "Failed to delete signedFile: " + signedFile
                        .getAbsolutePath());
            }

            if (!envelopedFile.delete()) {
                Log.e(E3Constants.LOG_TAG, "Failed to delete envelopedFile: " + envelopedFile
                        .getAbsolutePath());
            }
        }
    }

    private File buildMIMEMessage(final String contentType, final List<BodyPart> bodyParts, final
    String boundary) throws IOException, MessagingException {
        final String dashedBoundaryString = "--" + boundary;
        final String dashedBoundaryEndString = "--" + boundary + "--";
        final byte[] dashedBoundary = dashedBoundaryString.getBytes(Charsets.UTF_8);
        final File tempOutput = e3Utils.getTempFile("plain", "mime");
        final FileOutputStream outputStream = new FileOutputStream(tempOutput);

        try {
            if (!bodyParts.isEmpty()) {
                // Writes the overall header (such as multipart/alternative or multipart/mixed)
                // message.writeHeaderTo(outputStream);
                final String innerHeader = MimeHeader.HEADER_CONTENT_TYPE + ": " + contentType;
                outputStream.write(innerHeader.getBytes(Charsets.UTF_8));
                outputStream.write(E3Constants.CRLF);
                outputStream.write("MIME-Version: 1.0".getBytes(Charsets.UTF_8));
                outputStream.write(E3Constants.CRLF);
                outputStream.write(E3Constants.CRLF);
            }

            // Write out headers, since we need to envelope the entire thing
            for (final BodyPart bodyPart : bodyParts) {
                outputStream.write(dashedBoundary);
                outputStream.write(E3Constants.CRLF);

                // Before I did this by manually writing the header... did something change?
                bodyPart.writeTo(outputStream);
                outputStream.write(E3Constants.CRLF);
            }

            outputStream.write(dashedBoundaryEndString.getBytes(Charsets.UTF_8));
            outputStream.write(E3Constants.CRLF);
        } finally {
            Closeables.close(outputStream, false);
        }

        Log.d(E3Constants.LOG_TAG, FileUtils.readFileToString(tempOutput));

        return tempOutput;
    }

    private DeferredFileBody buildSMIMEMessageBody(final File bodyFile, final String
            contentEncoding) throws MessagingException, IOException {
        final DeferredFileBody body = new DeferredFileBody(e3Utils.getFileFactory(),
                contentEncoding);
        final OutputStream tempFileOutputStream = body.getOutputStream();
        final BufferedReader b = new BufferedReader(new FileReader(bodyFile));

        try {
            /*
             * Skip unnecessary headers added by openssl (the native code)
             *
             * MIME-Version: 1.0
             * Content-Disposition: attachment; filename="smime.p7m"
             * Content-Type: application/pkcs7-mime; smime-type=enveloped-data; name="smime.p7m"
             * Content-Transfer-Encoding: base64
             */
            for (int i = 0; i < 4; ++i) {
                b.readLine();
            }

            String line;

            while ((line = b.readLine()) != null) {
                tempFileOutputStream.write(line.getBytes(Charsets.UTF_8));
                tempFileOutputStream.write(E3Constants.CRLF);
            }
        } finally {
            Closeables.close(tempFileOutputStream, true);
            Closeables.close(b, true);
        }

        return body;
    }

    private native int cmsEncryptJNI(final byte[] certDer, final String messageLoc, final String
            outputLoc);

    private native int cmsSignJNI(final byte[] certDer, final byte[] privateKeyDer, final String
            plainLoc, final String outputLoc);
}
