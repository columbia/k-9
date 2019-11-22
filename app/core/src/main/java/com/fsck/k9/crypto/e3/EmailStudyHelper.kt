package com.fsck.k9.crypto.e3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.Exception
import java.net.URL

class EmailStudyHelper {

    fun apiGetRecordEncryptAsync(hostname: String, email: String, emailToken: String) {
        GlobalScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                val result = apiGetRecordEncrypt(hostname, email, emailToken)
                Timber.i("RECORD_ENCRYPT api response for email token $emailToken: $result")
            }
        }
    }

    fun apiGetRecordEncrypt(hostname: String, email: String, emailToken: String): String {
        val fullUrl = API_RECORD_ENCRYPT.format(hostname, email, emailToken)
        return try {
            URL(fullUrl).readText()
        } catch (e: Exception) {
            Timber.e(e, "Got error while trying to API_RECORD_ENCRYPT to: $hostname")
            fullUrl
        }
    }

    companion object {
        private const val API_RECORD_ENCRYPT = "%s/api/1.0/record_encrypt/%s/%s"
    }
}