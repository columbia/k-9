package com.fsck.k9.ui.e3

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.fsck.k9.Account
import com.fsck.k9.ui.settings.account.AccountSettingsFragment

class E3EnableDisableToggler(private val context: Context) {

    fun setE3DisabledState(account: Account) {
        account.e3Provider = ""
        val editor = getEditor()
        editor.putBoolean(AccountSettingsFragment.PREFERENCE_E3_ENABLE, false)
        editor.apply()
    }

    fun setE3EnabledState(account: Account, e3Provider: String) {
        account.e3Provider = e3Provider
        val editor = getEditor()
        editor.putBoolean(AccountSettingsFragment.PREFERENCE_E3_ENABLE, true)
        editor.apply()
    }

    private fun getEditor(): SharedPreferences.Editor {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.edit()
    }
}