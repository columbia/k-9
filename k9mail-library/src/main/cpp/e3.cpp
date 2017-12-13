#include <string.h>
#include <jni.h>
#include <openssl/pem.h>
#include <openssl/cms.h>
#include <openssl/err.h>
#include <android/log.h>

extern "C" {
const char *E3_CPP = "e3.cpp";
const char *E3_CPP_SIGN = "e3.cpp.sign";
const char *E3_CPP_ENCRYPT = "e3.cpp.encrypt";
const char *E3_CPP_DECRYPT = "e3.cpp.decrypt";

long long current_timestamp() {
    struct timeval te;
    gettimeofday(&te, NULL); // get current time
    return te.tv_sec * 1000LL + te.tv_usec / 1000; // calculate milliseconds
}

void logDuration(const int tag, const char *message, const long long start) {
    long long end = current_timestamp();
    long long duration = end - start;
    __android_log_print(tag, "e3.cpp", "%s: %lld", message, duration);
}

/**
 * @param j_cert_loc Path on system to the recipient's X509 certificate.
 * @param j_signed_loc Path on system to file containing signed message content to encrypt. Must be prefixed with at least MIME type.
 */
JNIEXPORT jint JNICALL
Java_com_fsck_k9_mail_e3_smime_SMIMEEncryptFunction_cmsEncryptJNI(JNIEnv *env,
                                                                  jobject thiz,
                                                                  jbyteArray j_cert,
                                                                  jstring j_signed_loc,
                                                                  jstring j_output_loc) {
    BIO *in = NULL, *out = NULL, *tbio = NULL;
    X509 *rcert = NULL;
    STACK_OF(X509) *recips = NULL;
    CMS_ContentInfo *cms = NULL;
    int ret = 1;
    int flags = CMS_STREAM;
    //const char *cert_loc = env->GetStringUTFChars(j_cert_loc, NULL);
    jbyte *cert = env->GetByteArrayElements(j_cert, NULL);
    jsize cert_length = env->GetArrayLength(j_cert);
    const char *msg_loc = env->GetStringUTFChars(j_signed_loc, NULL);
    const char *smime_output = env->GetStringUTFChars(j_output_loc, NULL);

    __android_log_print(ANDROID_LOG_DEBUG, E3_CPP_ENCRYPT, "cert: %p, msg_loc: %s, smime_output: %s",
                        cert, msg_loc, smime_output);

    OpenSSL_add_all_algorithms();
    ERR_load_crypto_strings();

    /* Read in recipient certificate */
    //tbio = BIO_new_file(cert_loc, "r");
    tbio = BIO_new_mem_buf(cert, cert_length);

    if (!tbio) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "tbio failed!");
        goto err;
    }

    //rcert = PEM_read_bio_X509(tbio, NULL, 0, NULL);

    /* DER to X509 */
    rcert = d2i_X509_bio(tbio, NULL);

    if (!rcert) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "rcert failed!");
        goto err;
    }

    /* Create recipient STACK and add recipient cert to it */
    recips = sk_X509_new_null();

    if (!recips || !sk_X509_push(recips, rcert)) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "recips failed!");
        goto err;
    }

    /*
     * sk_X509_pop_free will free up recipient STACK and its contents so set
     * rcert to NULL so it isn't freed up twice.
     */
    rcert = NULL;

    /* Open content being encrypted */
    in = BIO_new_file(msg_loc, "r");

    if (!in) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "in (msg_loc) failed!");
        goto err;
    }

    /* encrypt content */
    cms = CMS_encrypt(recips, in, EVP_aes_128_cbc(), flags);

    if (!cms) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "cms failed!");
        goto err;
    }

    out = BIO_new_file(smime_output, "w");
    if (!out) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "out (smime_output) failed!");
        goto err;
    }

    /* Write out S/MIME message (converts from CMS to S/MIME in b64) */
    if (!SMIME_write_CMS(out, cms, in, flags)) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_ENCRYPT, "SMIME_write_CMS() failed!");
        goto err;
    }

    ret = 0;

    err:
    if (ret) {
        fprintf(stderr, "Error Encrypting Data\n");
        ERR_print_errors_fp(stderr);
    }

    if (cert)
        //env->ReleaseStringUTFChars(j_cert_loc, cert_loc);
        env->ReleaseByteArrayElements(j_cert, cert, JNI_ABORT);
    if (msg_loc)
        env->ReleaseStringUTFChars(j_signed_loc, msg_loc);
    if (smime_output)
        env->ReleaseStringUTFChars(j_output_loc, smime_output);

    if (cms)
        CMS_ContentInfo_free(cms);
    if (rcert)
        X509_free(rcert);
    if (recips)
        sk_X509_pop_free(recips, X509_free);

    if (in)
        BIO_free(in);
    if (out)
        BIO_free(out);
    if (tbio)
        BIO_free(tbio);

    return ret;
}

