package com.fsck.k9.mail.e3;

import com.google.common.base.Function;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Random;

import static com.fsck.k9.mail.e3.E3KeyStoreService.BC_PROVIDER;

/**
 * Generate self-signed RSA key-pairs.
 *
 * Created on 12/3/2016.
 *
 * @author koh
 */
public class E3X509PKCS12Generator {
    private final Function<String, String> aliasFunction;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public E3X509PKCS12Generator() {
        this.aliasFunction = new E3AliasFunction();
    }

    /**
     * Generates a new E3 keypair and returns its alias as set in {@link E3KeyStoreService}.
     * <p>
     * The certificate for the keypair is self-signed.
     * <p>
     * All aliases are for use with E3 only.
     *
     * @param email          Email to use as X509 subject ("owner") to use for the generated
     *                       public key.
     * @param pkcs12Password The password to protect the PKCS#12 store.
     * @throws KeyStoreException
     * @throws NoSuchProviderException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws OperatorCreationException
     */
    public E3Key generateNewE3Key(final String email, final String pkcs12Password) throws
            KeyStoreException, NoSuchProviderException, IOException, NoSuchAlgorithmException,
            CertificateException, OperatorCreationException {
        final KeyPair keyPair = createKeyPair();
        final X509CertificateHolder certHolder = createSelfSignedCertificate("cn=" + email,
                keyPair);

        // This is really silly, but X509CertificateHolder doesn't expose a getter.
        final X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC_PROVIDER)
                .getCertificate(certHolder);
        final Certificate[] certChain = new Certificate[]{cert};
        final String alias = aliasFunction.apply(email);

        return new E3Key(alias, keyPair, certChain, pkcs12Password);
    }

    private KeyPair createKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {
        // GENERATE THE PUBLIC/PRIVATE RSA KEY PAIR
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", BC_PROVIDER);
        keyPairGenerator.initialize(2048, new SecureRandom());

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Creates a self-signed certificate WITH private key that expires in 100 years.
     *
     * @param subject The subject ("owner") of the certificate, and also the issuer.
     * @param keyPair The keypair to put into the certificate.
     * @return A {@link X509CertificateHolder} which contains a {@link X509Certificate} and
     * extensions.
     * @throws OperatorCreationException If creating the signer builder for the certificate fails.
     */
    private X509CertificateHolder createSelfSignedCertificate(final String subject, final KeyPair
            keyPair) throws OperatorCreationException {
        final PublicKey publicKey = keyPair.getPublic();
        final PrivateKey privateKey = keyPair.getPrivate();
        final Calendar notBeforeCalendar = Calendar.getInstance();
        final Calendar notAfterCalendar = Calendar.getInstance();

        // Expires in 100 years
        notAfterCalendar.add(Calendar.YEAR, 100);

        final X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder( //
                new X500Name(subject), // Issuer = Subject since self-signed
                BigInteger.valueOf(Math.abs(new Random().nextLong())), // Random sn
                notBeforeCalendar.getTime(), //
                notAfterCalendar.getTime(), //
                new X500Name(subject), //
                publicKey);

        final ContentSigner contentSigner = new JcaContentSignerBuilder
                ("SHA256WithRSAEncryption").setProvider(BC_PROVIDER).build(privateKey);

        return certBuilder.build(contentSigner);
    }
}
