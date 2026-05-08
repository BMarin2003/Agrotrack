import { configServer } from 'src/config';
import { watchdogService } from '@services/watchdog.service';
import { broadcastToGateway } from '@modules/iot/telemetry.ws';

interface SensorState {
  temperature: number;
  voltage: number;
  battery: number;
  tStart: number;
  tTarget: number;
  tick: number;
  cycleTicks: number;
}

export interface LocalReading {
  sensor_id: number;
  gateway_id: number;
  temperature: number;
  voltage: number;
  battery: number;
  received_at: string;
}

const MAX_BUFFER = 500;
const DEFAULT_CYCLE_TICKS = 120;
const TAU_RATIO = 0.33;
const LOCAL_GATEWAY_ID = 1;

export const localReadings = new Map<number, LocalReading[]>();

class GatewayMockService {
  private timer: Timer | null = null;
  private sensors = new Map<number, SensorState>();

  start() {
    const { gatewayIdentifier, sensorCount, intervalMs } = configServer.mock;

    for (let i = 1; i <= sensorCount; i++) {
      const tStart = 18 + Math.random() * 4;
      const tTarget = 1.5 + Math.random() * 2.5;
      const cycleTicks = DEFAULT_CYCLE_TICKS + Math.floor((Math.random() - 0.5) * 20);

      this.sensors.set(i, {
        temperature: tStart,
        voltage: 3.85 + Math.random() * 0.3,
        battery: 72 + Math.random() * 18,
        tStart,
        tTarget,
        tick: Math.floor(Math.random() * cycleTicks),
        cycleTicks,
      });

      localReadings.set(i, []);
    }

    console.log(`[${gatewayIdentifier}] Online — ${sensorCount} sensores conectados`);
    this.timer = setInterval(() => this.tick(gatewayIdentifier), intervalMs);
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private tick(identifier: string) {
    for (const [sensorId, state] of this.sensors) {
      this.step(state);

      const reading: LocalReading = {
        sensor_id: sensorId,
        gateway_id: LOCAL_GATEWAY_ID,
        temperature: state.temperature,
        voltage: state.voltage,
        battery: state.battery,
        received_at: new Date().toISOString(),
      };

      const buf = localReadings.get(sensorId)!;
      buf.push(reading);
      if (buf.length > MAX_BUFFER) buf.shift();

      watchdogService.heartbeat(sensorId, LOCAL_GATEWAY_ID);
      broadcastToGateway(LOCAL_GATEWAY_ID, { type: 'reading', data: reading });

      const sn = `${identifier}-S${String(sensorId).padStart(2, '0')}`;
      const ts = Math.floor(Date.now() / 1000);
      console.log(
        `[${identifier}] DATA  sn=${sn}  T=${state.temperature.toFixed(2)}°C  V=${state.voltage.toFixed(3)}V  B=${state.battery.toFixed(1)}%  unix=${ts}`
      );
    }
  }

  private step(s: SensorState) {
    s.tick++;

    if (s.tick >= s.cycleTicks) {
      s.tick = 0;
      s.tStart = 18 + Math.random() * 4;
      s.tTarget = 1.5 + Math.random() * 2.5;
    }

    const tau = s.cycleTicks * TAU_RATIO;
    const smooth = s.tTarget + (s.tStart - s.tTarget) * Math.exp(-s.tick / tau);
    const noise = (Math.random() - 0.5) * 0.6;

    s.temperature = parseFloat(Math.max(-5, Math.min(25, smooth + noise)).toFixed(2));
    s.voltage = parseFloat(Math.min(4.2, Math.max(3.7, s.voltage + (Math.random() - 0.5) * 0.02)).toFixed(3));
    s.battery = parseFloat(Math.max(0, s.battery - Math.random() * 0.002).toFixed(2));
  }
}

export const gatewayMockService = new GatewayMockService();