/**
 * @param j_priv_loc Path on system to the sender's private key.
 * @param j_plain_loc Path on system to file containing plain content to sign.
 */
JNIEXPORT jint JNICALL
Java_com_fsck_k9_mail_e3_smime_SMIMEEncryptFunction_cmsSignJNI(JNIEnv *env,
                                                               jobject thiz, jbyteArray j_cert,
                                                               jbyteArray j_priv,
                                                               jstring j_plain_loc,
                                                               jstring j_output_loc) {
    BIO *in = NULL, *out = NULL, *tbio = NULL, *tbio_c = NULL;
    X509 *scert = NULL;
    EVP_PKEY *skey = NULL;
    CMS_ContentInfo *cms = NULL;
    //const char *cert_loc = env->GetStringUTFChars(j_cert_loc, NULL);
    //const char *priv_loc = env->GetStringUTFChars(j_priv_loc, NULL);
    jbyte *cert = env->GetByteArrayElements(j_cert, NULL);
    jsize cert_length = env->GetArrayLength(j_cert);
    jbyte *key = env->GetByteArrayElements(j_priv, NULL);
    jsize key_length = env->GetArrayLength(j_priv);
    const char *plain_loc = env->GetStringUTFChars(j_plain_loc, NULL);
    const char *sign_output = env->GetStringUTFChars(j_output_loc, NULL);
    int ret = 1;

    __android_log_print(ANDROID_LOG_INFO, E3_CPP, "priv=%p, smime_loc=%s, sign_output=%s",
                        j_priv, plain_loc, sign_output);

    /*
     * For simple S/MIME signing use CMS_DETACHED. On OpenSSL 1.0.0 only: for
     * streaming detached set CMS_DETACHED|CMS_STREAM for streaming
     * non-detached set CMS_STREAM
     */
    int flags = CMS_DETACHED | CMS_STREAM;

    OpenSSL_add_all_algorithms();
    ERR_load_crypto_strings();

    /* Read in signer certificate and private key */
    //tbio = BIO_new_file(priv_loc, "r");
    //tbio_c = BIO_new_file(cert_loc, "r");
    tbio = BIO_new_mem_buf(key, key_length);
    tbio_c = BIO_new_mem_buf(cert, cert_length);

    if (!tbio || !tbio_c) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN, "tbio||tbio_c failed!");
        goto err;
    }

    //scert = PEM_read_bio_X509(tbio_c, NULL, 0, NULL);
    //skey = PEM_read_bio_PrivateKey(tbio, NULL, 0, NULL);
    skey = d2i_PrivateKey_bio(tbio, NULL);
    scert = d2i_X509_bio(tbio_c, NULL);

    if (!scert || !skey) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN,
                            "scert/skey read failed! (cert=%p, scert=%p, skey=%p)", cert, scert, skey);
        goto err;
    }

    /* Open content being signed */

    in = BIO_new_file(plain_loc, "r");

    if (!in) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN, "in (plain_loc) failed! (smime_loc=%s)",
                            plain_loc);
        goto err;
    }

    /* Sign content */
    cms = CMS_sign(scert, skey, NULL, in, flags);

    if (!cms) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN, "CMS_sign() failed!");
        goto err;
    }

    out = BIO_new_file(sign_output, "w");
    if (!out) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN,
                            "out (sign_output) failed! (sign_output=%s)", sign_output);
        goto err;
    }

    if (!(flags & CMS_STREAM))
        BIO_reset(in);

    /* Write out S/MIME message */
    if (!SMIME_write_CMS(out, cms, in, flags)) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN,
                            "SMIME_write_CMS() failed! (sign_output=%s)", sign_output);
        goto err;
    }

    ret = 0;

    err:
    if (ret) {
        fprintf(stderr, "Error Signing Data\n");
        ERR_print_errors_fp(stderr);
    }

    if (cert)
        //env->ReleaseStringUTFChars(j_cert_loc, cert_loc);
        env->ReleaseByteArrayElements(j_cert, cert, JNI_ABORT);
    if (key)
        //env->ReleaseStringUTFChars(j_priv_loc, priv_loc);
        env->ReleaseByteArrayElements(j_priv, key, JNI_ABORT);
    if (plain_loc)
        env->ReleaseStringUTFChars(j_plain_loc, plain_loc);
    if (sign_output)
        env->ReleaseStringUTFChars(j_output_loc, sign_output);

    if (cms)
        CMS_ContentInfo_free(cms);
    if (scert)
        X509_free(scert);
    if (skey)
        EVP_PKEY_free(skey);

    if (in)
        BIO_free(in);
    if (out)
        BIO_free(out);
    BIO_free(tbio);
    if (tbio_c)
        BIO_free(tbio_c);

    return ret;
}

