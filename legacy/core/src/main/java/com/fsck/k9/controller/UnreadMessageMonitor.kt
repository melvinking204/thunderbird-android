package com.fsck.k9.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.k9mail.legacy.message.controller.MessageCountsProvider
import app.k9mail.legacy.search.SearchAccount
import com.fsck.k9.led.GpioService
import com.fsck.k9.notification.NotificationIds
import com.fsck.k9.notification.NotificationResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class UnreadMessageMonitor(
    private val context: Context,
    private val notificationResourceProvider: NotificationResourceProvider,
    private val messageCountsProvider: MessageCountsProvider,
    private val gpioService: GpioService = GpioService(),
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var flowJob: Job? = null
    private val ledPin = 19
    private val blinkRateHz = 1

    private val channelId = "unread_messages_monitor"

    fun start() {
        if (flowJob != null) return

        Timber.d("Starting UnreadMessageMonitor")
        val unifiedInbox = SearchAccount.createUnifiedInboxAccount("Unified Inbox", "All messages")

        createNotificationChannel()

        flowJob = messageCountsProvider.getMessageCountsFlow(unifiedInbox.relatedSearch)
            .map { it.unread }
            .distinctUntilChanged()
            .onEach { unreadCount ->
                if (unreadCount > 0) {
                    Timber.d("Unread messages detected ($unreadCount). Starting LED blinking on pin $ledPin")
                    gpioService.startBlinking(ledPin, blinkRateHz)
                    showNotification(unreadCount)
                } else {
                    Timber.d("No unread messages. Stopping LED blinking on pin $ledPin")
                    gpioService.stopBlinking(ledPin)
                    cancelNotification()
                }
            }
            .launchIn(coroutineScope)
    }

    fun stop() {
        flowJob?.cancel()
        flowJob = null
        Timber.d("UnreadMessageMonitor stopped. Stopping LED blinking.")
        gpioService.stopBlinking(ledPin)
        cancelNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Unread Messages Monitor"
            val descriptionText = "Shows active LED blinking status"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(unreadCount: Int) {
        val notificationManager = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(notificationResourceProvider.iconNewMail)
            .setContentTitle("Unread Emails")
            .setContentText("You have $unreadCount unread email(s)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .build()

        try {
            notificationManager.notify(NotificationIds.UNREAD_MESSAGE_MONITOR_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.e(e, "Notification permission missing for UnreadMessageMonitor")
        }
    }

    private fun cancelNotification() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NotificationIds.UNREAD_MESSAGE_MONITOR_NOTIFICATION_ID)
    }
}
