package com.fsck.k9.ui.e3

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MenuItem
import android.view.View
import com.fsck.k9.R
import com.fsck.k9.view.StatusIndicator
import kotlinx.android.synthetic.main.crypto_e3_key_scan.*
import org.koin.android.ext.android.inject
import timber.log.Timber

class E3KeyScanActivity : E3ActionBaseActivity() {
    private val presenter: E3KeyScanPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3ScanView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_key_scan)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        e3KeyScanButton.setOnClickListener {
            var listener: E3KeyScanListener? = null
            val sharedPrefKey = "remote_search_enabled"
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
            val savedRemoteSearchEnabled = sharedPrefs.getBoolean(sharedPrefKey, false)

            if (e3KeyScanTempEnableRemoteSearchCheckbox.isChecked && !savedRemoteSearchEnabled) {
                val editor = sharedPrefs.edit()
                editor.putBoolean(sharedPrefKey, true)
                editor.commit()

                listener = E3KeyScanListener(editor, sharedPrefKey)
            }

            presenter.onClickScan(listener)
        }

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
        e3KeyScanAddress.text = address
    }

    fun sceneBegin() {
        e3KeyScanButton.visibility = View.VISIBLE
        e3KeyScanTempEnableRemoteSearchCheckbox.visibility = View.VISIBLE
        e3KeyScanMsgInfo.visibility = View.VISIBLE

        e3KeyScanLayoutScanning.visibility = View.GONE
        e3KeyScanLayoutDownloading.visibility = View.GONE
        e3KeyScanLayoutFinish.visibility = View.GONE
        e3KeyScanLayoutFinishNoMessages.visibility = View.GONE
        e3KeyScanCompletedInstructions.visibility = View.GONE
        e3KeyScanErrorUpload.visibility = View.GONE
    }

    fun sceneScanningAndDownloading() {
        setupSceneTransition()

        e3KeyScanButton.visibility = View.GONE
        e3KeyScanTempEnableRemoteSearchCheckbox.visibility = View.GONE
        e3KeyScanMsgInfo.visibility = View.GONE
        e3KeyScanLayoutScanning.visibility = View.VISIBLE
        e3KeyScanLayoutDownloading.visibility = View.VISIBLE
        e3KeyScanLayoutFinish.visibility = View.GONE
        e3KeyScanLayoutFinishNoMessages.visibility = View.GONE
        e3KeyScanCompletedInstructions.visibility = View.GONE
        e3KeyScanErrorUpload.visibility = View.GONE
    }

    fun setLoadingStateScanning() {
        e3KeyScanProgressScanning.setDisplayedChild(StatusIndicator.Status.PROGRESS)
        e3KeyScanProgressDownloading.setDisplayedChild(StatusIndicator.Status.IDLE)
    }

    fun setLoadingStateDownloading() {
        e3KeyScanProgressScanning.setDisplayedChild(StatusIndicator.Status.OK)
        e3KeyScanProgressDownloading.setDisplayedChild(StatusIndicator.Status.PROGRESS)
    }

    fun setLoadingStateFinished() {
        e3KeyScanProgressScanning.setDisplayedChild(StatusIndicator.Status.OK)
        e3KeyScanProgressDownloading.setDisplayedChild(StatusIndicator.Status.OK)
    }

    fun sceneFinished(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3KeyScanButton.visibility = View.GONE
        e3KeyScanTempEnableRemoteSearchCheckbox.visibility = View.GONE
        e3KeyScanMsgInfo.visibility = View.GONE
        e3KeyScanLayoutScanning.visibility = View.VISIBLE
        e3KeyScanLayoutDownloading.visibility = View.VISIBLE
        e3KeyScanLayoutFinish.visibility = View.VISIBLE
        e3KeyScanLayoutFinishNoMessages.visibility = View.GONE
        e3KeyScanCompletedInstructions.visibility = View.VISIBLE
        e3KeyScanErrorUpload.visibility = View.GONE
    }

    fun sceneFinishedNoMessages(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3KeyScanButton.visibility = View.GONE
        e3KeyScanTempEnableRemoteSearchCheckbox.visibility = View.GONE
        e3KeyScanMsgInfo.visibility = View.GONE
        e3KeyScanLayoutScanning.visibility = View.VISIBLE
        e3KeyScanLayoutDownloading.visibility = View.VISIBLE
        e3KeyScanLayoutFinish.visibility = View.GONE
        e3KeyScanLayoutFinishNoMessages.visibility = View.VISIBLE
        e3KeyScanCompletedInstructions.visibility = View.GONE
        e3KeyScanErrorUpload.visibility = View.GONE
    }

    fun setLoadingStateSendingFailed() {
        e3KeyScanProgressScanning.setDisplayedChild(StatusIndicator.Status.OK)
        e3KeyScanProgressDownloading.setDisplayedChild(StatusIndicator.Status.ERROR)
    }

    fun sceneSendError() {
        setupSceneTransition()

        e3KeyScanButton.visibility = View.GONE
        e3KeyScanTempEnableRemoteSearchCheckbox.visibility = View.GONE
        e3KeyScanMsgInfo.visibility = View.GONE
        e3KeyScanLayoutScanning.visibility = View.VISIBLE
        e3KeyScanLayoutDownloading.visibility = View.VISIBLE
        e3KeyScanLayoutFinish.visibility = View.GONE
        e3KeyScanLayoutFinishNoMessages.visibility = View.GONE
        e3KeyScanCompletedInstructions.visibility = View.GONE
        e3KeyScanErrorUpload.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"

        fun createIntent(context: Context, accountUuid: String): Intent {
            val intent = Intent(context, E3KeyScanActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            return intent
        }
    }
}

class E3KeyScanListener(private val sharedPrefsEditor: SharedPreferences.Editor, private val key: String) {
    fun keySearchFinished() {
        Timber.d("Resetting remote search shared preference to false")
        sharedPrefsEditor.putBoolean(key, false)
        sharedPrefsEditor.commit()
    }
}