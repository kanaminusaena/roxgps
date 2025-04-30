package io.github.jqssun.gpssetter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jqssun.gpssetter.utils.NotificationsChannel.ACTION_STOP

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_STOP) {
            // Directly send the ACTION_STOP broadcast.
            // BaseMapActivity listens for this and triggers performStopButtonClick().
            context?.sendBroadcast(Intent(ACTION_STOP))
        }
    }
}