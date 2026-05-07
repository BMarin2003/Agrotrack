/**
 * Gateway IoT Mock - Fase 1
 *
 * Simula un gateway publicando lecturas cada 5 segundos.
 * Soporta dos modos:
 *   MOCK_MODE=mqtt  (default) → publica al broker MQTT
 *   MOCK_MODE=http             → POST directo al endpoint /api/telemetry/ingest
 *
 * Variables de entorno:
 *   MOCK_MODE          mqtt | http
 *   MQTT_BROKER_URL    mqtt://localhost:1883
 *   GATEWAY_IDENTIFIER GW-001
 *   GATEWAY_API_KEY    (solo para modo http)
 *   API_URL            http://localhost:3000/api
 *   SENSOR_COUNT       3
 */

import mqtt from 'mqtt';

const MODE = process.env.MOCK_MODE || 'mqtt';
const BROKER_URL = process.env.MQTT_BROKER_URL || 'mqtt://localhost:1883';
const GATEWAY_IDENTIFIER = process.env.GATEWAY_IDENTIFIER || 'GW-001';
const GATEWAY_API_KEY = process.env.GATEWAY_API_KEY || 'test-api-key';
const API_URL = process.env.API_URL || 'http://localhost:3000/api';
const SENSOR_COUNT = parseInt(process.env.SENSOR_COUNT || '3');
const INTERVAL_MS = 5_000;

// Estado persistente de cada sensor para simular variación realista
interface SensorState {
  temperature: number;
  voltage: number;
  battery: number;
}

const sensors: Map<number, SensorState> = new Map();
for (let i = 1; i <= SENSOR_COUNT; i++) {
  sensors.set(i, { temperature: 22 + Math.random() * 5, voltage: 12.0, battery: 95 });
}

function nextReading(id: number): object {
  const s = sensors.get(id)!;

  // Deriva aleatoria pequeña para simular variación real
  s.temperature = parseFloat((s.temperature + (Math.random() - 0.48) * 0.4).toFixed(2));
  s.voltage = parseFloat((s.voltage + (Math.random() - 0.5) * 0.05).toFixed(3));
  s.battery = parseFloat(Math.max(0, s.battery - Math.random() * 0.1).toFixed(2));

  return {
    sensor_id: id,
    temperature: s.temperature,
    voltage: s.voltage,
    battery: s.battery,
    timestamp: Date.now(),
  };
}

// ─── Modo MQTT ──────────────────────────────────────────────────────────────

async function runMqtt() {
  const client = mqtt.connect(BROKER_URL, {
    clientId: `mock-gateway-${GATEWAY_IDENTIFIER}-${Date.now()}`,
    reconnectPeriod: 3000,
  });

  client.on('connect', () => {
    console.log(`[Mock] Conectado a ${BROKER_URL} como ${GATEWAY_IDENTIFIER}`);

    setInterval(() => {
      for (const [sensorId] of sensors) {
        const reading = nextReading(sensorId);
        const topic = `agrotrack/gateways/${GATEWAY_IDENTIFIER}/telemetry`;
        const payload = JSON.stringify(reading);
        client.publish(topic, payload, { qos: 1 });
        console.log(`[Mock MQTT] → ${topic}`, reading);
      }
    }, INTERVAL_MS);
  });

  client.on('error', err => console.error('[Mock] Error MQTT:', err));
}

// ─── Modo HTTP ───────────────────────────────────────────────────────────────

async function runHttp() {
  console.log(`[Mock] Modo HTTP → ${API_URL}/telemetry/ingest`);

  setInterval(async () => {
    for (const [sensorId] of sensors) {
      const reading = nextReading(sensorId);
      try {
        const res = await fetch(`${API_URL}/telemetry/ingest`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-api-key': GATEWAY_API_KEY,
          },
          body: JSON.stringify(reading),
        });
        const json = await res.json();
        console.log(`[Mock HTTP] sensor=${sensorId} status=${res.status}`, json);
      } catch (error) {
        console.error(`[Mock HTTP] Error sensor=${sensorId}:`, error);
      }
    }
  }, INTERVAL_MS);
}

// ─── Main ────────────────────────────────────────────────────────────────────

console.log(`\n🌱 AgroTrack Gateway Mock`);
console.log(`   Modo     : ${MODE}`);
console.log(`   Gateway  : ${GATEWAY_IDENTIFIER}`);
console.log(`   Sensores : ${SENSOR_COUNT}`);
console.log(`   Intervalo: ${INTERVAL_MS / 1000}s\n`);

if (MODE === 'http') {
  runHttp();
} else {
  runMqtt();
}
