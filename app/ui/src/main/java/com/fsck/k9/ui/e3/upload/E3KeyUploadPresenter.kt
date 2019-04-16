package com.fsck.k9.ui.e3.upload

import android.app.PendingIntent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openintents.openpgp.OpenPgpApiManager
import timber.log.Timber

class E3KeyUploadPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val context: Context,
        private val openPgpApiManager: OpenPgpApiManager,
        private val preferences: Preferences,
        private val viewModel: E3KeyUploadViewModel,
        private val view: E3KeyUploadActivity
) {

    private lateinit var account: Account
    private lateinit var pendingIntentForGetKey: PendingIntent

    init {
        viewModel.e3KeyUploadSetupMessageLiveEvent.observe(lifecycleOwner, Observer { msg -> msg?.let { onEventE3KeyUploadSetupMessage(it) } })

        viewModel.e3KeyUploadMessageUploadLiveEvent.observe(lifecycleOwner, Observer { pi -> onLoadedE3KeyUploadMessageUpload(pi) })
    }

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)

        openPgpApiManager.setOpenPgpProvider(account.e3Provider, E3OpenPgpPresenterCallback(openPgpApiManager, view))

        view.setAddress(account.identities[0].email)

        viewModel.e3KeyUploadMessageUploadLiveEvent.recall()
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }

    fun onClickUpload() {
        view.sceneGeneratingAndUploading()

        GlobalScope.launch(Dispatchers.Main) {
            view.uxDelay()
            view.setLoadingStateGenerating()

            viewModel.e3KeyUploadSetupMessageLiveEvent.loadE3KeyUploadMessageAsync(openPgpApiManager.openPgpApi, account)
        }
    }

    private fun onEventE3KeyUploadSetupMessage(setupMsg: E3KeyUploadMessage) {
        view.setLoadingStateSending()
        view.sceneGeneratingAndUploading()

        viewModel.e3KeyUploadMessageUploadLiveEvent.sendMessageAsync(account, setupMsg)
    }

    private fun onLoadedE3KeyUploadMessageUpload(result: E3KeyUploadMessageUploadResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3KeyUploadMessageUploadResult.Success -> {
                pendingIntentForGetKey = result.pendingIntentForGetKey
                view.setLoadingStateFinished()
                view.sceneFinished()

                val verificationPhrase = result.sentMessage.verificationPhrase
                account.e3KeyVerificationPhrase = verificationPhrase
                view.setVerification(verificationPhrase)

                val keyFingerprint = result.sentMessage.keyResult.fingerprint

                view.setQrcode(keyFingerprint.qrBitmap)
            }
            is E3KeyUploadMessageUploadResult.Failure -> {
                Timber.e(result.exception, "Error uploading E3 key")
                view.setLoadingStateSendingFailed()
                view.sceneSendError()
            }
        }
    }
}