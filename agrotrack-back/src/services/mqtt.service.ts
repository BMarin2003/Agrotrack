import mqtt from 'mqtt';
import { configServer } from 'src/config';
import { execProcedure } from '@core/db/connection';
import { rulesEngine } from '@services/rules.engine';
import { watchdogService } from '@services/watchdog.service';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

interface TwarmPayload {
  GatewayID: number;
  Slave: string;
  Tipo: string;
  T: number;
  TimeStamp: number;
  RSSI: number;
  SNR: number;
  Bs: number;
  Bg: number;
}

class MqttService {
  private client: mqtt.MqttClient | null = null;
  // Cache: "gatewayId:slaveIdentifier" → sensor DB id
  private sensorCache = new Map<string, number>();
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
    });

    this.client.on('message', async (topic, message) => {
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

  private async handleMessage(topic: string, rawMessage: Buffer) {
    try {
      const { topicSubscribe, gatewayUid, topicPrefix } = configServer.mqtt;

      let gatewayIdentifier: string;
      if (topicSubscribe) {
        gatewayIdentifier = gatewayUid;
      } else {
        gatewayIdentifier = topic.split('/')[2];
      }

      const payload: TwarmPayload = JSON.parse(rawMessage.toString());

      // Diagnóstico: registrar Slaves únicos vistos
      if (!this.uniqueSlaves.has(payload.Slave)) {
        this.uniqueSlaves.add(payload.Slave);
        console.log(`[MQTT] Slave nuevo: ${payload.Slave} (total únicos: ${this.uniqueSlaves.size}) → [${[...this.uniqueSlaves].join(', ')}]`);
      }

      const gatewayResult = await execProcedure('iot.get_gateway_by_identifier', [{ identifier: gatewayIdentifier }]);
      if (gatewayResult.error || !gatewayResult.result) {
        console.warn(`[MQTT] Gateway no registrado: ${gatewayIdentifier}`);
        return;
      }

      const gateway = gatewayResult.result;

      const sensorId = await this.resolveSensorId(gateway.id, payload.Slave);
      if (sensorId === null) return;

      const saveResult = await execProcedure('iot.save_reading', [{
        sensor_id: sensorId,
        gateway_id: gateway.id,
        temperature: payload.T ?? null,
        voltage: null,
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
      console.error('[MQTT] Error procesando mensaje:', error);
    }
  }

  disconnect() {
    this.client?.end();
  }
}

export const mqttService = new MqttService();
