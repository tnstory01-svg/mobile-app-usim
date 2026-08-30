package com.tnstory.esimprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ProbeCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOWNLOAD_CALLBACK) return
        val operationId = intent.getStringExtra(EXTRA_OPERATION_ID) ?: return
        val store = ProbeStateStore(context)
        val callbackResult = store.onCallback(
            operationId,
            resultCode,
            System.currentTimeMillis(),
        )
        context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
        if (callbackResult != ProbeStateStore.CallbackResult.OpenResolution) {
            return
        }

        if (!showResolutionNotification(context, operationId, intent)) {
            store.failActiveOperation(ProbeOutcome.Reason.RESOLUTION_NOTIFICATION_UNAVAILABLE)
            context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
        }
    }

    private fun showResolutionNotification(context: Context, operationId: String, callbackIntent: Intent): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "eSIM probe callbacks", NotificationManager.IMPORTANCE_HIGH),
            )
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        val launchIntent =
            Intent(context, ProbeActivity::class.java)
                .setAction(ACTION_OPEN_RESOLUTION)
                .putExtra(EXTRA_OPERATION_ID, operationId)
                .putExtra(EXTRA_CALLBACK_INTENT, callbackIntent)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            operationId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            NotificationManagerCompat.from(context).notify(
                operationId.hashCode(),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("eSIM action required")
                    .setContentText("Open the probe to continue the system eSIM flow.")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build(),
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val ACTION_DOWNLOAD_CALLBACK = "com.tnstory.esimprobe.DOWNLOAD_CALLBACK"
        const val ACTION_OPEN_RESOLUTION = "com.tnstory.esimprobe.OPEN_RESOLUTION"
        const val EXTRA_OPERATION_ID = "operation_id"
        const val EXTRA_CALLBACK_INTENT = "callback_intent"
        const val ACTION_STATE_CHANGED = "com.tnstory.esimprobe.STATE_CHANGED"
        private const val CHANNEL_ID = "esim_probe_callbacks"

        fun callbackUri(operationId: String): Uri =
            Uri.parse("esimprobe://download-callback/$operationId")
    }
}
