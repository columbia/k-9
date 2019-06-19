package com.fsck.k9.ui.e3.delete

import com.fsck.k9.Account
import com.fsck.k9.crypto.e3.E3PublicKeyIdName
import com.fsck.k9.helper.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.util.OpenPgpApi

class E3DeviceDeleteCreateLiveEvent(private val deleteMessageCreator: E3DeleteMessageCreator) : SingleLiveEvent<E3DeleteMessage>() {

    fun createE3DeleteMessageAsync(openPgpApi: OpenPgpApi, account: Account, e3DeviceIdNames: Set<E3PublicKeyIdName>) {
        GlobalScope.launch(Dispatchers.Main) {
            val setupMessage = bg {
                deleteMessageCreator.createE3DeleteMessage(openPgpApi, account, e3DeviceIdNames)
            }

            value = setupMessage.await()
        }
    }
}