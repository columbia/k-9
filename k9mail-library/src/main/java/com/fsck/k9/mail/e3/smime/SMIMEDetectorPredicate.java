package com.fsck.k9.mail.e3.smime;

import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;

/**
 * Tries to detect whether a {@link MimeMessage} is SMIME encrypted or not.
 * Created on 1/15/2018.
 *
 * @author koh
 */

public class SMIMEDetectorPredicate implements Predicate<MimeMessage> {
    @Override
    public boolean apply(final @Nullable MimeMessage input) {
        if (input == null) {
            return false;
        }

        final String[] contentType = input.getHeader(MimeHeader.HEADER_CONTENT_TYPE);

        for (final String smimeHeuristic : E3Constants.SMIME_CONTENT_TYPE_HEURISTICS) {
            if (contentType[0].contains(smimeHeuristic)) {
                return true;
            }
        }

        return false;
    }
}
