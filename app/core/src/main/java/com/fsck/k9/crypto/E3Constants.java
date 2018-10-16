package com.fsck.k9.crypto;


public final class E3Constants {
    // X-E3-ENCRYPTED: accountEmail
    public static final String MIME_E3_ENCRYPTED_HEADER = "X-E3-ENCRYPTED";
    public static final String MIME_E3_DIGEST = "X-E3-DIGEST";
    public static final String MIME_E3_NAME = "X-E3-NAME";
    public static final String MIME_E3_VERIFICATION = "X-E3-VERIFICATION";

    public static final String CONTENT_TYPE_PGP_KEYS = "application/pgp-keys";

    public static final int E3_VERIFICATION_PHRASES = 3;
    public static final int E3_VERIFICATION_PHRASE_LENGTH = 3;
    public static final String E3_VERIFICATION_PHRASE_DELIMITER = " ";
}
