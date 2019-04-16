package com.fsck.k9.activity.setup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.activity.Accounts
import com.fsck.k9.activity.K9Activity
import com.fsck.k9.crypto.OpenPgpApiHelper
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.ui.R
import com.fsck.k9.ui.e3.E3EnableDisableToggler
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
    private var mBottomSheetDialog: BottomSheetDialog? = null
    private var pendingIntent: PendingIntent? = null
    private var selectKeyPendingIntent: PendingIntent? = null
    private var mAccount: Account? = null
    private var mCanceled: Boolean = false
    private var defaultUserId: String? = null

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val RESULT_CODE_PENDING_INTENT_GENERATE = 9990
        private const val RESULT_CODE_PENDING_INTENT_SELECT = 9991

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
        mProgressBar = findViewById(R.id.progress)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid)
        defaultUserId = OpenPgpApiHelper.buildUserId(mAccount!!.getIdentity(0))

        // Choose crypto/key provider (aka choose OpenKeychain, for example)
        val openPgpProviderPackages = OpenPgpProviderUtil.getOpenPgpProviderPackages(this)
        if (openPgpProviderPackages.size == 1) {
            mAccount!!.e3Provider = openPgpProviderPackages[0]
            enableE3Preference(mAccount!!.e3Provider!!)
        } else {
            OpenPgpAppSelectDialog.startOpenPgpChooserActivity(this, mAccount, OpenPgpAppSelectDialog.ProviderType.E3)
        }

        // If this account already has an E3 key configured,
        // ask the user if he really wants to generate a new one,
        // otherwise just generate one right away.
        if (mAccount!!.e3Key != Account.NO_OPENPGP_KEY) {
            val bottomSheetLayout: View = layoutInflater.inflate(R.layout.account_setup_e3_bottom_sheet, null)
            bottomSheetLayout.findViewById<Button>(R.id.button_no).setOnClickListener {
                mBottomSheetDialog!!.dismiss()
                onCancel()
            }
            bottomSheetLayout.findViewById<Button>(R.id.button_ok).setOnClickListener {
                mBottomSheetDialog!!.dismiss()

                generateE3Key(openPgpCreateKeyCallback)
            }

            mBottomSheetDialog = BottomSheetDialog(this)
            mBottomSheetDialog!!.setContentView(bottomSheetLayout)

            mBottomSheetDialog!!.show()
        } else {
            generateE3Key(openPgpCreateKeyCallback)
        }

        return
    }

    // Generate key, then the callback will upload it
    private fun generateE3Key(callback: OpenPgpApi.IOpenPgpCallback) {
        val data = Intent(OpenPgpApi.ACTION_CREATE_ENCRYPT_ON_RECEIPT_KEY)
        data.putExtra(OpenPgpApi.EXTRA_NAME, String.format(resources.getString(R.string.account_setup_e3_generated_key_name), Build.BRAND, Build.MODEL))
        data.putExtra(OpenPgpApi.EXTRA_EMAIL, mAccount!!.email)

        val serviceConnection = OpenPgpServiceConnection(applicationContext, mAccount!!.e3Provider, object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: IOpenPgpService2) {
                val openPgpApi = OpenPgpApi(applicationContext, service)
                openPgpApi.executeApiAsync(data, null, null, callback)
            }

            override fun onError(e: Exception) {
                Timber.e(e, "Got error while binding to OpenPGP service")
            }
        })

        serviceConnection.bindToService()
    }

    private fun selectE3Key(callback: OpenPgpApi.IOpenPgpCallback) {
        // Now select the key in OpenKeychain so that it can be used for signing
        val serviceConnection = OpenPgpServiceConnection(applicationContext, mAccount!!.e3Provider, object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: IOpenPgpService2) {
                val openPgpApi = OpenPgpApi(applicationContext, service)
                val selectKeyIntent = Intent(OpenPgpApi.ACTION_GET_SIGN_KEY_ID)
                selectKeyIntent.putExtra(OpenPgpApi.EXTRA_USER_ID, defaultUserId)
                selectKeyIntent.putExtra(OpenPgpApi.EXTRA_PRESELECT_KEY_ID, mAccount!!.e3Key)
                selectKeyIntent.putExtra(OpenPgpApi.EXTRA_SHOW_AUTOCRYPT_HINT, false)
                openPgpApi.executeApiAsync(selectKeyIntent, null, null, callback)
            }

            override fun onError(e: Exception) {
                Timber.e(e, "Got error while binding to OpenPGP service")
            }
        })

        serviceConnection.bindToService()
    }

    // Runs after OpenPgpApi succeeds in generating the key
    private val openPgpCreateKeyCallback = OpenPgpApi.IOpenPgpCallback { result ->
        val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)
        when (resultCode) {
            OpenPgpApi.RESULT_CODE_SUCCESS,
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                pendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)

                if (result.hasExtra(OpenPgpApi.EXTRA_KEY_ID)) {
                    val keyId = result.getLongExtra(OpenPgpApi.EXTRA_KEY_ID, Account.NO_OPENPGP_KEY)
                    pendingIntent = null

                    if (keyId != Account.NO_OPENPGP_KEY) {
                        setE3KeyPreference(keyId)
                        selectE3Key(openPgpSelectKeyCallback)
                    } else {
                        onErrorGeneratingKey()
                        Accounts.listAccounts(this)
                        finish()
                    }
                } else {
                    apiStartPendingIntentForResult(pendingIntent!!, RESULT_CODE_PENDING_INTENT_GENERATE)
                }
            }
            OpenPgpApi.RESULT_CODE_ERROR -> {
                val error = result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                Timber.e("RESULT_CODE_ERROR: %s", error.message)
            }
        }
    }

    // Runs after OpenPgpApi succeeds in selecting the key to use for signing
    private val openPgpSelectKeyCallback = OpenPgpApi.IOpenPgpCallback { result ->
        val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)
        when (resultCode) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                selectKeyPendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)
                if (result.hasExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
                    val signKeyId = result.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, Account.NO_OPENPGP_KEY)

                    if (signKeyId != Account.NO_OPENPGP_KEY) {
                        // Upload the key
                        val intent = E3KeyUploadActivity.createIntent(this, mAccount!!.uuid, true)
                        startActivity(intent)
                    } else {
                        onErrorGeneratingKey()
                        Accounts.listAccounts(this)
                    }

                    finish()
                } else {
                    apiStartPendingIntentForResult(selectKeyPendingIntent!!, RESULT_CODE_PENDING_INTENT_SELECT)
                }
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                selectKeyPendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)

                apiStartPendingIntentForResult(selectKeyPendingIntent!!, RESULT_CODE_PENDING_INTENT_SELECT)
            }
            OpenPgpApi.RESULT_CODE_ERROR -> {
                val error = result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                Timber.e("RESULT_CODE_ERROR: %s", error.message)
            }
            else -> {
                Timber.e("OpenPgp select sign key result code: $resultCode")
            }
        }
    }

    private fun apiStartPendingIntentForResult(pi: PendingIntent, activityRequestCode: Int) {
        try {
            startIntentSenderForResult(pi.intentSender, activityRequestCode, null, 0, 0, 0)
        } catch (e: IntentSender.SendIntentException) {
            Timber.e(e, "Error launching pending intent")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RESULT_CODE_PENDING_INTENT_GENERATE -> {
                Timber.d("Got result for key generation PendingIntent")
                if (resultCode == Activity.RESULT_OK) {
                    generateE3Key(openPgpCreateKeyCallback)
                }
            }
            RESULT_CODE_PENDING_INTENT_SELECT -> {
                Timber.d("Got result for key selection PendingIntent")
                if (resultCode == Activity.RESULT_OK) {
                    // Upload the key
                    val intent = E3KeyUploadActivity.createIntent(this, mAccount!!.uuid, true)
                    startActivity(intent)
                } else {
                    onErrorGeneratingKey()
                    Accounts.listAccounts(this)
                }
                finish()
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    // Enable E3 in the account settings preference
    private fun enableE3Preference(e3Provider: String) {
        E3EnableDisableToggler(this).setE3EnabledState(mAccount!!, e3Provider)
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