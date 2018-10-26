package com.fsck.k9.crypto.e3;


public final class E3Constants {
    // X-E3-ENCRYPTED: accountEmail
    public static final String MIME_E3_ENCRYPTED_HEADER = "X-E3-ENCRYPTED";
    public static final String MIME_E3_DIGEST = "X-E3-DIGEST";
    public static final String MIME_E3_NAME = "X-E3-NAME";
    public static final String MIME_E3_VERIFICATION = "X-E3-VERIFICATION";
    public static final String MIME_E3_TIMESTAMP = "X-E3-TIMESTAMP";
    public static final String MIME_E3_UID = "X-E3-UID"; // Used for uniquely identifying E3 key emails *internally*

    public static final String CONTENT_TYPE_PGP_KEYS = "application/pgp-keys";

    public static final int E3_VERIFICATION_PHRASES = 3;
    public static final int E3_VERIFICATION_PHRASE_LENGTH = 3;
    public static final String E3_VERIFICATION_PHRASE_DELIMITER = " ";
    public static final long E3_VERIFICATION_ALLOWED_AGE_MS = 1200L; //120000L;

    // TODO: E3 make these strings usable by the Notification classes
    // <string name="e3_key_notification_title">New E3 device detected</string>
    // <string name="e3_key_notification_text">Press to verify your new device.</string>
    public static final String E3_KEY_NOTIFICATION_TITLE = "New E3 device detected";
    public static final String E3_KEY_NOTIFICATION_TEXT = "Press to verify your new device.";
    public static final String E3_KEY_NOTIFICATION_BIG_TEXT = "A new E3 device was detected. Press to verify your new device.";
}
