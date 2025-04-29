package io.github.jqssun.gpssetter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jqssun.gpssetter.ui.BaseMapActivity
import io.github.jqssun.gpssetter.utils.NotificationsChannel

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
    if (intent?.action == NotificationsChannel.ACTION_STOP && context is BaseMapActivity) {
        context.runOnUiThread {
            context.performStopButtonClick() // Call the public method
        }
    }
}
}