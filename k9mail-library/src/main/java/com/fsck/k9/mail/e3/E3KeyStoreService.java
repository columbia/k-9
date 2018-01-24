package com.fsck.k9.mail.e3;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;

/**
 * Service for accessing the singleton E3 {@link KeyStore}.
 * <p>
 * Created on 12/5/2016.
 *
 * @author koh
 */

public final class E3KeyStoreService {
    // The provider is BouncyCastle but it's identified as SC (SpongyCastle)
    static final String BC_PROVIDER = "SC";

    @VisibleForTesting
    static final String PKCS12_FILE_NAME_SUFFIX = "_e3_pkcs12_store.pfx";

    private static KeyStore KEY_STORE;
    private final E3Utils e3Utils;
    private final String password;
    private final String pkcs12FileName;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public E3KeyStoreService(final E3Utils e3Utils, final String accountUuid, @Nullable final String password) {
        this.e3Utils = e3Utils;
        this.password = password;
        this.pkcs12FileName = accountUuid + PKCS12_FILE_NAME_SUFFIX;

        initializeKeyStore();
    }

    public E3KeyStoreService(final E3Utils e3Utils, final String accountUuid) {
        this(e3Utils, accountUuid, null);
    }

    /**
     * Write out the contents of the key store to disk in a private location.
     * <p>
     * The location is determined by {@link Context} because it gives us access to a private
     * directory on disk specifically for this application. It is protected by the OS.
     *
     * @param password Password to protect the PKCS#12 file on disk.
     * @throws IOException
     */
    public synchronized void store(final String password) throws IOException {
        OutputStream output = null;

        try {
            output = e3Utils.openFileOutput(pkcs12FileName, Context.MODE_PRIVATE);

            KEY_STORE.store(output, password.toCharArray());

            Log.i(E3Constants.LOG_TAG, "Stored PKCS#12 store to disk in " + e3Utils.getFileStreamPath(pkcs12FileName));
        } catch (final KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            if (output == null) {
                throw new RuntimeException("Failed to open " + pkcs12FileName);
            }

            throw new RuntimeException("Failed to store the PKCS12 keystore");
        } finally {
            Closeables.close(output, true);
        }
    }

    /**
     * Gets a {@link File} pointing to the location of the PKCS#12 file stored by this class.
     * <p>
     * Note: Be careful to not delete or overwrite this file unless you know what you are doing.
     *
     * @return An {@link Optional} which may or may not contain a {@link File}.
     */
    public Optional<File> getStoreFile() {
        final File pkcs12File = e3Utils.getFileStreamPath(pkcs12FileName);

        if (pkcs12File.exists()) {
            return Optional.of(pkcs12File);
        }

        return Optional.absent();
    }

    /**
     * Sets (adds) a key entry for the given alias, password, and cert chain in the key store.
     *
     * @param alias    The alias for the key entry.
     * @param key      The actual key, usually a {@link java.security.PrivateKey}.
     * @param password A password to protect the key entry.
     * @param chain
     * @return
     * @throws KeyStoreException
     */
    public synchronized KeyStore setKeyEntry(final String alias, final Key key, @Nullable final
    String password, final Certificate[] chain) throws KeyStoreException {
        if (Strings.isNullOrEmpty(password)) {
            KEY_STORE.setKeyEntry(alias, key, null, chain);
        } else {
            KEY_STORE.setKeyEntry(alias, key, password.toCharArray(), chain);
        }

        return KEY_STORE;
    }

    /**
     * Get an entry in the underlying key store by alias.
     * <p>
     * A {@link ProtectionParameter} can optionally be supplied (can be null).
     *
     * @param alias               The alias of the entry you want to retrieve.
     * @param protectionParameter A protection parameter for the entry.
     * @return An {@link Entry} which is usually a {@link java.security.KeyStore.PrivateKeyEntry}.
     * @throws NoSuchAlgorithmException
     * @throws UnrecoverableEntryException
     * @throws KeyStoreException
     */
    public synchronized PrivateKeyEntry getEntry(final String alias, @Nullable final ProtectionParameter
            protectionParameter) throws NoSuchAlgorithmException, UnrecoverableEntryException,
            KeyStoreException {
        return (PrivateKeyEntry) KEY_STORE.getEntry(alias, protectionParameter);
    }

    /**
     * Returns the aliases currently in the key store.
     *
     * @return A {@link List} of aliases.
     * @throws KeyStoreException If the underlying key store could not return the aliases.
     */
    public synchronized List<String> aliases() throws KeyStoreException {
        return Collections.list(KEY_STORE.aliases());
    }

    private synchronized void initializeKeyStore() {
        if (KEY_STORE == null) {
            InputStream input = null;

            try {
                KEY_STORE = KeyStore.getInstance("PKCS12", BC_PROVIDER);

                final File keyStoreFile = e3Utils.getFileStreamPath(pkcs12FileName);

                if (keyStoreFile != null && keyStoreFile.exists() && password != null) {
                    input = new FileInputStream(keyStoreFile);
                    KEY_STORE.load(input, password.toCharArray());
                    Log.i(E3Constants.LOG_TAG, "Loaded keystore from file");
                } else {
                    KEY_STORE.load(null, null);
                }
            } catch (final Exception e) {
                throw new RuntimeException("Failed to initialize E3 keystore", e);
            } finally {
                Closeables.closeQuietly(input);
            }
        }
    }
}
