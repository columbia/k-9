package com.fsck.k9.notification

import android.util.SparseArray
import com.fsck.k9.Account
import com.fsck.k9.mailstore.LocalMessage

class NewE3KeyNotifications internal constructor(private val notificationHelper: NotificationHelper,
            private val contentCreator: NotificationContentCreator,
            private val deviceNotifications: DeviceNotifications,
            private val wearNotifications: WearNotifications) {

    fun addNewE3KeyNotification(account: Account, message: LocalMessage) {
        val content = contentCreator.createFromMessage(account, message)

        synchronized(lock) {
            val notificationData = getOrCreateNotificationData(account)
            val result = notificationData.addNotificationContent(content)

            if (result.shouldCancelNotification()) {
                val notificationId = result.getNotificationId()
                cancelNotification(notificationId)
            }

            createStackedNotification(account, result.getNotificationHolder())
            createSummaryNotification(account, notificationData, false)
        }
    }

    private fun getOrCreateNotificationData(account: Account, message: LocalMessage): NotificationData {
        val notificationData = getNotificationData(account)
        if (notificationData != null) {
            return notificationData
        }

        val accountNumber = account.accountNumber
        val newNotificationHolder = createNotificationData(account, unreadMessageCount)
        notifications.put(accountNumber, newNotificationHolder)

        return newNotificationHolder
    }

    private fun getNotificationData(account: Account): NotificationData {
        val accountNumber = account.accountNumber
        return notifications.get(accountNumber)
    }

    companion object {
        private val lock = Any()
        private val notifications = SparseArray<NotificationData>()
    }
}