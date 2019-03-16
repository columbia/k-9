package com.fsck.k9.crypto;

import com.fsck.k9.mail.filter.Base64;

import java.util.Locale;

import kotlin.text.Charsets;
import okio.ByteString;

/**
 * Taken from the open-keychain/OpenKeychain project.
 */
public class KeyFormattingUtils {
    private static final int HEADER_LINE_LENGTH = 76;

    /**
     * Makes a human-readable version of a key ID, which is usually 64 bits: lower-case, no
     * leading 0x, space-separated quartets (for keys whose length in hex is divisible by 4)
     *
     * @param idHex - the key id
     * @return - the beautified form
     */
    public static String beautifyHex(String idHex) {
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
        return beautifyHex(convertKeyIdToHex(keyId));
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

    /**
     * Copied from {@link com.fsck.k9.autocrypt.AutocryptHeader}.
     *
     * @param keyData Key data in bytes.
     * @return Folded base64 key data.
     */
    public static String foldBase64KeyData(byte[] keyData) {
        String base64KeyData = ByteString.of(keyData).base64();
        StringBuilder result = new StringBuilder();

        for (int i = 0, base64Length = base64KeyData.length(); i < base64Length; i += HEADER_LINE_LENGTH) {
            if (i + HEADER_LINE_LENGTH <= base64Length) {
                result.append("\r\n ");
                result.append(base64KeyData, i, i + HEADER_LINE_LENGTH);
            } else {
                result.append("\r\n ");
                result.append(base64KeyData, i, base64Length);
            }
        }

        return result.toString();
    }

    public static byte[] unfoldBase64KeyData(byte[] foldedBase64Data) {
        String unfolded = new String(foldedBase64Data).replace("\r\n ", "");
        return Base64.decode(unfolded).getBytes(Charsets.UTF_8);
    }

    private static String convertKeyIdToHex32bit(long keyId) {
        String hexString = Long.toHexString(keyId & 0xffffffffL).toLowerCase(Locale.ENGLISH);
        while (hexString.length() < 8) {
            hexString = "0" + hexString;
        }
        return hexString;
    }
}
