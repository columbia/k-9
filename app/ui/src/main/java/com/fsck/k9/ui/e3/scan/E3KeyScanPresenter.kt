package com.fsck.k9.ui.e3.scan

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.crypto.e3.E3KeyScanResult
import com.fsck.k9.mailstore.LocalMessage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

class E3KeyScanPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val viewModel: E3KeyScanViewModel,
        private val view: E3KeyScanActivity
) {
    private lateinit var account: Account

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

        view.setAddress(account.identities[0].email)

        viewModel.e3KeyScanDownloadLiveEvent.recall()
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }

    fun onClickScan(tempEnableRemoteSearch: Boolean) {
        view.sceneScanningAndDownloading()

        GlobalScope.launch(Dispatchers.Main) {
            view.uxDelay()
            view.setLoadingStateScanning()

            viewModel.e3KeyScanScanLiveEvent.scanRemoteE3KeysAsync(account, tempEnableRemoteSearch)
        }
    }

    private fun onEventE3KeyScan(e3KeyScanResult: E3KeyScanResult) {
        view.setLoadingStateDownloading()
        view.sceneScanningAndDownloading()

        viewModel.e3KeyScanDownloadLiveEvent.downloadE3KeysAsync(e3KeyScanResult)
    }

    fun verifyKeys(keyMessages: List<LocalMessage>) {
        val uidsToPhrases = hashMapOf<String, String>()

        for (keyMsg in keyMessages) {
            uidsToPhrases[keyMsg.uid] = keyMsg.getHeader(E3Constants.MIME_E3_VERIFICATION)[0]
        }

        view.startVerifyKeyActivity(account.uuid, uidsToPhrases)
    }

    private fun onLoadedE3KeyScanDownload(result: E3KeyScanDownloadResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3KeyScanDownloadResult.Success -> {
                view.setLoadingStateFinished()
                view.sceneFinished()
                Timber.d("Got downloaded key results ${result.resultMessages.size}")
                //addKeysFromMessagesToKeychain(result.resultMessages)

                val keyMessages = mutableListOf<LocalMessage>()

                for (keyMsg in result.resultMessages) {
                    if (keyMsg.headerNames.contains(E3Constants.MIME_E3_VERIFICATION)) {
                        keyMessages.add(keyMsg)
                    }
                }

                verifyKeys(keyMessages)
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