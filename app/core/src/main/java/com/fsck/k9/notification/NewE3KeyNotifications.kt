package com.fsck.k9.notification

import androidx.core.app.NotificationManagerCompat
import android.util.SparseArray
import com.fsck.k9.Account
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.mailstore.LocalMessage

/**
 * At the moment, duplicates a lot of code from NewMailNotifications.
 */
class NewE3KeyNotifications internal constructor(private val notificationHelper: NotificationHelper,
            private val contentCreator: NotificationContentCreator,
            private val deviceNotifications: DeviceNotifications,
            private val wearNotifications: WearNotifications) {

    companion object {
        private val lock = Any()
        private val notifications = SparseArray<E3NotificationData>()
    }

    fun addNewE3KeyNotification(account: Account, message: LocalMessage) {
        val content = contentCreator.createFromMessage(account, message)

        synchronized(lock) {
            val notificationData = getOrCreateNotificationData(account, message)
            val result = notificationData.addNotificationContent(content)

            if (result.shouldCancelNotification()) {
                val notificationId = result.notificationId
                cancelNotification(notificationId)
            }

            notifyUsingSummaryNotification(account, message, notificationData, false)
        }
    }

    private fun getOrCreateNotificationData(account: Account, message: LocalMessage): E3NotificationData {
        val notificationData = notifications.get(account.accountNumber)
        if (notificationData != null) {
            return notificationData
        }

        val accountNumber = account.accountNumber
        val newNotificationHolder = E3NotificationData(account)

        newNotificationHolder.verificationPhrase = message.getHeader(E3Constants.MIME_E3_VERIFICATION)[0]
        notifications.put(accountNumber, newNotificationHolder)

        return newNotificationHolder
    }

    private fun notifyUsingSummaryNotification(account: Account, message: LocalMessage, notificationData: E3NotificationData, silent: Boolean) {
        val notification = deviceNotifications.buildSummaryNotificationForE3Key(account, message, notificationData, silent)
        val notificationId = NotificationIds.getNewE3KeyNotificationId(account)

        getNotificationManager().notify(notificationId, notification)
    }

    private fun cancelNotification(notificationId: Int) {
        getNotificationManager().cancel(notificationId)
    }

    private fun getNotificationManager(): NotificationManagerCompat {
        return notificationHelper.getNotificationManager()
    }
}