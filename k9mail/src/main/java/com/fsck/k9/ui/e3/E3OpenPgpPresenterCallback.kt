package com.fsck.k9.ui.e3

import org.openintents.openpgp.OpenPgpApiManager

class E3OpenPgpPresenterCallback(
        private val openPgpApiManager: OpenPgpApiManager,
        private val view: E3ActionBaseActivity) : OpenPgpApiManager.OpenPgpApiManagerCallback {

    override fun onOpenPgpProviderStatusChanged() {
        if (openPgpApiManager.openPgpProviderState == OpenPgpApiManager.OpenPgpProviderState.UI_REQUIRED) {
            view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
        }
    }

    override fun onOpenPgpProviderError(error: OpenPgpApiManager.OpenPgpProviderError) {
        view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
    }
}