package com.fsck.k9.mail.e3;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;

/**
 * Created on 12/8/2017.
 *
 * @author koh
 */

public final class E3Constants {
    public static final String LOG_TAG = "E3_LOG";
    public static final String MEASURE_LOG_TAG = "E3_MEASURE";
    public static final String E3_DIGEST_HEADER = "X-E3-DIGEST";
    public static final String E3_NAME_HEADER = "X-E3-NAME";

    public final static ImmutableSet<String> SMIME_CONTENT_TYPE_HEURISTICS = ImmutableSet.of
            ("pkcs7-mime", "smime", "p7m");

    public static final byte[] CRLF = "\r\n".getBytes(Charsets.UTF_8);
}

