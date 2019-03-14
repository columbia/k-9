package com.fsck.k9.activity.setup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.activity.Accounts
import com.fsck.k9.activity.K9Activity
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.ui.R
import com.fsck.k9.ui.e3.upload.E3KeyUploadActivity
import com.fsck.k9.ui.settings.account.AccountSettingsFragment
import com.fsck.k9.ui.settings.account.OpenPgpAppSelectDialog
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpProviderUtil
import org.openintents.openpgp.util.OpenPgpServiceConnection
import timber.log.Timber


class AccountSetupE3 : K9Activity(), View.OnClickListener,
        ConfirmationDialogFragment.ConfirmationDialogFragmentListener {
    private var mMessageView: TextView? = null
    private var mProgressBar: ProgressBar? = null

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val RESULT_CODE_PENDING_INTENT = 9990
        private var mAccount: Account? = null
        private var mCanceled: Boolean = false
        private var pendingIntent: PendingIntent? = null

        @JvmStatic
        fun actionSetupE3(context: Context, account: Account) {
            val i = Intent(context, AccountSetupE3::class.java)
            i.putExtra(EXTRA_ACCOUNT, account.uuid)
            context.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_setup_e3)
        mMessageView = findViewById(R.id.message)
        mProgressBar = findViewById<ProgressBar>(R.id.progress)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid)

        // Choose crypto/key provider (aka choose OpenKeychain, for example)
        val openPgpProviderPackages = OpenPgpProviderUtil.getOpenPgpProviderPackages(this)
        if (openPgpProviderPackages.size == 1) {
            mAccount!!.e3Provider = openPgpProviderPackages[0]
            enableE3Preference()
        } else {
            OpenPgpAppSelectDialog.startOpenPgpChooserActivity(this, mAccount, OpenPgpAppSelectDialog.ProviderType.E3)
        }

        // Generate a key if one doesn't exist already, then the callback will upload it
        generateE3Key(openPgpCreateKeyCallback)
        return
    }

    override fun onResume() {
        super.onResume()
        // Resume key generation after user allows access to openkeychain?
        if (mAccount!!.e3Key == Account.NO_OPENPGP_KEY && pendingIntent != null) {
            // Didn't generate key yet, so restart it
            Timber.d("Resumed AccountSetupE3 without an E3 key generated yet, and potentially resolved a pending intent")
            generateE3Key(openPgpCreateKeyCallback)
        }
    }

    private fun generateE3Key(callback: OpenPgpApi.IOpenPgpCallback) {
        val data = Intent()
        data.action = OpenPgpApi.ACTION_CREATE_ENCRYPT_ON_RECEIPT_KEY
        data.putExtra(OpenPgpApi.EXTRA_NAME, "test key name")
        data.putExtra(OpenPgpApi.EXTRA_EMAIL, mAccount!!.email)
        //data.putExtra(OpenPgpApi.EXTRA_CREATE_SECURITY_TOKEN, false)

        val serviceConnection = OpenPgpServiceConnection(applicationContext, mAccount!!.e3Provider, object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: IOpenPgpService2) {
                val openPgpApi = OpenPgpApi(applicationContext, service)
                openPgpApi.executeApiAsync(data, null, null, callback)
            }

            override fun onError(e: Exception) {
                Timber.e("Got error while binding to OpenPGP service", e)
            }
        })

        serviceConnection.bindToService()
    }

    // Runs after OpenPgpApi succeeds in generating the key
    private val openPgpCreateKeyCallback = OpenPgpApi.IOpenPgpCallback { result ->
        val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)
        when (resultCode) {
            OpenPgpApi.RESULT_CODE_SUCCESS, OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                pendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)

                if (result.hasExtra(OpenPgpApi.EXTRA_KEY_ID)) {
                    val keyId = result.getLongExtra(OpenPgpApi.EXTRA_KEY_ID, Account.NO_OPENPGP_KEY)
                    pendingIntent = null

                    if (keyId != Account.NO_OPENPGP_KEY) {
                        setE3KeyPreference(keyId)

                        // Upload the key
                        val intent = E3KeyUploadActivity.createIntent(this, mAccount!!.uuid, true)
                        startActivity(intent)
                    } else {
                        onErrorGeneratingKey()
                        Accounts.listAccounts(this)
                    }

                    finish()
                } else {
                    apiStartPendingIntent()
                }
            }
            OpenPgpApi.RESULT_CODE_ERROR -> {
                val error = result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                Timber.e("RESULT_CODE_ERROR: %s", error.message)
            }
        }
    }

    private fun apiStartPendingIntent() {
        try {
            pendingIntent!!.send(RESULT_CODE_PENDING_INTENT,
                    PendingIntent.OnFinished { pendingIntent, intent, i, s, bundle -> Timber.d("In pending intent callback") },
                    null)
            //startIntentSender(pendingIntent!!.intentSender, null, 0, 0, 0)
        } catch (e: IntentSender.SendIntentException) {
            Timber.e(e, "Error launching pending intent")
        }
    }

    // Enable E3 in the account settings preference
    private fun enableE3Preference() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPrefs.edit()
        editor.putBoolean(AccountSettingsFragment.PREFERENCE_E3_ENABLE, true)
        editor.apply()
    }

    private fun setE3KeyPreference(e3KeyId: Long) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPrefs.edit()
        editor.putLong(AccountSettingsFragment.PREFERENCE_E3_KEY, e3KeyId)
        editor.apply()
        mAccount!!.e3Key = e3KeyId
    }

    private fun setMessage(resId: Int) {
        mMessageView?.text = getString(resId)
    }

    private fun onCancel() {
        mCanceled = true
        setMessage(R.string.account_setup_check_settings_canceling_msg)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun onErrorGeneratingKey() {
        mCanceled = true
        setMessage(R.string.account_setup_e3_error_generating_key)
        setResult(Activity.RESULT_CANCELED)
    }

    override fun onClick(v: View) {
        if (v.id == R.id.cancel) {
            onCancel()
        }
    }

    override fun dialogCancelled(dialogId: Int) {
        // nothing to do here...
    }

    override fun doPositiveClick(dialogId: Int) {
        if (dialogId == R.id.dialog_account_setup_error) {
            finish()
        }
    }

    override fun doNegativeClick(dialogId: Int) {
        if (dialogId == R.id.dialog_account_setup_error) {
            mCanceled = false
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}