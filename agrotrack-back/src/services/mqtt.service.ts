import mqtt from 'mqtt';
import { configServer } from '../config';
import { execProcedure } from '@core/db/connection';
import { rulesEngine } from '@services/rules.engine';
import { watchdogService } from '@services/watchdog.service';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

export interface TwarmPayload {
  GatewayID: number;
  Slave: string;
  Tipo: string;
  T: number;
  TimeStamp: number;
  RSSI: number;
  SNR: number;
  Bs: number;
  Bg: number;
  ConnMode?: string;
  PendingSync?: number;
  /** Voltaje real del sensor (V) — NO existe en el protocolo Twarm real
   *  confirmado hasta ahora (solo Bs/Bg = batería %). Campo opcional que
   *  únicamente rellena la flota simulada; el ingest de MQTT real nunca lo
   *  recibe, así que un gateway físico real seguirá guardando NULL como
   *  siempre hasta que el firmware confirme un campo equivalente. */
  V?: number;
}

class MqttService {
  private client: mqtt.MqttClient | null = null;
  // Cache: "gatewayId:slaveIdentifier" → sensor DB id
  private sensorCache = new Map<string, number>();
  // Cache: gateway identifier → { id, name }
  private gatewayCache = new Map<string, { id: number; name: string }>();
  // Tracking de Slaves únicos para diagnóstico
  private uniqueSlaves = new Set<string>();

  connect() {
    const { brokerUrl, username, password, clientId, topicPrefix, topicSubscribe } = configServer.mqtt;

    this.client = mqtt.connect(brokerUrl, {
      clientId,
      username: username || undefined,
      password: password || undefined,
      reconnectPeriod: 5000,
      connectTimeout: 10000,
    });

    this.client.on('connect', () => {
      console.log(`[MQTT] Conectado a ${brokerUrl}`);
      const topic = topicSubscribe || `${topicPrefix}/+/telemetry`;
      this.client!.subscribe(topic, err => {
        if (err) console.error('[MQTT] Error al suscribirse:', err);
        else console.log(`[MQTT] Suscrito a ${topic}`);
      });

      // Ack de comandos (ej. calibración) — esquema propuesto, sin firmware
      // real implementado todavía. Ver migración 009.
      const ackTopic = `${topicPrefix}/+/ack/calibration`;
      this.client!.subscribe(ackTopic, err => {
        if (err) console.error('[MQTT] Error al suscribirse al ack:', err);
        else console.log(`[MQTT] Suscrito a ${ackTopic}`);
      });
    });

    this.client.on('message', async (topic, message) => {
      if (topic.includes('/ack/calibration')) {
        await this.handleCalibrationAck(message);
        return;
      }
      await this.handleMessage(topic, message);
    });

    this.client.on('error', err => console.error('[MQTT] Error:', err));
    this.client.on('reconnect', () => console.log('[MQTT] Reconectando...'));
    this.client.on('offline', () => console.warn('[MQTT] Cliente desconectado'));
  }

  private async resolveSensorId(gatewayId: number, slaveIdentifier: string): Promise<number | null> {
    const cacheKey = `${gatewayId}:${slaveIdentifier}`;
    if (this.sensorCache.has(cacheKey)) {
      return this.sensorCache.get(cacheKey)!;
    }

    const listResult = await execProcedure('iot.list_sensors', [{ gateway_id: gatewayId }]);
    if (!listResult.error && Array.isArray(listResult.result)) {
      const found = listResult.result.find((s: any) => s.identifier === slaveIdentifier);
      if (found) {
        this.sensorCache.set(cacheKey, found.id);
        return found.id;
      }
    }

    // Auto-crear sensor si no existe (identifier único por gateway)
    const createResult = await execProcedure('iot.save_sensor', [{
      gateway_id: gatewayId,
      name: slaveIdentifier,
      identifier: slaveIdentifier,
      type: 'temperature',
      unit: '°C',
    }]);

    if (createResult.error || !createResult.result?.id) {
      console.error('[MQTT] No se pudo crear sensor:', slaveIdentifier, createResult.error);
      return null;
    }

    const newId: number = createResult.result.id;
    console.log(`[MQTT] Sensor auto-creado: ${slaveIdentifier} → id=${newId}`);
    this.sensorCache.set(cacheKey, newId);
    return newId;
  }

  async resolveGateway(
    identifier: string,
    hints?: { name?: string; location?: string },
  ): Promise<{ id: number; name: string } | null> {
    if (this.gatewayCache.has(identifier)) {
      return this.gatewayCache.get(identifier)!;
    }

    const found = await execProcedure('iot.get_gateway_by_identifier', [{ identifier }]);
    if (!found.error && found.result) {
      this.gatewayCache.set(identifier, found.result);
      return found.result;
    }

    // El gateway físico ya existe y está transmitiendo — igual que con los
    // sensores (resolveSensorId), no tiene sentido bloquear su telemetría
    // esperando que alguien lo dé de alta a mano en la app/API. Si el
    // llamador conoce un nombre/ubicación real (ej. la flota simulada),
    // se usa; si no (MQTT real sin ese contexto), cae al nombre genérico.
    const created = await execProcedure('iot.save_gateway', [{
      name: hints?.name ?? `Gateway ${identifier}`,
      identifier,
      location: hints?.location,
    }]);

    if (created.error || !created.result?.id) {
      console.error('[MQTT] No se pudo auto-crear gateway:', identifier, created.error);
      return null;
    }

    const gateway = { id: created.result.id, name: hints?.name ?? `Gateway ${identifier}` };
    console.log(`[MQTT] Gateway auto-creado: ${identifier} → id=${gateway.id}`);
    this.gatewayCache.set(identifier, gateway);
    return gateway;
  }

