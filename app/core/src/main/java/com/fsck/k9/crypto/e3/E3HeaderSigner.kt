package com.fsck.k9.crypto.e3

import android.content.Intent
import com.fsck.k9.mail.MessagingException
import org.openintents.openpgp.OpenPgpSignatureResult
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter

class E3HeaderSigner(private val openPgpApi: OpenPgpApi) {

    fun signE3Headers(keyId: Long, message: E3KeyEmail): String? {
        val dataSource = createOpenPgpDataSourceFromString(message.headersToSign)

        val intent = Intent(OpenPgpApi.ACTION_DETACHED_SIGN)
        intent.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, keyId)
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)

        val result = openPgpApi.executeApi(intent, dataSource, null)
        val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, -1)

        return when (resultCode) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                val signedData = result.getByteArrayExtra(OpenPgpApi.RESULT_DETACHED_SIGNATURE)
                        ?: throw MessagingException("didn't find expected RESULT_DETACHED_SIGNATURE in api call result")

                String(signedData)
            }
            else -> {
                Timber.e("Failed to sign E3 key upload headers, got OpenPgpApi result code=$resultCode")
                null
            }
        }
    }

    fun verifyE3Headers(message: E3KeyEmail): Boolean {
        // Rebuild the original signed data from headers
        val dataSource = createOpenPgpDataSourceFromString(message.headersToSign)

        // Get the actual signature
        val signatureData = message.headersSignature

        val intent = Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY)
        intent.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, signatureData)

        val result = openPgpApi.executeApi(intent, dataSource, null)
        val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, -1)

        return when (resultCode) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                val signatureResult = result.getParcelableExtra<OpenPgpSignatureResult>(OpenPgpApi.RESULT_SIGNATURE)

                handleOpenPgpSignatureResult(signatureResult)
            }
            else -> {
                Timber.e("Failed to verify E3 key upload headers, got OpenPgpApi result code=$resultCode")
                false
            }
        }
    }

    // TODO: E3 return more info other than just boolean
    private fun handleOpenPgpSignatureResult(signatureResult: OpenPgpSignatureResult): Boolean {
        return when (signatureResult.result) {
            OpenPgpSignatureResult.RESULT_VALID_KEY_CONFIRMED -> {
                true
            }
            OpenPgpSignatureResult.RESULT_VALID_KEY_UNCONFIRMED -> {
                Timber.w("Got signature with valid key but unconfirmed result")
                true
            } else -> {
                false
            }
        }
    }

    private fun createOpenPgpDataSourceFromString(str: String): OpenPgpApi.OpenPgpDataSource {
        return object : OpenPgpApi.OpenPgpDataSource() {
            @Throws(IOException::class)
            override fun writeTo(os: OutputStream) {
                try {
                    OutputStreamWriter(os, Charsets.UTF_8).write(str)
                } catch (e: MessagingException) {
                    throw IOException(e)
                }

            }
        }
    }
}
