---
title: Notificación de recuperación de sensor
description: Interfaz/flujo, lógica de deduplicación y pruebas funcionales de la HU más compleja del backlog analizado.
---

## Por qué esta es la historia de usuario más compleja del lote analizado

De las diez historias evaluadas (voltaje, conectividad, sincronización pendiente,
gráfico histórico, alias de sensor, alias en dashboard, sesión persistente,
**notificación de recuperación**, historial de alertas, umbrales independientes),
esta es la única que exige coordinar correctamente **tres sistemas asíncronos
distintos en tiempo real** en vez de una sola capa:

1. Un proceso de fondo en el backend que decide, sin que nadie se lo pida,
   cuándo un sensor "se recuperó" (y no confundirlo con su primera transmisión).
2. Un canal de comunicación en vivo (WebSocket) que empuje ese evento al
   dispositivo del usuario sin que la app tenga que preguntar por polling.
3. Una notificación nativa de Android que aparezca incluso si el usuario está
   en otra pantalla de la app.

Las demás historias son, en esencia, CRUD con una vista distinta (un campo
más en una tabla, un selector nuevo, un gráfico sobre datos que ya existían).
Esta es la única que es un **sistema reactivo de punta a punta**: detectar un
evento → decidir si ya se notificó antes (deduplicación) → propagarlo por red
→ transformarlo en una notificación del sistema operativo. Un error en
cualquiera de los tres eslabones (falso positivo al reconectar, notificación
duplicada en cada heartbeat, mensaje perdido si el WebSocket se reconecta en
mal momento) es invisible en una demo tranquila y solo aparece bajo uso real.

**Valor real para la empresa:** en una cadena de frío, un sensor que deja de
transmitir y luego vuelve a hacerlo sin que nadie se entere representa una
ventana ciega — el operador no sabe si el producto estuvo fuera de rango
mientras el sensor estaba "callado". Esta funcionalidad es la diferencia
entre que un técnico se entere de una desconexión-reconexión en el momento
(y pueda ir a revisar por qué pasó — ¿corte de energía?, ¿el gateway se
movió de lugar?) o que se entere días después revisando un historial, cuando
ya no hay nada que hacer al respecto. Es la funcionalidad que convierte al
sistema de "panel que hay que estar mirando" a "sistema que avisa solo".

---

## 1. Implementación de interfaz/flujo

El flujo completo, de servidor a pantalla del usuario:

```
watchdog.service.ts (backend)
   → detecta que el sensor volvió a transmitir
   → guarda la alerta y la empuja por WebSocket a todos los clientes conectados
        ↓
WebSocketManager (Android, singleton de sesión)
   → recibe el mensaje JSON crudo por el socket ya abierto
        ↓
ObserveLiveReadingsUseCase
   → lo interpreta como un LiveEvent.NewAlert
        ↓
SessionViewModel (vive mientras dura la sesión, no una pantalla)
   → cachea la alerta y dispara la notificación del sistema
        ↓
AlertNotificationHelper
   → construye y muestra la notificación nativa de Android
```

El punto de diseño clave es que la conexión WebSocket y la suscripción a
alertas viven en `SessionViewModel`, no en la pantalla del Dashboard — así
la notificación llega **sin importar en qué pantalla esté el usuario**
mientras la app está en primer plano.

**`SessionViewModel.kt`** — conecta el socket y enruta cada alerta nueva hacia la notificación:

```kotlin
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository:      AuthRepository,
    private val sessionManager:      SessionManager,
    private val wsManager:           WebSocketManager,
    private val observeLive:         ObserveLiveReadingsUseCase,
    private val notificationHelper:  AlertNotificationHelper,
    private val telemetryRepository: TelemetryRepository,
) : ViewModel() {

    private var liveUpdatesStarted = false

    fun connectLiveUpdates() {
        if (liveUpdatesStarted) return
        liveUpdatesStarted = true

        wsManager.connect(DEFAULT_GATEWAY_ID)
        observeLive()
            .onEach { event ->
                if (event is LiveEvent.NewAlert) {
                    telemetryRepository.cacheAlert(event.data)
                    notificationHelper.showAlert(event.data)
                }
            }
            .launchIn(viewModelScope)
    }
}
```

**`AlertNotificationHelper.kt`** — canal dedicado (`agrotrack_recovery`) y
notificación nativa, con título y prioridad distintos a las alertas de umbral
u offline:

```kotlin
@Singleton
class AlertNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val CHANNEL_RECOVERY = "agrotrack_recovery"
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            // ...otros canales (umbral, offline, anomalía)...
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

        NotificationManagerCompat.from(context).notify(notificationId(alert), builder.build())
    }

    private fun alertMeta(alert: Alert): Pair<String, String>? = when (alert.type) {
        "sensor_recovered" -> CHANNEL_RECOVERY to "Sensor recuperado"
        // ...otros tipos (threshold_exceeded, sensor_offline, anomalous_reading)...
        else -> null
    }
}
```

