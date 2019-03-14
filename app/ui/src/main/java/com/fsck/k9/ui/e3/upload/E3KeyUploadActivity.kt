package com.fsck.k9.ui.e3.upload

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import com.fsck.k9.activity.Accounts
import com.fsck.k9.ui.R
import com.fsck.k9.ui.e3.E3ActionBaseActivity
import com.fsck.k9.view.StatusIndicator
import kotlinx.android.synthetic.main.crypto_e3_key_upload.*
import kotlinx.android.synthetic.main.text_list_item.view.*
import kotlinx.android.synthetic.main.wizard_cancel_done.*
import org.koin.android.ext.android.inject

class E3KeyUploadActivity : E3ActionBaseActivity(), View.OnClickListener {
    private val presenter: E3KeyUploadPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3UploadView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_key_upload)

        isInitialSetup = intent.getBooleanExtra(EXTRA_IS_SETUP, false)
        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        e3KeyUploadButton.setOnClickListener { presenter.onClickUpload() }

        findViewById<View>(R.id.cancel).setOnClickListener(this)
        findViewById<View>(R.id.done).setOnClickListener(this)

        presenter.initFromIntent(accountUuid)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            presenter.onClickHome()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun setAddress(address: String) {
        e3KeyUploadAddress.text = address
        e3KeyUploadAddress2.text = address
    }

    fun setQrcode(qrBitmap: Bitmap?) {
        if (qrBitmap != null) {
            e3KeyQrCode.setImageBitmap(qrBitmap)
        }
    }

    fun setVerification(verificationPhrase: String) {
        e3KeyVerificationPhrase.text = verificationPhrase
    }

    fun sceneBegin() {
        e3KeyUploadHeader.visibility = View.VISIBLE
        e3KeyUploadButton.visibility = View.VISIBLE
        e3KeyUploadMsgInfo.visibility = View.VISIBLE

        e3KeyUploadLayoutGenerating.visibility = View.GONE
        e3KeyUploadLayoutUploading.visibility = View.GONE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadLayoutVerification.visibility = View.GONE
        e3KeyUploadLayoutVerificationPhrase.visibility = View.GONE
        e3KeyUploadLayoutVerificationInstructions.visibility = View.GONE

        e3KeyUploadAdvancedOptions.visibility = View.GONE
        e3KeyUploadLayoutQrCodeInstructions.visibility = View.GONE
        e3KeyUploadLayoutQrCode.visibility = View.GONE

        e3KeyUploadErrorUpload.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneGeneratingAndUploading() {
        setupSceneTransition()

        e3KeyUploadHeader.visibility = View.GONE
        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.VISIBLE
        e3KeyUploadLayoutUploading.visibility = View.VISIBLE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadLayoutVerification.visibility = View.GONE
        e3KeyUploadLayoutVerificationPhrase.visibility = View.GONE
        e3KeyUploadLayoutVerificationInstructions.visibility = View.GONE

        e3KeyUploadAdvancedOptions.visibility = View.GONE
        e3KeyUploadLayoutQrCodeInstructions.visibility = View.GONE
        e3KeyUploadLayoutQrCode.visibility = View.GONE

        e3KeyUploadErrorUpload.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneSendError() {
        setupSceneTransition()

        e3KeyUploadHeader.visibility = View.GONE
        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.VISIBLE
        e3KeyUploadLayoutUploading.visibility = View.VISIBLE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadLayoutVerification.visibility = View.GONE
        e3KeyUploadLayoutVerificationInstructions.visibility = View.GONE
        e3KeyUploadLayoutVerificationPhrase.visibility = View.GONE

        e3KeyUploadAdvancedOptions.visibility = View.GONE
        e3KeyUploadLayoutQrCodeInstructions.visibility = View.GONE
        e3KeyUploadLayoutQrCode.visibility = View.GONE

        e3KeyUploadErrorUpload.visibility = View.VISIBLE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneFinished(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3KeyUploadHeader.visibility = View.GONE
        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.GONE
        e3KeyUploadLayoutUploading.visibility = View.GONE
        e3KeyUploadLayoutFinish.visibility = View.VISIBLE

        e3KeyUploadErrorUpload.visibility = View.GONE

        e3KeyUploadLayoutVerification.visibility = View.VISIBLE
        e3KeyUploadLayoutVerificationPhrase.visibility = View.VISIBLE
        e3KeyUploadLayoutVerificationInstructions.visibility = View.VISIBLE

        e3KeyUploadAdvancedOptions.visibility = View.VISIBLE
        e3KeyUploadLayoutQrCodeInstructions.visibility = View.VISIBLE
        e3KeyUploadLayoutQrCode.visibility = View.VISIBLE

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    fun sceneCancelled() {
        setupSceneTransition()

        e3KeyUploadHeader.visibility = View.VISIBLE
        e3KeyUploadButton.visibility = View.GONE
        e3KeyUploadMsgInfo.visibility = View.GONE
        e3KeyUploadLayoutGenerating.visibility = View.GONE
        e3KeyUploadLayoutUploading.visibility = View.GONE
        e3KeyUploadLayoutFinish.visibility = View.GONE
        e3KeyUploadLayoutVerification.visibility = View.GONE
        e3KeyUploadLayoutVerificationInstructions.visibility = View.GONE
        e3KeyUploadLayoutVerificationPhrase.visibility = View.GONE

        e3KeyUploadAdvancedOptions.visibility = View.GONE
        e3KeyUploadLayoutQrCodeInstructions.visibility = View.GONE
        e3KeyUploadLayoutQrCode.visibility = View.GONE

        e3KeyUploadErrorUpload.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
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

    private fun setMessage(resId: Int) {
        findViewById<TextView>(R.id.e3KeyUploadHeader).text = getString(resId)
        sceneCancelled()
    }

    private fun onCancel() {
        //mCanceled = true
        setMessage(R.string.e3_key_upload_cancelled)
        setResult(Activity.RESULT_CANCELED)

        if (isInitialSetup) {
            Accounts.listAccounts(this)
        }

        finish()
    }

    override fun onClick(v: View) {
        if (v.id == R.id.cancel) {
            onCancel()
        } else if (v.id == R.id.done) {
            if (isInitialSetup) {
                Accounts.listAccounts(this)
            }

            finish()
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_IS_SETUP = "is_setup"
        private var isInitialSetup: Boolean = false

        @JvmStatic
        fun createIntent(context: Context, accountUuid: String, isInitialSetup: Boolean): Intent {
            val intent = Intent(context, E3KeyUploadActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            intent.putExtra(EXTRA_IS_SETUP, isInitialSetup)
            return intent
        }
    }
}