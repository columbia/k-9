package com.fsck.k9.ui.e3.delete

import androidx.lifecycle.LifecycleOwner
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback
import org.openintents.openpgp.OpenPgpApiManager

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

        view.sceneBegin()
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }
}