package com.fsck.k9.ui.e3.scan

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.crypto.e3.E3KeyScanResult
import com.fsck.k9.crypto.e3.E3KeyScanner
import com.fsck.k9.helper.SingleLiveEvent
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg

class E3KeyScanScanLiveEvent(private val context: Context) : SingleLiveEvent<E3KeyScanResult>() {

    fun scanRemoteE3KeysAsync(account: Account, tempEnableRemoteSearch: Boolean) {
        val scanner = E3KeyScanner(context)
        launch(UI) {
            val scanResult = bg {
                scanner.scanRemote(account, tempEnableRemoteSearch)
            }

            value = scanResult.await()
        }
    }
}