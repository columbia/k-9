package com.fsck.k9.mailstore;

import android.support.annotation.NonNull;

import com.fsck.k9.mail.internet.MimeBodyPart;

/**
 * Created by mauzel on 12/11/2017.
 */

public class SMIMECryptoResultAnnotation {
    @NonNull
    private final CryptoError errorType;
    private final MimeBodyPart replacementData;

    public SMIMECryptoResultAnnotation(@NonNull CryptoError errorType, MimeBodyPart replacementData) {
        this.errorType = errorType;
        this.replacementData = replacementData;
    }

    public static SMIMECryptoResultAnnotation createSMIMEResultAnnotation(MimeBodyPart replacementData) {
        return new SMIMECryptoResultAnnotation(CryptoError.SMIME_OK, replacementData);
    }

    public static SMIMECryptoResultAnnotation createErrorAnnotation(CryptoError error, MimeBodyPart replacementData) {
        if (error == CryptoError.SMIME_OK) {
            throw new AssertionError("CryptoError must be actual error state!");
        }
        return new SMIMECryptoResultAnnotation(error, replacementData);
    }

    @NonNull
    public CryptoError getErrorType() {
        return errorType;
    }

    public MimeBodyPart getReplacementData() {
        return replacementData;
    }

    public enum CryptoError {
        ENCRYPTED_BUT_UNSUPPORTED,
        SMIME_ENCRYPTED,
        SMIME_ENCRYPTED_BUT_INCOMPLETE,
        SMIME_OK,
    }
}
