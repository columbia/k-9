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
        if (keyEmail.deletedPublicKeyIds.isEmpty()) {
            Timber.d("Found no E3 public keys to delete")
            return
        }

        for (keyId in keyEmail.deletedPublicKeyIds) {
            deletePublicKeyFromKeychain(E3PublicKeyIdName(keyId, null))
        }
    }

    fun deletePublicKeyFromKeychain(e3KeyIdName: E3PublicKeyIdName): Boolean {
        val intent = Intent(OpenPgpApi.ACTION_DELETE_ENCRYPT_ON_RECEIPT_KEY)
        intent.putExtra(OpenPgpApi.EXTRA_KEY_ID, e3KeyIdName.keyId)

        val deleteKeyResult = openPgpApi.executeApi(intent, null as InputStream?, null)

        val resultCode = deleteKeyResult.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

        if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
            Timber.d("Successfully deleted E3 key from OpenKeychain $e3KeyIdName")
            return true
        } else {
            Timber.d("Failed to delete E3 key from OpeKeychain: $resultCode")
            return false
        }
    }

    fun requestKnownE3PublicKeys(): List<E3PublicKeyIdName> {
        val intent = Intent(OpenPgpApi.ACTION_GET_ENCRYPT_ON_RECEIPT_PUBLIC_KEYS)
        val keyIdsResult = openPgpApi.executeApi(intent, null as InputStream?, null)

        val eorKeyIds = keyIdsResult.getLongArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)
        val eorKeyNames = keyIdsResult.getStringArrayExtra(OpenPgpApi.EXTRA_NAMES)

        if (eorKeyIds.size != eorKeyNames.size) {
            return emptyList()
        }

        val e3KeyIdNames = mutableListOf<E3PublicKeyIdName>()

        var i = 0
        while (i < eorKeyIds.size) {
            // Skip this device's own key since it would also delete the private key
            e3KeyIdNames.add(E3PublicKeyIdName(eorKeyIds[i], eorKeyNames[i]))
            i++
        }

        return e3KeyIdNames
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

data class E3PublicKeyIdName(val keyId: Long, val keyName: String?)