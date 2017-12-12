package com.fsck.k9.mail.e3;

import com.fsck.k9.mail.filter.Base64;
import com.google.common.base.Objects;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;

/**
 * <p>
 * Created on 12/12/2017.
 *
 * @author koh
 */

public final class E3Key {
    private final KeyPair keyPair;
    private final Certificate[] certChain;
    private final String keyName;
    private final String e3Password;
    private final String sha256Digest;

    public E3Key(final String keyName, final KeyPair keyPair, final Certificate[] certChain,
                 final String e3Password) {
        this.keyName = keyName;
        this.keyPair = keyPair;
        this.certChain = certChain;
        this.e3Password = e3Password;

        try {
            final MessageDigest digester = MessageDigest.getInstance("sha256");
            final byte[] sha256Bytes = digester.digest(keyPair.getPublic().getEncoded());
            final byte[] base64Bytes = Base64.encodeBase64(sha256Bytes);
            sha256Digest = new String(base64Bytes);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public Certificate[] getCertChain() {
        return certChain;
    }

    public String getKeyName() {
        return keyName;
    }

    public String getE3Password() {
        return e3Password;
    }

    public String getSHA256Digest() {
        return sha256Digest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        E3Key e3Key = (E3Key) o;
        return Objects.equal(keyPair, e3Key.keyPair) && Objects.equal(certChain, e3Key.certChain)
                && Objects.equal(keyName, e3Key.keyName) && Objects.equal(e3Password, e3Key
                .e3Password) && Objects.equal(sha256Digest, e3Key.sha256Digest);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(keyPair, certChain, keyName, e3Password, sha256Digest);
    }
}