  private async handleMessage(topic: string, rawMessage: Buffer) {
    try {
      const { topicSubscribe, gatewayUid } = configServer.mqtt;

      // TEMPORAL — quitar una vez confirmado qué claves manda el gateway real
      // (voltaje, ConnMode, PendingSync). Loguea el JSON tal cual llega, sin
      // pasar por la interfaz TwarmPayload, para no ocultar campos inesperados.
      console.log(`[MQTT][DEBUG PAYLOAD] topic=${topic} raw=${rawMessage.toString()}`);

      const payload: TwarmPayload = JSON.parse(rawMessage.toString());

      let gatewayIdentifier: string;
      if (topicSubscribe) {
        // Tópico fijo compartido por varios gateways (no wildcard por gateway):
        // el identificador real viene DENTRO del payload, no del tópico.
        // MQTT_GATEWAY_UID queda solo como fallback si algún mensaje llegara
        // sin GatewayID.
        gatewayIdentifier = payload.GatewayID != null ? String(payload.GatewayID) : gatewayUid;
      } else {
        gatewayIdentifier = topic.split('/')[2];
      }

      await this.ingestPayload(gatewayIdentifier, payload);
    } catch (error) {
      console.error('[MQTT] Error procesando mensaje:', error);
    }
  }

  /**
   * Lógica de ingesta compartida por MQTT real y por MockFleetService — así
   * el mock ejercita EXACTAMENTE el mismo camino (auto-registro, guardado en
   * BD, RulesEngine, WatchdogService, broadcast WS) que un gateway físico,
   * en vez de simular la app por otro lado.
   */
  async ingestPayload(
    gatewayIdentifier: string,
    payload: TwarmPayload,
    gatewayHints?: { name?: string; location?: string },
  ) {
    try {
      // Diagnóstico: registrar Slaves únicos vistos
      if (!this.uniqueSlaves.has(payload.Slave)) {
        this.uniqueSlaves.add(payload.Slave);
        console.log(`[MQTT] Slave nuevo: ${payload.Slave} (total únicos: ${this.uniqueSlaves.size})`);
      }

      const gateway = await this.resolveGateway(gatewayIdentifier, gatewayHints);
      if (!gateway) return;

      if (payload.ConnMode || typeof payload.PendingSync === 'number' || typeof payload.Bg === 'number') {
        await execProcedure('iot.update_gateway_status', [{
          gateway_id: gateway.id,
          connectivity_mode: payload.ConnMode,
          pending_sync_count: payload.PendingSync,
          battery: payload.Bg,
        }]);
      }

      const sensorId = await this.resolveSensorId(gateway.id, payload.Slave);
      if (sensorId === null) return;

      const saveResult = await execProcedure('iot.save_reading', [{
        sensor_id: sensorId,
        gateway_id: gateway.id,
        temperature: payload.T ?? null,
        voltage: payload.V ?? null,
        battery: payload.Bs ?? null,
        extra_data: {
          rssi: payload.RSSI,
          snr: payload.SNR,
          tipo: payload.Tipo,
          bg: payload.Bg,
          timestamp: payload.TimeStamp,
        },
      }]);

      if (saveResult.error) {
        console.error('[MQTT] Error al guardar lectura:', saveResult.error);
        return;
      }

      const reading = {
        sensor_id: sensorId,
        gateway_id: gateway.id,
        temperature: payload.T,
        battery: payload.Bs,
        reading_id: saveResult.result?.id,
        received_at: new Date().toISOString(),
      };

      watchdogService.heartbeat(sensorId, gateway.id);
      await rulesEngine.evaluate(reading);
      broadcastToGateway(gateway.id, { type: 'reading', data: reading });
    } catch (error) {
      console.error('[MQTT] Error en ingestPayload:', error);
    }
  }

  /** Publica un comando al gateway. No hay garantía de entrega ni de que el
   *  firmware exista todavía — el llamador es responsable del timeout. */
  private publish(topic: string, payload: unknown) {
    if (!this.client) {
      console.warn('[MQTT] publish() llamado sin cliente conectado, se ignora:', topic);
      return;
    }
    this.client.publish(topic, JSON.stringify(payload), err => {
      if (err) console.error('[MQTT] Error al publicar:', topic, err);
    });
  }

  /** Comando de calibración — esquema propuesto (ver migración 009), sin
   *  firmware real todavía. requestId permite correlacionar el ack. */
  publishCalibrationCommand(gatewayIdentifier: string, payload: {
    requestId: string; slave: string; gain: number; intercept: number;
  }) {
    const { topicPrefix } = configServer.mqtt;
    this.publish(`${topicPrefix}/${gatewayIdentifier}/cmd/calibration`, payload);
  }

  private async handleCalibrationAck(rawMessage: Buffer) {
    try {
      const payload = JSON.parse(rawMessage.toString());
      if (!payload.requestId) return;
      await execProcedure('iot.mark_calibration_ack', [{
        request_id: payload.requestId,
        status: payload.status === 'error' ? 'error' : 'ok',
      }]);
    } catch (error) {
      console.error('[MQTT] Error procesando ack de calibración:', error);
    }
  }

  disconnect() {
    this.client?.end();
  }
}

export const mqttService = new MqttService();
