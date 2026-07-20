---
title: Ingesta MQTT y auto-registro
description: Cómo un gateway o sensor nuevo se da de alta solo, sin pasos manuales.
sidebar:
  order: 2
---

Este es el mecanismo central del sistema: **un gateway o sensor nunca se da de alta manualmente** desde la aplicación. Se registra solo, la primera vez que transmite.

## Conexión al broker

`MqttService.connect()` (`services/mqtt.service.ts`) abre una única conexión persistente al broker (`mqtt.connect(brokerUrl, { clientId, username, password, reconnectPeriod: 5000, connectTimeout: 10000 })`) y se suscribe al tópico configurado (`configServer.mqtt.topicSubscribe`). Los eventos `connect`, `error`, `close`, `offline` del cliente se loguean para diagnóstico operativo, y la reconexión ante caídas de red es automática (parámetro `reconnectPeriod`).

## El contrato del payload — `TwarmPayload`

Cada gateway publica un JSON con esta forma (interfaz `TwarmPayload`, exportada desde `mqtt.service.ts`):

```ts
interface TwarmPayload {
  GatewayID?: string | number;  // identificador real del gateway físico
  Slave: string;                // identificador del sensor dentro del gateway
  Tipo: string;                 // tipo de métrica (ej. "temperature")
  T?: number;                   // temperatura
  TimeStamp?: string;
  RSSI?: number;                // intensidad de señal
  SNR?: number;
  Bs?: number;                  // batería del sensor
  Bg?: number;                  // batería del gateway
  ConnMode?: string;             // "wifi" | "sim"
  PendingSync?: number;          // lecturas pendientes de sincronizar
  V?: number;                   // voltaje del sensor
}
```

El backend no asume una forma fija de tópico por gateway (no hace wildcard-per-device): el **identificador real del gateway viene dentro del payload** (`GatewayID`), no del nombre del tópico MQTT — esto permite que múltiples gateways físicos compartan el mismo tópico raíz sin necesidad de reconfigurar el broker por cada dispositivo nuevo.

## Auto-registro — `resolveGateway` / `resolveSensorId`

Al llegar un mensaje (`handleMessage(topic, rawMessage)`):

1. **Resolver el gateway** (`resolveGateway(identifier, hints?)`):
   - Se busca primero en una caché en memoria (`gatewayCache: Map<string, {id, name}>`) para evitar una consulta a BD por cada mensaje.
   - Si no está en caché, se consulta `iot.get_gateway_by_identifier`.
   - **Si el gateway no existe en la base de datos, se crea automáticamente** vía `iot.save_gateway`, usando el identificador que llegó en el payload como clave única. No hace falta que un operador lo dé de alta manualmente desde la app — el sistema trata la primera transmisión real como el evento de alta.

2. **Resolver el sensor** (`resolveSensorId(gatewayId, slaveIdentifier)`):
   - Misma lógica de caché + auto-creación, pero con el sensor asociado a ese `gatewayId` (`iot.save_sensor`, con `identifier` único *dentro del gateway*, no global — dos gateways distintos pueden tener cada uno un sensor `Slave=1` sin colisionar).

3. **Persistir la lectura** — `ingestPayload(gatewayIdentifier, payload, hints?)` (método público, compartido por cualquier fuente que hable el mismo contrato de payload) hace:
   - `iot.update_gateway_status` — actualiza conectividad (`ConnMode`), batería del gateway (`Bg`), sincronización pendiente (`PendingSync`) y marca `last_synced_at`.
   - `iot.save_reading` — persiste la lectura del sensor (temperatura, voltaje `V`, batería `Bs`).
   - `watchdogService.heartbeat(sensorId, gatewayId)` — informa al watchdog que el sensor sigue vivo (usado para detectar sensores offline).
   - `rulesEngine.evaluate(reading)` — evalúa umbrales configurados y detecta anomalías (lecturas fuera de rango físico o saltos bruscos de temperatura), generando alertas cuando corresponde.
   - `broadcastToGateway(gatewayId, {...})` — empuja la lectura en tiempo real a cualquier cliente Android conectado por WebSocket a ese gateway.

Esto significa que **instalar un gateway nuevo en campo no requiere ningún paso de configuración remota en el backend**: se enchufa, empieza a transmitir, y en su primer mensaje ya aparece en la lista de gateways de la app, con sus sensores creándose a medida que cada uno transmite su primera lectura.

## De MQTT a la app — REST + WebSocket

El backend es la única pieza que habla MQTT. Hacia la aplicación expone dos canales, cada uno para un propósito distinto:

- **REST** (`modules/iot/*.api.ts`) — consultas puntuales e históricas: lista de gateways/sensores, historial de lecturas con rango de fecha, reportes agregados, alertas, umbrales. Protegido con JWT + control de permisos por rol (`requirePermission`).
- **WebSocket** (`modules/iot/telemetry.ws.ts`, función `broadcastToGateway`) — empuje en vivo de lecturas nuevas y alertas nuevas a los clientes suscritos a un gateway, para que el Dashboard de la app se actualice sin tener que hacer polling constante.

## Resumen de decisiones

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Auto-registro MQTT | Alta manual de gateway/sensor en la app | Cero fricción operativa al instalar hardware nuevo |
