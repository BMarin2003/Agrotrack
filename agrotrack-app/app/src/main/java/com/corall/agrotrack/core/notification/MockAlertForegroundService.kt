package com.corall.agrotrack.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.corall.agrotrack.data.mock.MockData
import com.corall.agrotrack.data.mock.MockTemperatureSimulator
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MockAlertForegroundService : Service() {

    @Inject lateinit var telemetryRepository: TelemetryRepository
    @Inject lateinit var notificationHelper: AlertNotificationHelper

    private val simulator = MockTemperatureSimulator()
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createMockServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(MOCK_SERVICE_NOTIF_ID, buildServiceNotification())
        scope.launch { runSimulationLoop() }
        return START_STICKY
    }

    private suspend fun runSimulationLoop() {
        while (true) {
            delay(Random.nextLong(15_000L, 45_000L))
            val alert = simulator.tick()
            if (alert != null) {
                MockData.addMockAlert(alert)
                telemetryRepository.cacheAlert(alert)
                notificationHelper.showAlert(alert)
                Log.d(TAG, "tick → ${alert.type} | temp=${"%.1f".format(simulator.currentTemp)}°C")
            } else {
                Log.d(TAG, "tick → null | temp=${"%.1f".format(simulator.currentTemp)}°C")
            }
        }
    }

    private fun createMockServiceChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MOCK_SERVICE,
                "Servicio de simulación",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notificación persistente del simulador (solo desarrollo)"
            }
        )
    }

    private fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_MOCK_SERVICE)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Simulación de alertas activa")
            .setContentText("Generando alertas mock para testing — solo en desarrollo")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG                   = "MockAlertService"
        private const val CHANNEL_MOCK_SERVICE  = "agrotrack_mock_service"
        private const val MOCK_SERVICE_NOTIF_ID = 9999
    }
}
