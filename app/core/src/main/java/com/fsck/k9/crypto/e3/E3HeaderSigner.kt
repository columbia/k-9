package com.fsck.k9.crypto.e3

import android.content.Intent
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.internet.MimeMessage
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter

class E3HeaderSigner(private val openPgpApi: OpenPgpApi) {

    fun signE3Headers(keyId: Long, message: MimeMessage): String? {
        val sortedE3HeaderNames = message.headerNames.filter { name -> name.toUpperCase().startsWith(E3Constants.MIME_E3_PREFIX) }.toSortedSet()
        val concatHeadersBuilder = StringBuilder()

        for (header in sortedE3HeaderNames) {
            val values = message.getHeader(header)

            for (value in values) {
                concatHeadersBuilder.append(value)
            }
        }

        val dataSource = createOpenPgpDataSourceFromString(concatHeadersBuilder.toString())

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
