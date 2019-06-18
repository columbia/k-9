package com.fsck.k9.crypto.e3

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream

class E3PublicKeyManager(private val openPgpApi: OpenPgpApi) {

    fun addPublicKeysToKeychain(keyEmail: E3KeyEmail) {
        for (keyBytes in keyEmail.publicKeys) {
            val pgpApiIntent = Intent(OpenPgpApi.ACTION_ADD_ENCRYPT_ON_RECEIPT_KEY)
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_ASCII_ARMORED_KEY, keyBytes)

            val result = openPgpApi.executeApi(pgpApiIntent, null as InputStream?, null)
            val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

            if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
                Timber.d("addPublicKeysToKeychain(): Successfully added E3 public key to OpenKeychain")
            } else {
                Timber.d("addPublicKeysToKeychain(): Failed to add E3 public key to OpeKeychain: $resultCode")
            }
        }
    }

    fun deletePublicKeysFromKeychain(keyEmail: E3KeyEmail) {
        if (keyEmail.deletedPublicKeys.isEmpty()) {
            Timber.d("Found no E3 public keys to delete")
            return
        }

        for (keyBytes in keyEmail.deletedPublicKeys) {
            val pgpApiIntent = Intent(OpenPgpApi.ACTION_DELETE_ENCRYPT_ON_RECEIPT_KEY)
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_ASCII_ARMORED_KEY, keyBytes)

            val result = openPgpApi.executeApi(pgpApiIntent, null as InputStream?, null)
            val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

            if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
                Timber.d("deletePublicKeysFromKeychain(): Successfully deleted E3 public key from OpenKeychain")
            } else {
                Timber.d("deletePublicKeysFromKeychain(): Failed to delete E3 public key to OpeKeychain: $resultCode")
            }
        }
    }

    fun requestPgpKey(openPgpApi: OpenPgpApi, keyId: Long, armored: Boolean, fingerprint: Boolean): KeyResult {
        val intent = Intent(OpenPgpApi.ACTION_GET_KEY)
        intent.putExtra(OpenPgpApi.EXTRA_KEY_ID, keyId)
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, armored)
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_FINGERPRINT, fingerprint)
        val baos = ByteArrayOutputStream()
        val result = openPgpApi.executeApi(intent, null as InputStream?, baos)

        val resultIntent = result.getParcelableExtra<PendingIntent>(OpenPgpApi.RESULT_INTENT)
        val identity = result.getStringExtra(OpenPgpApi.RESULT_USER_ID)

        if (fingerprint) {
            val fingerprintString = result.getStringExtra(OpenPgpApi.RESULT_FINGERPRINT)
            val fingerprintBitmap = result.getParcelableExtra<Bitmap>(OpenPgpApi.RESULT_FINGERPRINT_QR)

            return KeyResult(resultIntent, baos, identity, KeyFingerprint(fingerprintString, fingerprintBitmap))
        } else {
            return KeyResult(resultIntent, baos, identity, null)
        }
    }
}


data class KeyResult(val pendingIntent: PendingIntent, val resultData: ByteArrayOutputStream,
                     val identity: String, val fingerprint: KeyFingerprint?)

data class KeyFingerprint(val hexString: String?, val qrBitmap: Bitmap?)