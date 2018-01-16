package com.fsck.k9.mail.e3.smime;

import android.content.Context;

import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.e3.E3KeyStoreService;
import com.fsck.k9.mail.e3.E3Utils;
import com.google.common.base.Function;
import com.google.common.io.ByteSource;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;

/**
 * Factory for creating {@link SMIMEDecryptFunction} instances.
 *
 * Created by koh on 12/11/2017.
 */

public final class SMIMEDecryptFunctionFactory {
    public static Function<Part, ByteSource> get(final E3Utils e3Utils, final String
            keyAlias, final String keyPassword) {
        // TODO: Make keyPassword separate from key store password
        final E3KeyStoreService keyStoreService = new E3KeyStoreService(e3Utils, keyPassword);
        final char[] e3Password = keyPassword.toCharArray();
        final KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(e3Password);

        try {
            final KeyStore.PrivateKeyEntry entry = keyStoreService.getEntry(keyAlias, protParam);
            return new SMIMEDecryptFunction(entry, e3Utils);
        } catch (final NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static Function<Part, ByteSource> get(final Context context, final String
            keyAlias, final String keyPassword) {
        final E3Utils e3Utils = new E3Utils(context);

        return get(e3Utils, keyAlias, keyPassword);
    }
}
