package com.fsck.k9.crypto;

import java.util.Locale;

/**
 * Taken from the open-keychain/OpenKeychain project.
 */
public class KeyFormattingUtils {
    /**
     * Makes a human-readable version of a key ID, which is usually 64 bits: lower-case, no
     * leading 0x, space-separated quartets (for keys whose length in hex is divisible by 4)
     *
     * @param idHex - the key id
     * @return - the beautified form
     */
    public static String beautifyKeyId(String idHex) {
        if (idHex.startsWith("0x")) {
            idHex = idHex.substring(2);
        }
        if ((idHex.length() % 4) == 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < idHex.length(); i += 4) {
                if (i != 0) {
                    sb.appendCodePoint(0x2008); // U+2008 PUNCTUATION SPACE
                }
                sb.append(idHex.substring(i, i + 4).toLowerCase(Locale.US));
            }
            idHex = sb.toString();
        }

        return idHex;
    }

    /**
     * Makes a human-readable version of a key ID, which is usually 64 bits: lower-case, no
     * leading 0x, space-separated quartets (for keys whose length in hex is divisible by 4)
     *
     * @param keyId - the key id
     * @return - the beautified form
     */
    public static String beautifyKeyId(long keyId) {
        return beautifyKeyId(convertKeyIdToHex(keyId));
    }

    /**
     * Convert key id from long to 64 bit hex string
     * <p/>
     * V4: "The Key ID is the low-order 64 bits of the fingerprint"
     * <p/>
     * see http://tools.ietf.org/html/rfc4880#section-12.2
     *
     * @param keyId
     * @return
     */
    public static String convertKeyIdToHex(long keyId) {
        long upper = keyId >> 32;
        if (upper == 0) {
            // this is a short key id
            return convertKeyIdToHexShort(keyId);
        }
        return "0x" + convertKeyIdToHex32bit(keyId >> 32) + convertKeyIdToHex32bit(keyId);
    }

    public static String convertKeyIdToHexShort(long keyId) {
        return "0x" + convertKeyIdToHex32bit(keyId);
    }

    private static String convertKeyIdToHex32bit(long keyId) {
        String hexString = Long.toHexString(keyId & 0xffffffffL).toLowerCase(Locale.ENGLISH);
        while (hexString.length() < 8) {
            hexString = "0" + hexString;
        }
        return hexString;
    }
}
