package com.corall.agrotrack.service

// TODO: Descomentar cuando se integre Firebase (requiere google-services.json)
// import com.google.firebase.messaging.FirebaseMessagingService
// import com.google.firebase.messaging.RemoteMessage
// import dagger.hilt.android.AndroidEntryPoint

/**
 * Servicio FCM para alertas push en tiempo real.
 *
 * Activación:
 * 1. Agregar google-services.json en /app
 * 2. Descomentar plugin google-services en build.gradle.kts (raíz y app)
 * 3. Descomentar la clase y el <service> en AndroidManifest.xml
 * 4. Registrar el FCM token en el backend via POST /api/devices/register
 *
 * El backend enviará notificaciones push cuando:
 * - Una alerta de umbral no ha sido resuelta en X minutos
 * - Un sensor lleva >5 minutos offline
 */

// @AndroidEntryPoint
// class AgroTrackFcmService : FirebaseMessagingService() {
//
//     override fun onNewToken(token: String) {
//         // Enviar token al backend para vincular con el usuario autenticado
//         // registerTokenUseCase(token)
//     }
//
//     override fun onMessageReceived(message: RemoteMessage) {
//         val title = message.notification?.title ?: "AgroTrack"
//         val body  = message.notification?.body  ?: return
//         showNotification(title, body)
//     }
//
//     private fun showNotification(title: String, body: String) {
//         // NotificationManagerCompat + NotificationCompat.Builder
//     }
// }
