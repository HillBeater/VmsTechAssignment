package com.hillbeater.vmstechsassignment.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.hillbeater.vmstechsassignment.R

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val CHANNEL_ID = "geofence_channel"
        private const val NOTIFICATION_ID = 101

        fun getGeofencePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            intent.action = "com.google.android.gms.location.GeofenceTrasition.ACTION_GEOFENCE_TRANSITION"
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.action.equals("com.google.android.gms.location.GeofenceTrasition.ACTION_GEOFENCE_TRANSITION", true)) {
            Log.w(TAG, "Received unknown intent action: ${intent.action}")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null) {
            if (geofencingEvent.hasError()) {
                val errorMessage = geofencingEvent.errorCode
                Log.e(TAG, "Geofencing error: $errorMessage")
                return
            }
        }

        val geofenceTransition = geofencingEvent?.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val transitionString = when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered Home"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited Home"
                else -> "Unknown transition"
            }

            val triggeringGeofences = geofencingEvent.triggeringGeofences

            sendNotification(context, transitionString)

            if (triggeringGeofences != null) {
                Log.i(TAG, "$transitionString for geofences: ${triggeringGeofences.joinToString { it.requestId }}")
            }
        } else {
            Log.w(TAG, "Unknown geofence transition: $geofenceTransition")
        }
    }

    private fun sendNotification(context: Context, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if(message == "Entered Home") R.drawable.enter else R.drawable.exit)
            .setContentTitle("Geofence Event")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}