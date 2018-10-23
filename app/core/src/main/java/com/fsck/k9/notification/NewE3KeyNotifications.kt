package com.fsck.k9.notification

import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.SparseArray
import com.fsck.k9.Account
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
        private val notifications = SparseArray<NotificationData>()
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

            createSummaryNotification(account, notificationData, false)
        }
    }

    private fun getOrCreateNotificationData(account: Account, message: LocalMessage): NotificationData {
        val notificationData = notifications.get(account.accountNumber)
        if (notificationData != null) {
            return notificationData
        }

        val accountNumber = account.accountNumber
        val newNotificationHolder = NotificationData(account)
        notifications.put(accountNumber, newNotificationHolder)

        return newNotificationHolder
    }

    private fun createSummaryNotification(account: Account, notificationData: NotificationData, silent: Boolean) {
        val notification = deviceNotifications.buildSummaryNotificationForE3Key(account, notificationData, silent)
        val notificationId = NotificationIds.getNewMailSummaryNotificationId(account)

        getNotificationManager().notify(notificationId, notification)
    }

    private fun cancelNotification(notificationId: Int) {
        getNotificationManager().cancel(notificationId)
    }

    private fun getNotificationManager(): NotificationManagerCompat {
        return notificationHelper.getNotificationManager()
    }
}