JNIEXPORT jint JNICALL
Java_com_fsck_k9_mail_e3_smime_SMIMEDecryptFunction_cmsDecryptJNI(JNIEnv *env,
                                                                  jobject thiz,
                                                                  jbyteArray j_cert,
                                                                  jbyteArray j_priv,
                                                                  jstring j_enveloped_data_loc,
                                                                  jstring j_output_loc) {
    BIO *in = NULL, *decrypted_cont = NULL, *out = NULL, *tbio = NULL, *tbio2 = NULL, *decrypted_cont_bio = NULL;
    X509 *rcert = NULL;
    X509_STORE *st = NULL;
    EVP_PKEY *rkey = NULL;
    CMS_ContentInfo *cms = NULL, *cms_decrypted = NULL;
    jbyte *cert = env->GetByteArrayElements(j_cert, NULL);
    jsize cert_length = env->GetArrayLength(j_cert);
    jbyte *key = env->GetByteArrayElements(j_priv, NULL);
    jsize key_length = env->GetArrayLength(j_priv);
    const char *env_data_loc = env->GetStringUTFChars(j_enveloped_data_loc, NULL);
    const char *decrypt_output = env->GetStringUTFChars(j_output_loc, NULL);
    int ret = 1;
    long long startLoad = 0, startLoadCertAndKey = 0, startDecrypt = 0, startVerify = 0, startReadCms = 0, startCmsVerify = 0;

    startLoad = current_timestamp();

    OpenSSL_add_all_algorithms();
    ERR_load_crypto_strings();

    logDuration(ANDROID_LOG_INFO, "BoringDecryptMeasure startLoad", startLoad);

    startLoadCertAndKey = current_timestamp();

    /* Read in recipient certificate and private key */
    //tbio = BIO_new(BIO_s_mem());
    //tbio2 = BIO_new(BIO_s_mem());
    tbio = BIO_new_mem_buf(cert, cert_length);
    tbio2 = BIO_new_mem_buf(key, key_length);

    if (!tbio || !tbio2) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT, "tbio||tbio_c failed!");
        goto err;
    }

    rcert = d2i_X509_bio(tbio, NULL);
    rkey = d2i_PrivateKey_bio(tbio2, NULL);

    if (!rcert || !rkey) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT,
                            "rcert/rkey read failed! (rcert=%p, rkey=%p)", rcert, rkey);
        goto err;
    }

    /* Set up trusted CA certificate store */
    st = X509_STORE_new();
    if (!X509_STORE_add_cert(st, rcert)) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT,
                            "X509_STORE_add_cert failed! (rcert=%p)", rcert);
        goto err;
    }

    logDuration(ANDROID_LOG_INFO, "BoringDecryptMeasure startLoadCertAndKey", startLoadCertAndKey);

    startDecrypt = current_timestamp();

    /* Open S/MIME message to decrypt */
    in = BIO_new_file(env_data_loc, "r");

    if (!in) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT, "in (env_data) failed!");
        goto err;
    }

    /* Parse message */
    cms = SMIME_read_CMS(in, NULL);
    if (!cms) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT,
                            "SMIME_read_CMS() failed! (env_data=%s), %s", env_data_loc,
                            ERR_error_string(ERR_peek_last_error(), NULL));
        goto err;
    }

    decrypted_cont = BIO_new(BIO_s_mem());
    if (!decrypted_cont) {
        goto err;
    }

    /* Decrypt S/MIME message */
    if (!CMS_decrypt(cms, rkey, rcert, NULL, decrypted_cont, 0)) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT, "CMS_decrypt() failed!, %s",
                            ERR_error_string(ERR_peek_last_error(), NULL));
        goto err;
    }

    logDuration(ANDROID_LOG_INFO, "BoringDecryptMeasure startDecrypt", startDecrypt);

    startVerify = current_timestamp();

    out = BIO_new_file(decrypt_output, "w");
    if (!out) {
        goto err;
    }

    startReadCms = current_timestamp();
    // Read decrypted content into decrypted_cont_bio
    cms_decrypted = SMIME_read_CMS(decrypted_cont, &decrypted_cont_bio);
    if (!cms_decrypted) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT,
                            "SMIME_read_CMS() for cms_decrypted failed! (env_data=%s), %s",
                            env_data_loc, ERR_error_string(ERR_peek_last_error(), NULL));
        /*
        BUF_MEM *bufferPtr = NULL;
        BIO_get_mem_ptr(decrypted_cont, &bufferPtr);
        char *temp_buff = (char *) malloc(bufferPtr->length);
        memcpy(temp_buff, bufferPtr->data, bufferPtr->length - 1);
        temp_buff[bufferPtr->length - 1] = 0;
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT, "SMIME_read_CMS() decrypted_cont BIO=%s", temp_buff);
         */
        goto err;
    }
    logDuration(ANDROID_LOG_INFO, "BoringDecryptMeasure startReadCms", startReadCms);

    startCmsVerify = current_timestamp();

    if (!CMS_verify(cms_decrypted, NULL, st, decrypted_cont_bio, out,
                    CMS_NO_SIGNER_CERT_VERIFY | CMS_DETACHED)) {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_DECRYPT,
                            "CMS_verify() failed! (decrypt_output=%s), %s", decrypt_output,
                            ERR_error_string(ERR_peek_last_error(), NULL));
        goto err;
    } else {
        __android_log_print(ANDROID_LOG_INFO, E3_CPP_SIGN, "successfully verified!");
    }

    logDuration(ANDROID_LOG_INFO, "BoringDecryptMeasure startCmsVerify", startCmsVerify);
    logDuration(ANDROID_LOG_INFO, "BoringDecryptMeasure startVerify", startVerify);
    __android_log_print(ANDROID_LOG_INFO, "BoringDecryptMeasure", "-----------------");

    ret = 0;

    err:
    if (ret) {
        fprintf(stderr, "Error Decrypting Data\n");
        ERR_print_errors_fp(stderr);
    }

    if (cert)
        env->ReleaseByteArrayElements(j_cert, cert, JNI_ABORT);
    if (key)
        env->ReleaseByteArrayElements(j_priv, key, JNI_ABORT);
    if (env_data_loc)
        env->ReleaseStringUTFChars(j_enveloped_data_loc, env_data_loc);
    if (decrypt_output)
        env->ReleaseStringUTFChars(j_output_loc, decrypt_output);

    if (cms)
        CMS_ContentInfo_free(cms);
    if (cms_decrypted)
        CMS_ContentInfo_free(cms_decrypted);
    if (rcert)
        X509_free(rcert);
    if (rkey)
        EVP_PKEY_free(rkey);
    if (st)
        X509_STORE_free(st);

    if (in)
        BIO_free(in);
    if (out)
        BIO_free(out);
    if (tbio)
        BIO_free(tbio);
    if (tbio2)
        BIO_free(tbio2);
    if (decrypted_cont)
        BIO_free(decrypted_cont);
    if (decrypted_cont_bio)
        BIO_free(decrypted_cont_bio);

    return ret;
}
}