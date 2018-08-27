package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.crypto.E3Constants
import com.fsck.k9.mail.internet.MimeUtility
import com.fsck.k9.mailstore.LocalMessage
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.openintents.openpgp.OpenPgpApiManager
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream

class E3KeyScanPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val context: Context,
        private val openPgpApiManager: OpenPgpApiManager,
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

    fun onClickScan(keyScanListener: E3KeyScanListener?) {
        view.sceneScanningAndDownloading()

        launch(UI) {
            view.uxDelay()
            view.setLoadingStateScanning()

            viewModel.e3KeyScanScanLiveEvent.scanRemoteE3KeysAsync(account, keyScanListener)
        }
    }

    private fun onEventE3KeyScan(e3KeyScanResult: E3KeyScanResult) {
        view.setLoadingStateDownloading()
        view.sceneScanningAndDownloading()

        //val transport = transportProvider.getTransport(context, account)
        viewModel.e3KeyScanDownloadLiveEvent.downloadE3KeysAsync(e3KeyScanResult)
    }

    private fun addKeysFromMessagesToKeychain(keyMessages: List<LocalMessage>) {
        for (keyMsg: LocalMessage in keyMessages) {
            if (!keyMsg.hasAttachments()) {
                continue
            }

            val keyPart = MimeUtility.findFirstPartByMimeType(keyMsg, E3Constants.CONTENT_TYPE_PGP_KEYS)

            if (keyPart == null) {
                Timber.e("Did not find any ${E3Constants.CONTENT_TYPE_PGP_KEYS} attachment in E3 key message: ${keyMsg.messageId} ${keyMsg.preview}")
                continue
            }

            val keyBytes = ByteArrayOutputStream()
            keyPart.body.writeTo(keyBytes)

            val pgpApiIntent = Intent(OpenPgpApi.ACTION_ADD_ENCRYPT_ON_RECEIPT_KEY)
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_ASCII_ARMORED_KEY, keyBytes.toByteArray())

            val result = openPgpApiManager.openPgpApi.executeApi(pgpApiIntent, null as InputStream?, null)
            val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

            if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
                Timber.d("Successfully added E3 public key to OpenKeychain")
            } else {
                Timber.d("Failed to add E3 public key to OpeKeychain: $resultCode")
            }
        }
    }

    private fun onLoadedE3KeyScanDownload(result: E3KeyScanDownloadResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3KeyScanDownloadResult.Success -> {
                view.setLoadingStateFinished()
                view.sceneFinished()
                Timber.d("Got downloaded key results ${result.resultMessages.size}")
                addKeysFromMessagesToKeychain(result.resultMessages)
            }
            is E3KeyScanDownloadResult.NoneFound -> {
                view.setLoadingStateFinished()
                view.sceneFinishedNoMessages()
            }
            is E3KeyScanDownloadResult.Failure -> {
                Timber.e(result.exception, "Error downloading E3 public key")
                view.setLoadingStateSendingFailed()
                view.sceneSendError()
            }
        }
    }
}