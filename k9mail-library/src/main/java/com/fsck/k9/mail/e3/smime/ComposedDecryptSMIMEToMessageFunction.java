package com.fsck.k9.mail.e3.smime;

import android.content.Context;

import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeMessage;
import com.google.common.base.Function;
import com.google.common.io.ByteSource;

import javax.annotation.Nullable;

/**
 * Created on 1/16/2018.
 *
 * @author mauzel
 */

public class ComposedDecryptSMIMEToMessageFunction implements Function<Part, MimeMessage> {
    private final Function<Part, ByteSource> decryptFunction;

    public ComposedDecryptSMIMEToMessageFunction(final Context context, final String keyAlias, final String keyPassword) {
        this.decryptFunction = SMIMEDecryptFunctionFactory.get(context, keyAlias, keyPassword);
    }

    @Nullable
    @Override
    public MimeMessage apply(final @Nullable Part smimeMsg) {
        final ByteSource decryptedBytes = decryptFunction.apply(smimeMsg);

        return null;
    }
}
