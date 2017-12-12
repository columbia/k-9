package com.fsck.k9.mail.e3;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Enumeration;

import timber.log.Timber;

/**
 * <p>
 * Created on 12/12/2017.
 *
 * @author koh
 */

public class PFXToE3KeyFunction implements Function<byte[], E3Key> {
    private final String e3KeyName;
    private final String pfxPassword;

    public PFXToE3KeyFunction(final String e3KeyName, final String pfxPassword) {
        this.e3KeyName = e3KeyName;
        this.pfxPassword = pfxPassword;
    }

    @Override
    public E3Key apply(final byte[] input) {
        try {
            final KeyStore pkcs12KeyStore = KeyStore.getInstance("pkcs12", "BC");
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);

            pkcs12KeyStore.load(inputStream, pfxPassword.toCharArray());

            // The joys of super old java libraries...
            final Enumeration<String> aliasEnumeration = pkcs12KeyStore.aliases();
            final String alias = aliasEnumeration.nextElement();
            final Certificate[] certChain = pkcs12KeyStore.getCertificateChain(alias);
            final PublicKey pubKey = pkcs12KeyStore.getCertificate(alias).getPublicKey();
            final Key key = pkcs12KeyStore.getKey(alias, pfxPassword.toCharArray());

            Preconditions.checkState(key instanceof PrivateKey, "The key in the PFX file was not a private key");

            final PrivateKey privKey = (PrivateKey) key;

            if (!aliasEnumeration.hasMoreElements()) {
                Timber.w(E3Constants.LOG_TAG, "Got E3 PFX file with more than 1 certificate chain alias?");
            }

            return new E3Key(e3KeyName, new KeyPair(pubKey, privKey), certChain, pfxPassword);
        } catch (final KeyStoreException | NoSuchProviderException e) {
            throw new RuntimeException("Could not get KeyStore instance", e);
        } catch (final IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Could not load PFX file to keystore", e);
        } catch (final UnrecoverableKeyException e) {
            throw new RuntimeException("Could not retrieve keys from PFX file", e);
        }
    }
}
