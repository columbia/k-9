package com.fsck.k9.mail.e3.smime;

import android.content.Context;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.internet.MimeMessage;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

import timber.log.Timber;

/**
 * Created on 1/16/2018.
 *
 * @author koh
 */

public class ComposedDecryptSMIMEToMessageFunction implements Function<Part, MimeMessage> {
    private final Function<Part, ByteSource> decryptFunction;

    public ComposedDecryptSMIMEToMessageFunction(final Context context, final String accountUuid, final String keyAlias, final String keyPassword) {
        this.decryptFunction = SMIMEDecryptFunctionFactory.get(context, accountUuid, keyAlias, keyPassword);
    }

    @Nullable
    @Override
    public MimeMessage apply(final @Nullable Part smimeMsg) {
        final ByteSource decryptedBytes = decryptFunction.apply(smimeMsg);

        if (decryptedBytes == null) {
            return null;
        }

        InputStream decryptedIn = null;
        try {
            decryptedIn = decryptedBytes.openStream();
            final MimeMessage decryptedMIME = new MimeMessage();
            decryptedMIME.parse(decryptedIn);

            return decryptedMIME;
        } catch (final IOException | MessagingException e) {
            Timber.e(e);
            throw new RuntimeException(e);
        } finally {
            Closeables.closeQuietly(decryptedIn);
        }
    }


}