**Nota honesta de alcance:** este flujo requiere que la app esté en
primer o segundo plano (proceso vivo) para recibir la notificación, ya que
usa un WebSocket propio y no un servicio de push del sistema operativo. Si
el sistema operativo mata el proceso, la notificación no llega hasta que la
app se vuelve a abrir. Resolver eso requeriría integrar un servicio de push
externo (p. ej. Firebase Cloud Messaging), lo cual quedó explícitamente fuera
de alcance de este ciclo de desarrollo por decisión conjunta.

---

## 2. Implementación de lógica y validación

La parte no trivial de esta historia no es "mandar un mensaje" — es decidir
**cuándo** mandarlo. Un sensor que reporta cada pocos segundos genera
cientos de heartbeats; si cada uno después de una reconexión disparara una
alerta, el usuario recibiría notificaciones repetidas por el mismo evento.

**`watchdog.service.ts`** — el corazón de la lógica: un `Set` de sensores
actualmente offline es la fuente de verdad para decidir si un heartbeat es
"nada nuevo" o "esto es una recuperación real":

```typescript
const heartbeats = new Map<number, { gateway_id: number; last_seen: number }>();
const offlineSensors = new Set<number>();

class WatchdogService {
  async heartbeat(sensorId: number, gatewayId?: number) {
    const existing = heartbeats.get(sensorId);
    const resolvedGatewayId = gatewayId ?? existing?.gateway_id ?? 0;

    heartbeats.set(sensorId, { gateway_id: resolvedGatewayId, last_seen: Date.now() });

    // Solo es "recuperación" si el sensor estaba marcado offline.
    // Un heartbeat normal de un sensor que nunca se desconectó no dispara nada.
    if (offlineSensors.has(sensorId)) {
      offlineSensors.delete(sensorId);

      const message = `Sensor ${sensorId} volvió a transmitir`;
      const result = await execProcedure('iot.save_alert', [{
        sensor_id: sensorId,
        gateway_id: resolvedGatewayId,
        user_id: null,
        type: 'sensor_recovered',
        message,
      }]);

      if (!result.error) {
        broadcastToAll({
          type: 'alert',
          data: { id: result.result?.id, sensor_id: sensorId, gateway_id: resolvedGatewayId,
                   type: 'sensor_recovered', message },
        });
      }
    }
  }

  async check() {
    const now = Date.now();
    for (const [sensorId, data] of heartbeats) {
      const silentMs = now - data.last_seen;
      if (silentMs > OFFLINE_THRESHOLD_MS && !offlineSensors.has(sensorId)) {
        offlineSensors.add(sensorId); // marca offline UNA vez — la próxima verificación no repite la alerta
        await execProcedure('iot.save_alert', [{ sensor_id: sensorId, gateway_id: data.gateway_id,
          type: 'sensor_offline', message: `Sensor ${sensorId} sin transmitir por más de ${OFFLINE_THRESHOLD_MS / 1000} segundos` }]);
        broadcastToAll({ type: 'alert', data: { sensor_id: sensorId, gateway_id: data.gateway_id,
          type: 'sensor_offline', message: `Sensor ${sensorId} offline` } });
      }
    }
  }
}
```

**`telemetry.ws.ts`** — la validación de identidad ocurre en la apertura del
socket (token verificado antes de aceptar la conexión), y la difusión es un
simple fan-out a todos los clientes autenticados conectados:

```typescript
export function broadcastToAll(payload: object) {
  const message = JSON.stringify(payload);
  for (const [, client] of wsClients) {
    try { client.ws.send(message); } catch {}
  }
}

export const TelemetryWs = new Elysia().ws('telemetry', {
  open: async ws => {
    const { token, gateway_id, wsid } = ws.data.query as any;
    if (!token || !wsid) return ws.terminate();

    const user = verifyToken(token);
    if (!user) return ws.terminate();

    wsClients.set(wsid, { ws, user: { id: user.id }, gatewayId: gateway_id || '*' });
  },
  close: ws => { wsClients.delete((ws.data.query as any).wsid); },
});
```

**`ObserveLiveReadingsUseCase.kt`** (Android) — la validación en el otro
extremo: el mensaje que llega por el socket es texto crudo no confiable, así
que el parseo nunca asume una forma válida y descarta silenciosamente
cualquier payload que no calce:

