package com.fsck.k9.mail.e3;

/**
 * Represents the E3 mode of operation for a given account.
 * <p>
 * Created on 1/24/2018.
 *
 * @author koh
 */

public enum E3Type {
    STANDALONE,
    PASSIVE;

    public static E3Type fromString(final String e3TypeString) {
        switch (e3TypeString.toLowerCase()) {
            case "standalone":
                return STANDALONE;
            case "passive":
                return PASSIVE;
            default:
                throw new IllegalArgumentException(e3TypeString);
        }
    }
}
