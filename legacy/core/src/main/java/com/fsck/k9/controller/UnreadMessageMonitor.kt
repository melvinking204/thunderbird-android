package com.fsck.k9.controller

import app.k9mail.legacy.message.controller.MessageCountsProvider
import app.k9mail.legacy.search.SearchAccount
import com.fsck.k9.led.GpioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class UnreadMessageMonitor(
    private val messageCountsProvider: MessageCountsProvider,
    private val gpioService: GpioService = GpioService(),
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var flowJob: Job? = null
    private val ledPin = 19
    private val blinkRateHz = 1

    fun start() {
        if (flowJob != null) return

        Timber.d("Starting UnreadMessageMonitor")
        val unifiedInbox = SearchAccount.createUnifiedInboxAccount("Unified Inbox", "All messages")

        flowJob = messageCountsProvider.getMessageCountsFlow(unifiedInbox.relatedSearch)
            .map { it.unread > 0 }
            .distinctUntilChanged()
            .onEach { hasUnreadMessages ->
                if (hasUnreadMessages) {
                    Timber.d("Unread messages detected. Starting LED blinking on pin $ledPin")
                    gpioService.startBlinking(ledPin, blinkRateHz)
                } else {
                    Timber.d("No unread messages. Stopping LED blinking on pin $ledPin")
                    gpioService.stopBlinking(ledPin)
                }
            }
            .launchIn(coroutineScope)
    }

    fun stop() {
        flowJob?.cancel()
        flowJob = null
        Timber.d("UnreadMessageMonitor stopped. Stopping LED blinking.")
        gpioService.stopBlinking(ledPin)
    }
}
