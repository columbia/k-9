package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.Observer
import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.mail.TransportProvider
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.openintents.openpgp.OpenPgpApiManager
import timber.log.Timber

class E3KeyScanPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val context: Context,
        private val openPgpApiManager: OpenPgpApiManager,
        private val transportProvider: TransportProvider,
        private val preferences: Preferences,
        private val viewModel: E3KeyScanViewModel,
        private val view: E3KeyScanActivity
) {
    private lateinit var account: Account
    private lateinit var pendingIntentForGetKey: PendingIntent

    init {
        viewModel.e3KeyScanScanLiveEvent.observe(lifecycleOwner, Observer { msg -> msg?.let { onEventE3KeyScan(it) } })
        viewModel.e3KeyScanDownloadLiveEvent.observe(lifecycleOwner, Observer { pi -> onLoadedE3KeyScanDownload(pi) })
    }

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)

        openPgpApiManager.setOpenPgpProvider(account.e3Provider, object : OpenPgpApiManager.OpenPgpApiManagerCallback {
            override fun onOpenPgpProviderStatusChanged() {
                if (openPgpApiManager.openPgpProviderState == OpenPgpApiManager.OpenPgpProviderState.UI_REQUIRED) {
                    view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
                }
            }

            override fun onOpenPgpProviderError(error: OpenPgpApiManager.OpenPgpProviderError) {
                view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
            }
        })

        view.setAddress(account.identities[0].email)

        viewModel.e3KeyScanDownloadLiveEvent.recall()
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }

    fun onClickScan() {
        view.sceneScanningAndDownloading()

        launch(UI) {
            view.uxDelay()
            view.setLoadingStateScanning()

            viewModel.e3KeyScanScanLiveEvent.scanRemoteE3KeysAsync(openPgpApiManager.openPgpApi, account)
        }
    }

    private fun onEventE3KeyScan(E3KeyScanResult: E3KeyScanResult) {
        view.setLoadingStateDownloading()
        view.sceneScanningAndDownloading()

        val transport = transportProvider.getTransport(context, account)
        viewModel.e3KeyScanDownloadLiveEvent.downloadE3KeysAsync(transport, E3KeyScanResult)
    }

    private fun onLoadedE3KeyScanDownload(result: E3KeyScanDownloadResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3KeyScanDownloadResult.Success -> {
                //pendingIntentForGetKey = result.pendingIntentForGetKey
                view.setLoadingStateFinished()
                view.sceneFinished()
            }
            is E3KeyScanDownloadResult.Failure -> {
                Timber.e(result.exception, "Error downloading E3 public key")
                view.setLoadingStateSendingFailed()
                view.sceneSendError()
            }
        }
    }
}