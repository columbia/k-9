package com.fsck.k9.notification

import com.fsck.k9.Account

internal class E3NotificationData(account: Account) : NotificationData(account) {
    lateinit var verificationPhrase: String

}