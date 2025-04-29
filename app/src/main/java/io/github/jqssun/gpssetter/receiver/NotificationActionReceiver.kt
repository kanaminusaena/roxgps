class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == NotificationsChannel.ACTION_STOP && context is BaseMapActivity) {
            // Trigger the stop button click logic
            context.runOnUiThread {
                context.binding.stopButton.performClick()
            }
        }
    }
}