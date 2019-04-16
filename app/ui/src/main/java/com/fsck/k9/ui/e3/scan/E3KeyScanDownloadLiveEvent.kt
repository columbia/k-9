package com.fsck.k9.ui.e3.scan

import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.crypto.e3.E3KeyScanResult
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mailstore.LocalMessage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg
import timber.log.Timber

class E3KeyScanDownloadLiveEvent : SingleLiveEvent<E3KeyScanDownloadResult>() {

    private val fetchProfile = FetchProfile()

    init {
        fetchProfile.add(FetchProfile.Item.ENVELOPE)
        fetchProfile.add(FetchProfile.Item.BODY)
    }

    fun downloadE3KeysAsync(e3KeyScanResult: E3KeyScanResult) {
        GlobalScope.launch(Dispatchers.Main) {
            val scanResult = bg {
                downloadRemote(e3KeyScanResult)
            }

            value = try {
                val filteredResult = scanResult.await()
                if (!filteredResult.isEmpty()) {
                    E3KeyScanDownloadResult.Success(filteredResult)
                } else {
                    E3KeyScanDownloadResult.NoneFound("no e3 keys were found")
                }
            } catch (e: Exception) {
                E3KeyScanDownloadResult.Failure(e)
            }
        }
    }

    private fun downloadRemote(e3KeyScanResult: E3KeyScanResult): List<LocalMessage> {
        val filtered = mutableListOf<LocalMessage>()

        for (msg: LocalMessage in e3KeyScanResult.results) {
            if (!msg.headerNames.contains(E3Constants.MIME_E3_NAME)) {
                continue
            }

            val subj = msg.subject
            val keyName = msg.getHeader(E3Constants.MIME_E3_NAME)[0]

            Timber.d("Got E3 key (subj=$subj, key_name=$keyName)")

            if (!msg.hasAttachments()) {
                Timber.w("E3 key email had no attachments (uid=${msg.uid}, subj=$subj, key_name=$keyName)")
                continue
            }

            val localMessageList = listOf(msg)

            msg.folder.fetch(localMessageList, fetchProfile, null)

            filtered.add(msg)
        }

        Timber.i("Finished processing scanned keys")

        return filtered
    }

}

sealed class E3KeyScanDownloadResult {
    data class Success(val resultMessages: List<LocalMessage>) : E3KeyScanDownloadResult()
    data class Failure(val exception: Exception) : E3KeyScanDownloadResult()
    data class NoneFound(val msg: String) : E3KeyScanDownloadResult()
}