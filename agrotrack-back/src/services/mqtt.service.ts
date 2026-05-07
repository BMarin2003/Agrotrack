import mqtt from 'mqtt';
import { configServer } from 'src/config';
import { execProcedure } from '@core/db/connection';
import { rulesEngine } from '@services/rules.engine';
import { watchdogService } from '@services/watchdog.service';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

// Payload que envía el Gateway
interface GatewayPayload {
  sensor_id: number;
  temperature?: number;
  voltage?: number;
  battery?: number;
  timestamp?: number;
  extra_data?: Record<string, any>;
}

class MqttService {
  private client: mqtt.MqttClient | null = null;

  connect() {
    const { brokerUrl, username, password, clientId, topicPrefix } = configServer.mqtt;

    this.client = mqtt.connect(brokerUrl, {
      clientId,
      username: username || undefined,
      password: password || undefined,
      reconnectPeriod: 5000,
      connectTimeout: 10000,
    });

    this.client.on('connect', () => {
      console.log(`[MQTT] Conectado a ${brokerUrl}`);
      // Suscripción wildcard: escucha todos los gateways
      this.client!.subscribe(`${topicPrefix}/+/telemetry`, err => {
        if (err) console.error('[MQTT] Error al suscribirse:', err);
        else console.log(`[MQTT] Suscrito a ${topicPrefix}/+/telemetry`);
      });
    });

    this.client.on('message', async (topic, message) => {
      await this.handleMessage(topic, message);
    });

    this.client.on('error', err => console.error('[MQTT] Error:', err));
    this.client.on('reconnect', () => console.log('[MQTT] Reconectando...'));
    this.client.on('offline', () => console.warn('[MQTT] Cliente desconectado'));
  }

  private async handleMessage(topic: string, rawMessage: Buffer) {
    try {
      // topic: agrotrack/gateways/{gateway_identifier}/telemetry
      const parts = topic.split('/');
      const gatewayIdentifier = parts[2];

      const payload: GatewayPayload = JSON.parse(rawMessage.toString());

      const gatewayResult = await execProcedure('iot.get_gateway_by_identifier', [{ identifier: gatewayIdentifier }]);
      if (gatewayResult.error || !gatewayResult.result) {
        console.warn(`[MQTT] Gateway no registrado: ${gatewayIdentifier}`);
        return;
      }

      const gateway = gatewayResult.result;

      const saveResult = await execProcedure('iot.save_reading', [{
        sensor_id: payload.sensor_id,
        gateway_id: gateway.id,
        temperature: payload.temperature ?? null,
        voltage: payload.voltage ?? null,
        battery: payload.battery ?? null,
        extra_data: payload.extra_data ?? null,
      }]);

      if (saveResult.error) {
        console.error('[MQTT] Error al guardar lectura:', saveResult.error);
        return;
      }

      const reading = {
        ...payload,
        gateway_id: gateway.id,
        reading_id: saveResult.result?.id,
        received_at: new Date().toISOString(),
      };

      watchdogService.heartbeat(payload.sensor_id);
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
