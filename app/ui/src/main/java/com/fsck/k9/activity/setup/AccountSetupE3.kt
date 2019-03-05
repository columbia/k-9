package com.fsck.k9.activity.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.fsck.k9.ui.settings.account.AccountSettingsFragment
import com.fsck.k9.ui.settings.account.OpenPgpAppSelectDialog
import org.koin.android.ext.android.inject
import org.openintents.openpgp.OpenPgpApiManager
import org.openintents.openpgp.util.OpenPgpProviderUtil


class AccountSetupE3: K9Activity(), View.OnClickListener,
        ConfirmationDialogFragment.ConfirmationDialogFragmentListener {
    private val openPgpApiManager: OpenPgpApiManager by inject(parameters = { mapOf("lifecycleOwner" to this) })
    private var mMessageView: TextView? = null
    private var mProgressBar: ProgressBar? = null

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private var mAccount: Account? = null
        private var mCanceled: Boolean = false

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

        // Choose crypto/key provider
        val openPgpProviderPackages = OpenPgpProviderUtil.getOpenPgpProviderPackages(this)
        if (openPgpProviderPackages.size == 1) {
            mAccount!!.e3Provider = openPgpProviderPackages[0]
            configureE3KeyPreference()
        } else {
            OpenPgpAppSelectDialog.startOpenPgpChooserActivity(this, mAccount, OpenPgpAppSelectDialog.ProviderType.E3)
        }

        // Generate a key if one doesn't exist already



        // Upload the key
        //val intent = E3KeyUploadActivity.createIntent(this, mAccount!!.uuid)
        //startActivity(intent)

        Accounts.listAccounts(this)
        finish()
    }

    // Enable E3 in the account settings preference
    private fun configureE3KeyPreference() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPrefs.edit()
        editor.putBoolean(AccountSettingsFragment.PREFERENCE_E3_ENABLE, true)
        editor.apply()
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