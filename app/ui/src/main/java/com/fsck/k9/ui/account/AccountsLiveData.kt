package com.fsck.k9.ui.account

import androidx.lifecycle.LiveData
import com.fsck.k9.Account
import com.fsck.k9.Preferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg

class AccountsLiveData(val preferences: Preferences) : LiveData<List<Account>>() {
    init {
        loadAccountsAsync()
    }

    private fun loadAccountsAsync() {
        GlobalScope.launch(Dispatchers.Main) {
            val accounts = bg {
                loadAccounts()
            }

            value = accounts.await()
        }
    }

    private fun loadAccounts(): List<Account> {
        return preferences.accounts
    }
}
