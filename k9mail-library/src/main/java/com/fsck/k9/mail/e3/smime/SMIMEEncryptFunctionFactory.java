package com.fsck.k9.mail.e3.smime;

import android.content.Context;
import android.util.Log;

import com.fsck.k9.mail.e3.E3Constants;
import com.fsck.k9.mail.e3.E3KeyStoreService;
import com.fsck.k9.mail.e3.E3Utils;
import com.fsck.k9.mail.internet.MimeMessage;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;

/**
 * Created on 12/14/2017.
 *
 * @author koh
 */

public final class SMIMEEncryptFunctionFactory {
    public static Function<MimeMessage, MimeMessage> get(final Context context, final String accountUuid, final String
            keyAlias, final String keyPassword) {
        try {
            final E3Utils e3Utils = new E3Utils(context);
            final E3KeyStoreService keyStoreService = new E3KeyStoreService(e3Utils, accountUuid, keyPassword);
            final char[] e3Password = Preconditions.checkNotNull(keyPassword).toCharArray();

            Log.d(E3Constants.LOG_TAG, "Got alias: " + keyAlias);

            final ProtectionParameter protParam = new PasswordProtection(e3Password);
            final PrivateKeyEntry entry = keyStoreService.getEntry(keyAlias, protParam);

            return new SMIMEEncryptFunction(entry, e3Utils);
        } catch (final NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException
                e) {
            throw new RuntimeException("Failed to build SMIMEEncryptFunction", e);
        }
    }
}