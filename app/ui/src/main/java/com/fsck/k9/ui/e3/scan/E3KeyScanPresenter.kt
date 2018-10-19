package com.fsck.k9.ui.e3.scan

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.Observer
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.crypto.E3Constants
import com.fsck.k9.mailstore.LocalMessage
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class E3KeyScanPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val viewModel: E3KeyScanViewModel,
        private val view: E3KeyScanActivity
) {
    private lateinit var account: Account
    private lateinit var pendingKeysToVerify: Queue<LocalMessage>

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
        pendingKeysToVerify = ConcurrentLinkedQueue<LocalMessage>()

        view.setAddress(account.identities[0].email)

        viewModel.e3KeyScanDownloadLiveEvent.recall()
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }

    fun onClickScan(tempEnableRemoteSearch: Boolean) {
        view.sceneScanningAndDownloading()

        launch(UI) {
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

    fun verifyNextKey() {
        if (anyKeysToVerify()) {
            val keyToVerify = pendingKeysToVerify.poll()
            val uid = keyToVerify.uid
            val phrase = keyToVerify.getHeader(E3Constants.MIME_E3_VERIFICATION)[0]

            view.startVerifyKeyActivity(account.uuid, uid, phrase)
        }
    }

    fun anyKeysToVerify(): Boolean {
        return pendingKeysToVerify.isNotEmpty()
    }

    private fun onLoadedE3KeyScanDownload(result: E3KeyScanDownloadResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3KeyScanDownloadResult.Success -> {
                view.setLoadingStateFinished()
                view.sceneFinished()
                Timber.d("Got downloaded key results ${result.resultMessages.size}")
                //addKeysFromMessagesToKeychain(result.resultMessages)

                for (keyMsg in result.resultMessages) {
                    if (keyMsg.headerNames.contains(E3Constants.MIME_E3_VERIFICATION)) {
                        pendingKeysToVerify.add(keyMsg)
                    }
                }

                verifyNextKey()
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