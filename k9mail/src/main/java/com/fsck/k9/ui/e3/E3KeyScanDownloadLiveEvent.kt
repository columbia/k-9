package com.fsck.k9.ui.e3

import android.app.PendingIntent
import com.fsck.k9.Account
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Transport
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi

class E3KeyScanDownloadLiveEvent() : SingleLiveEvent<E3KeyScanDownloadResult>() {

    fun downloadE3KeysAsync(transport: Transport, messagesToDownload: E3KeyScanResult) {
        launch(UI) {
            val scanResult = bg {
                downloadRemote(transport, messagesToDownload)
            }

            try {
                scanResult.await()
                value = E3KeyScanDownloadResult.Success(messagesToDownload.test)
            } catch (e: Exception) {
                value = E3KeyScanDownloadResult.Failure(e)
            }
        }
    }

    private fun downloadRemote(transport: Transport, messagesToDownload: E3KeyScanResult) {
        //val address = Address.parse(account.getIdentity(0).email)[0]
    }

}

sealed class E3KeyScanDownloadResult {
    data class Success(val test: Int) : E3KeyScanDownloadResult()
    data class Failure(val exception: Exception) : E3KeyScanDownloadResult()
}