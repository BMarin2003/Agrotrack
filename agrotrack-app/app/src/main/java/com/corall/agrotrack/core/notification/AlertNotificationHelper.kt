package com.corall.agrotrack.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.corall.agrotrack.domain.model.Alert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val CHANNEL_THRESHOLD = "agrotrack_threshold"
        private const val CHANNEL_OFFLINE   = "agrotrack_offline"
        private const val CHANNEL_ANOMALY   = "agrotrack_anomaly"
        private const val CHANNEL_RECOVERY  = "agrotrack_recovery"
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(
                CHANNEL_THRESHOLD,
                "Alertas de umbral",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Temperatura fuera de rango configurado" },
            NotificationChannel(
                CHANNEL_OFFLINE,
                "Sensores desconectados",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Sensor sin transmitir datos" },
            NotificationChannel(
                CHANNEL_ANOMALY,
                "Datos anómalos",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Lecturas fuera del rango físico o saltos bruscos" },
            NotificationChannel(
                CHANNEL_RECOVERY,
                "Sensor recuperado",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "El sensor volvió a transmitir tras una desconexión" },
        ))
    }

    fun showAlert(alert: Alert) {
        val (channelId, title) = alertMeta(alert) ?: return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context)
            .notify(notificationId(alert), builder.build())
    }

    private fun alertMeta(alert: Alert): Pair<String, String>? = when (alert.type) {
        "threshold_exceeded" -> {
            val title = if (alert.message.contains("mínimo", ignoreCase = true))
                "Temperatura bajo umbral mínimo"
            else
                "Temperatura sobre umbral máximo"
            CHANNEL_THRESHOLD to title
        }
        "sensor_offline"   -> CHANNEL_OFFLINE  to "Sensor desconectado"
        "anomalous_reading",
        "sensor_degraded"  -> CHANNEL_ANOMALY  to "Datos anómalos detectados"
        "sensor_recovered" -> CHANNEL_RECOVERY to "Sensor recuperado"
        else -> null
    }

    private fun notificationId(alert: Alert): Int =
        (alert.type + "_" + alert.sensorId).hashCode()
}
