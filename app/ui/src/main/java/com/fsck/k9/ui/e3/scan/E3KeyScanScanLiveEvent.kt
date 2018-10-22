package com.fsck.k9.ui.e3.scan

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.SimpleMessagingListener
import com.fsck.k9.crypto.E3KeyScanResult
import com.fsck.k9.crypto.E3KeyScanner
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.search.LocalSearch
import com.fsck.k9.search.SearchSpecification.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import timber.log.Timber
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue

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