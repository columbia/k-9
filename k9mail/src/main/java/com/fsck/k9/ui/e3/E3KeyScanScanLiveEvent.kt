package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.content.Intent
import com.fsck.k9.Account
import com.fsck.k9.helper.SingleLiveEvent
import com.fsck.k9.mail.Address
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi

class E3KeyScanScanLiveEvent() : SingleLiveEvent<E3KeyScanResult>() {

    fun scanRemoteE3KeysAsync(openPgpApi: OpenPgpApi, account: Account) {
        launch(UI) {
            val scanResult = bg {
                scanRemote(openPgpApi, account)
            }

            value = scanResult.await()
        }
    }

    private fun scanRemote(openPgpApi: OpenPgpApi, account: Account): E3KeyScanResult {
        val address = Address.parse(account.getIdentity(0).email)[0]


        return E3KeyScanResult(1)
    }

}

data class E3KeyScanResult(val test: Int)