```kotlin
internal fun parseLiveEvent(gson: Gson, raw: String): LiveEvent? {
    return try {
        val obj  = gson.fromJson(raw, JsonObject::class.java)
        val type = obj.get("type")?.asString ?: return null
        val data = obj.getAsJsonObject("data") ?: return null

        when (type) {
            "alert" -> LiveEvent.NewAlert(
                Alert(
                    id        = data.get("id")?.asLong ?: 0L,
                    sensorId  = data.get("sensor_id").asInt,
                    gatewayId = data.get("gateway_id").asInt,
                    type      = data.get("type")?.asString ?: "unknown",
                    message   = data.get("message")?.asString ?: "",
                    resolved  = false,
                    createdAt = System.currentTimeMillis(),
                )
            )
            // "reading" -> ... (no relevante para esta HU)
            else -> null
        }
    } catch (e: Exception) { null }
}
```

---

## 3. Pruebas funcionales

**Backend — `tests/watchdog.dedup.test.ts`** (caja blanca: casos elegidos
mirando las dos ramas de `heartbeat()` — con y sin `offlineSensors.has(id)`):

```typescript
it("heartbeat de un sensor que NUNCA estuvo offline no dispara alerta de recuperación", async () => {
    const sensorId = 900001;
    await watchdogService.heartbeat(sensorId, 1);
    expect(saveAlertCalls.length).toBe(0);
});

it("tras superar el umbral de offline sin heartbeat, check() marca offline y el siguiente heartbeat dispara sensor_recovered", async () => {
    const sensorId = 900002;
    const pastTimestamp = Date.now() - 10 * 60 * 1000;
    const nowSpy = spyOn(Date, "now").mockReturnValue(pastTimestamp);
    await watchdogService.heartbeat(sensorId, 1);
    nowSpy.mockRestore();

    await watchdogService.check();               // marca el sensor como offline
    await watchdogService.heartbeat(sensorId, 1); // heartbeat real → debe disparar la recuperación

    expect(saveAlertCalls.some((a) => a.type === "sensor_recovered" && a.sensor_id === sensorId)).toBe(true);
});
```

Ejecución real:

```
$ bun test tests/watchdog.dedup.test.ts --timeout 15000

[Watchdog] Sensor 900002 sin transmitir por 600s
[Watchdog] Sensor 900002 volvió a transmitir

 2 pass
 0 fail
 2 expect() calls
Ran 2 tests across 1 file. [1.36s]
```

**Android — `ObserveLiveReadingsUseCaseTest.kt`** (caja negra a nivel de la
función de parseo: casos derivados solo de la forma del mensaje público que
manda el servidor, no de su implementación interna):

```kotlin
@Test
fun `evento sensor_recovered se parsea con sus datos completos`() {
    val raw = """{"type":"alert","data":{"id":42,"sensor_id":7,"gateway_id":1,"type":"sensor_recovered","message":"Sensor 7 volvió a transmitir"}}"""

    val event = parseLiveEvent(gson, raw)

    assertTrue(event is LiveEvent.NewAlert)
    val alert = (event as LiveEvent.NewAlert).data
    assertEquals("sensor_recovered", alert.type)
    assertEquals(7, alert.sensorId)
    assertEquals("Sensor 7 volvió a transmitir", alert.message)
}

@Test
fun `evento de lectura no se confunde con un evento de alerta`() {
    val raw = """{"type":"reading","data":{"sensor_id":7,"gateway_id":1,"temperature":21.5}}"""
    assertTrue(parseLiveEvent(gson, raw) is LiveEvent.Reading)
}

@Test
fun `payload sin tipo reconocido se descarta`() {
    assertNull(parseLiveEvent(gson, """{"foo":"bar"}"""))
}

@Test
fun `JSON malformado no lanza excepcion y se descarta`() {
    assertNull(parseLiveEvent(gson, "esto no es json"))
}
```

Ejecución real:

```
$ ./gradlew :app:testDebugUnitTest --tests "com.corall.agrotrack.domain.usecase.telemetry.ObserveLiveReadingsUseCaseTest"

BUILD SUCCESSFUL in 19s
31 actionable tasks: 13 executed, 18 up-to-date
```

Resultado del XML de JUnit generado: `tests="4" skipped="0" failures="0" errors="0"`.

### Qué cubren estas pruebas y qué queda fuera

Las pruebas automatizadas cubren los dos puntos donde un error sería más
costoso: que el backend **no dispare una alerta falsa** en el heartbeat
normal de un sensor sano, y que **sí la dispare exactamente una vez** al
volver de un estado offline real; y que el cliente Android interprete
correctamente (o descarte de forma segura) cualquier mensaje que llegue por
el socket. La conexión WebSocket en sí (apertura, reconexión con backoff,
keepalive) y la aparición visual de la notificación en la bandeja del
sistema no se prestan a una prueba automatizada — se verificaron
manualmente: sensor forzado a estado offline y luego a transmitir de nuevo,
confirmando que el mensaje llega por el socket y la notificación aparece en
el dispositivo con el canal y el texto esperados.
