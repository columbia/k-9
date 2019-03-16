package com.fsck.k9.crypto.e3

import android.content.Intent
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.InputStream

class E3PublicKeyManager(private val openPgpApi: OpenPgpApi) {

    fun addPublicKeysFromKeyEmail(keyEmail: E3KeyEmail) {
        for (keyBytes in keyEmail.publicKeys) {
            val pgpApiIntent = Intent(OpenPgpApi.ACTION_ADD_ENCRYPT_ON_RECEIPT_KEY)
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_ASCII_ARMORED_KEY, keyBytes)

            val result = openPgpApi.executeApi(pgpApiIntent, null as InputStream?, null)
            val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

            if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
                Timber.d("addPublicKeysFromKeyEmail(): Successfully added E3 public key to OpenKeychain")
            } else {
                Timber.d("addPublicKeysFromKeyEmail(): Failed to add E3 public key to OpeKeychain: $resultCode")
            }
        }
    }

    fun deletePublicKeysFromKeyEmail(keyEmail: E3KeyEmail) {
        throw NotImplementedError("not implemented yet")
    }
}