package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.transition.TransitionInflater
import android.transition.TransitionManager
import android.view.MenuItem
import android.view.View
import com.fsck.k9.R
import com.fsck.k9.activity.K9Activity
import com.fsck.k9.finishWithErrorToast
import com.fsck.k9.view.StatusIndicator
import kotlinx.android.synthetic.main.crypto_e3_key_upload.*
import kotlinx.coroutines.experimental.delay
import org.koin.android.ext.android.inject
import timber.log.Timber

class E3KeyUploadActivity : K9Activity() {
    private val presenter: E3KeyUploadPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3UploadView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_key_upload)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        e3KeyUploadButton.setOnClickListener { presenter.onClickUpload() }

        presenter.initFromIntent(accountUuid)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            presenter.onClickHome()
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    fun setAddress(address: String) {
        e3KeyUploadAddress.text = address
        e3KeyUploadAddress2.text = address
    }

    fun sceneBegin() {
        e3KeyUploadButton.visibility = View.VISIBLE
        e3KeyUploadMsgInfo.visibility = View.VISIBLE

        e3KeyUploadLayoutGenerating.visibility = View.GONE
        e3KeyUploadLayoutUploading.visibility = View.GONE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadErrorUpload.visibility = View.GONE
    }

    fun sceneGeneratingAndUploading() {
        setupSceneTransition()

        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.VISIBLE
        e3KeyUploadLayoutUploading.visibility = View.VISIBLE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadErrorUpload.visibility = View.GONE
    }

    fun sceneSendError() {
        setupSceneTransition()

        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.VISIBLE
        e3KeyUploadLayoutUploading.visibility = View.VISIBLE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadErrorUpload.visibility = View.VISIBLE
    }

    fun sceneFinished(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.VISIBLE
        e3KeyUploadLayoutUploading.visibility = View.VISIBLE
        e3KeyUploadLayoutFinish.visibility = View.VISIBLE
        e3KeyUploadErrorUpload.visibility = View.GONE
    }

    fun setLoadingStateGenerating() {
        e3KeyUploadProgressGenerating.setDisplayedChild(StatusIndicator.Status.PROGRESS)
        e3KeyUploadProgressUploading.setDisplayedChild(StatusIndicator.Status.IDLE)
    }

    fun setLoadingStateSending() {
        e3KeyUploadProgressGenerating.setDisplayedChild(StatusIndicator.Status.OK)
        e3KeyUploadProgressUploading.setDisplayedChild(StatusIndicator.Status.PROGRESS)
    }

    fun setLoadingStateSendingFailed() {
        e3KeyUploadProgressGenerating.setDisplayedChild(StatusIndicator.Status.OK)
        e3KeyUploadProgressUploading.setDisplayedChild(StatusIndicator.Status.ERROR)
    }

    fun setLoadingStateFinished() {
        e3KeyUploadProgressGenerating.setDisplayedChild(StatusIndicator.Status.OK)
        e3KeyUploadProgressUploading.setDisplayedChild(StatusIndicator.Status.OK)
    }

    fun finishWithInvalidAccountError() {
        finishWithErrorToast(R.string.toast_account_not_found)
    }

    fun finishWithProviderConnectError(providerName: String) {
        finishWithErrorToast(R.string.toast_openpgp_provider_error, providerName)
    }

    fun launchUserInteractionPendingIntent(pendingIntent: PendingIntent) {
        try {
            startIntentSender(pendingIntent.intentSender, null, 0, 0, 0)
        } catch (e: IntentSender.SendIntentException) {
            Timber.e(e)
        }
    }

    private fun setupSceneTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val transition = TransitionInflater.from(this).inflateTransition(R.transition.transfer_transitions)
            TransitionManager.beginDelayedTransition(findViewById(android.R.id.content), transition)
        }
    }

    fun finishAsCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    suspend fun uxDelay() {
        // called before logic resumes upon screen transitions, to give some breathing room
        delay(UX_DELAY_MS)
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val UX_DELAY_MS = 1200L

        fun createIntent(context: Context, accountUuid: String): Intent {
            val intent = Intent(context, E3KeyUploadActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            return intent
        }
    }
}