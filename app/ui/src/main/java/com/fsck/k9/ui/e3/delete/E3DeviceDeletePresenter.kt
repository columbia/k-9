package com.fsck.k9.ui.e3.delete

import android.widget.AdapterView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback
import org.openintents.openpgp.OpenPgpApiManager
import timber.log.Timber

class E3DeviceDeletePresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val openPgpApiManager: OpenPgpApiManager,
        private val view: E3DeviceDeleteActivity,
        private val messagingController: MessagingController
) {
    private lateinit var account: Account

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)
        openPgpApiManager.setOpenPgpProvider(account.e3Provider, E3OpenPgpPresenterCallback(openPgpApiManager, view))

        view.addDevicesToListView(listOf("device 1", "device 2"), getDevicesListAdapterListener())

        view.sceneBegin()
    }

    private fun getDevicesListAdapterListener() : AdapterView.OnItemClickListener {
        return AdapterView.OnItemClickListener { _, textView, _, _ ->
            val selectedDevice: String = (textView as TextView).text.toString()

            Timber.d("User selected device $selectedDevice to delete")
        }
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }
}