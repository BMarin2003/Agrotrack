import { configServer } from '../config';
import { execProcedure } from '@core/db/connection';
import { mqttService, type TwarmPayload } from '@services/mqtt.service';

interface MockSensorState {
  temperature: number;
  tStart: number;
  tTarget: number;
  tick: number;
  cycleTicks: number;
  battery: number; // Bs — batería del sensor (%), 0-100
  voltage: number; // V — voltaje real (V), correlacionado con `battery`
  silentTicksLeft: number; // >0 = simulando sensor caído (no envía nada)
}

interface MockGatewayDef {
  identifier: string;
  name: string;
  location: string;
}

interface MockGatewayState extends MockGatewayDef {
  numericId: number; // solo para rellenar TwarmPayload.GatewayID — ingestPayload no lo usa, identifier viaja aparte
  connMode: 'wifi' | 'sim';
  batteryGw: number; // Bg — batería del gateway (%)
  pendingSync: number;
  sensors: Map<string, MockSensorState>;
}

const TAU_RATIO = 0.33;

// Identificadores con forma de MAC (como los que sí se vieron reales en el
// broker bajo device/status/<mac> — ver conversación) y nombres/ubicaciones
// de una operación de cadena de frío agrícola real. Fijos a propósito (no
// generados al azar en cada arranque) para que reiniciar el backend
// reutilice los mismos gateways en vez de crear duplicados.
const FLEET: MockGatewayDef[] = [
  { identifier: 'a4cf5e120a01', name: 'Cámara Fría 1 - Packing',    location: 'Fundo San Vicente, Ica' },
  { identifier: 'a4cf5e120a02', name: 'Cámara Fría 2 - Packing',    location: 'Fundo San Vicente, Ica' },
  { identifier: 'a4cf5e120a03', name: 'Túnel de Congelado A',       location: 'Planta Chincha' },
  { identifier: 'a4cf5e120a04', name: 'Túnel de Congelado B',       location: 'Planta Chincha' },
  { identifier: 'a4cf5e120a05', name: 'Almacén Frigorífico Norte',  location: 'Fundo La Esperanza, Trujillo' },
  { identifier: 'a4cf5e120a06', name: 'Almacén Frigorífico Sur',    location: 'Fundo La Esperanza, Trujillo' },
  { identifier: 'a4cf5e120a07', name: 'Sala de Preenfriado',        location: 'Planta Ica' },
  { identifier: 'a4cf5e120a08', name: 'Cuarto de Maduración',       location: 'Planta Ica' },
  { identifier: 'a4cf5e120a09', name: 'Zona de Carga y Despacho',   location: 'Terminal Paracas' },
  { identifier: 'a4cf5e120a0a', name: 'Packing House Principal',    location: 'Fundo San Vicente, Ica' },
  { identifier: 'a4cf5e120a0b', name: 'Invernadero Norte',          location: 'Fundo Los Álamos, Barranca' },
  { identifier: 'a4cf5e120a0c', name: 'Invernadero Sur',            location: 'Fundo Los Álamos, Barranca' },
];

/**
 * Flota simulada de gateways/sensores — para probar la app de punta a punta
 * mientras no hay hardware Twarm real transmitiendo (ver conversación:
 * el broker mqtt.coralldar.com no tuvo tráfico Twarm en 5+ min de escucha).
 *
 * Cada payload pasa por MqttService.ingestPayload(), el mismo camino que un
 * gateway físico real usaría — auto-registro, guardado en BD, RulesEngine,
 * WatchdogService, broadcast WS.
 *
 * `V` (voltaje) SÍ se rellena acá con un valor realista correlacionado con
 * la batería — a diferencia del resto de campos, este NO viene confirmado
 * del protocolo real (TwarmPayload no lo trae todavía). Es deliberadamente
 * el único campo "de más": permite demostrar la HU de voltaje mientras se
 * confirma con firmware el nombre real de la clave (o si el hardware
 * directamente no lo reporta y hay que rediseñar la HU para mostrar
 * batería % en su lugar).
 */
class MockFleetService {
  private timer: Timer | null = null;
  private stopped = false;
  private gateways: MockGatewayState[] = [];

  start() {
    const { sensorsPerGateway, intervalMs } = configServer.mockFleet;
    const count = Math.min(configServer.mockFleet.gatewayCount, FLEET.length);

    for (let i = 0; i < count; i++) {
      const def = FLEET[i];
      const sensors = new Map<string, MockSensorState>();
      for (let s = 1; s <= sensorsPerGateway; s++) {
        sensors.set(`S${String(s).padStart(2, '0')}`, this.randomSensorState());
      }
      this.gateways.push({
        ...def,
        numericId: i + 1,
        connMode: Math.random() < 0.75 ? 'wifi' : 'sim',
        batteryGw: 55 + Math.random() * 45,
        pendingSync: 0,
        sensors,
      });
    }

    console.log(`[MockFleet] Iniciado — ${count} gateways x ${sensorsPerGateway} sensores, tick cada ${intervalMs}ms`);
    this.stopped = false;
    this.scheduleNextTick(0);
  }

  stop() {
    this.stopped = true;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }

  /**
   * setTimeout auto-programado (no setInterval): el siguiente tick solo se
   * agenda cuando el anterior terminó por completo. Con setInterval, un tick
   * lento (muchos sensores x varias llamadas a BD) se solapaba con el
   * siguiente y disparaba condiciones de carrera en el auto-registro
   * (duplicate key al crear el mismo sensor dos veces en paralelo).
   */
  private scheduleNextTick(delayMs: number) {
    if (this.stopped) return;
    this.timer = setTimeout(async () => {
      const start = Date.now();
      try {
        await this.tick();
      } catch (error) {
        console.error('[MockFleet] Error en tick:', error);
      }
      const elapsed = Date.now() - start;
      const { intervalMs } = configServer.mockFleet;
      this.scheduleNextTick(Math.max(0, intervalMs - elapsed));
    }, delayMs);
  }

  private randomSensorState(): MockSensorState {
    const tStart = 18 + Math.random() * 4;
    const tTarget = 1.5 + Math.random() * 2.5;
    const cycleTicks = 80 + Math.floor(Math.random() * 80);
    const battery = 55 + Math.random() * 45;
    return {
      temperature: tStart,
      tStart,
      tTarget,
      tick: Math.floor(Math.random() * cycleTicks),
      cycleTicks,
      battery,
      voltage: this.voltageForBattery(battery),
      silentTicksLeft: 0,
    };
  }

  /** Celda Li-ion típica: ~3.0V vacía, ~4.2V llena — correlacionada con el
   *  % de batería reportado, con un poco de ruido para que no sea una recta. */
  private voltageForBattery(batteryPct: number): number {
    return parseFloat((3.0 + (batteryPct / 100) * 1.2 + (Math.random() - 0.5) * 0.05).toFixed(3));
  }

  /** Identifica si un identifier corresponde a la flota simulada (para no
   *  publicar comandos falsos al broker real compartido — ver HU32-35). */
  isMockIdentifier(identifier: string): boolean {
    return FLEET.some(g => g.identifier === identifier);
  }

  /**
   * Simula el ciclo completo de calibración remota sin tocar el broker real:
   * el "dispositivo" tarda unos segundos en aplicar el cambio y confirma
   * (o, ocasionalmente, falla) — así HU32-35 se puede probar de punta a
   * punta en la app sin depender de firmware que todavía no existe.
   */
  simulateCalibrationAck(requestId: string) {
    const delayMs = 2000 + Math.random() * 6000;
    const status = Math.random() < 0.1 ? 'error' : 'ok';
    setTimeout(async () => {
      await execProcedure('iot.mark_calibration_ack', [{ request_id: requestId, status }]);
      console.log(`[MockFleet] Calibración ${status === 'ok' ? 'confirmada' : 'RECHAZADA'} (simulado) — request_id=${requestId}`);
    }, delayMs);
  }

  /**
   * Simula que el gateway recibe y aplica el nuevo tópico MQTT (HU19) — no
   * hay canal real de aprovisionamiento a un dispositivo físico en este
   * repo, así que un gateway real se queda 'pending' para siempre. Esta
   * flota sí puede fingir la latencia de campo (radio + reinicio del
   * dispositivo) y confirmar, para demostrar la HU completa.
   */
  simulateMqttTopicAck(requestId: string) {
    const delayMs = 3000 + Math.random() * 7000;
    const status = Math.random() < 0.08 ? 'error' : 'applied';
    setTimeout(async () => {
      await execProcedure('iot.mark_mqtt_topic_status', [{ request_id: requestId, status }]);
      console.log(`[MockFleet] Tópico MQTT ${status === 'applied' ? 'aplicado' : 'RECHAZADO'} (simulado) — request_id=${requestId}`);
    }, delayMs);
  }

  private stepSensor(s: MockSensorState) {
    s.tick++;
    if (s.tick >= s.cycleTicks) {
      // Reinicia el ciclo de enfriamiento/calentamiento con nuevos extremos
      s.tick = 0;
      s.tStart = 18 + Math.random() * 4;
      s.tTarget = 1.5 + Math.random() * 2.5;
    }

    const tau = s.cycleTicks * TAU_RATIO;
    const smooth = s.tTarget + (s.tStart - s.tTarget) * Math.exp(-s.tick / tau);
    let noise = (Math.random() - 0.5) * 0.6;

    // ~1% de probabilidad por tick de una lectura anómala — para que
    // RulesEngine/alertas tengan algo real que evaluar, no solo curvas lisas.
    if (Math.random() < 0.01) noise += (Math.random() < 0.5 ? -1 : 1) * (4 + Math.random() * 6);

    s.temperature = parseFloat(Math.max(-10, Math.min(30, smooth + noise)).toFixed(2));
    // La batería baja lentísimo — a esta cadencia tarda "días" en agotarse,
    // como un sensor real, no minutos.
    s.battery = parseFloat(Math.max(5, s.battery - Math.random() * 0.005).toFixed(2));
    s.voltage = this.voltageForBattery(s.battery);
    // ~0.5% de probabilidad de una caída de voltaje más marcada (batería
    // floja/vieja) — para poder ver el aviso "reemplazar batería pronto" en
    // la app sin esperar días a que la simulación la agote de verdad.
    if (Math.random() < 0.005) {
      s.voltage = parseFloat(Math.max(3.0, s.voltage - (0.2 + Math.random() * 0.3)).toFixed(3));
    }
  }

  private async tick() {
    // Un gateway a la vez internamente (evita la carrera de auto-registro
    // dentro del mismo gateway), pero todos los gateways en paralelo entre
    // sí (no comparten identifier, no hay colisión posible entre ellos).
    await Promise.all(this.gateways.map(gw => this.tickGateway(gw)));
  }

  private async tickGateway(gw: MockGatewayState) {
    // Cortes de conectividad breves y poco frecuentes — la mayoría de los
    // gateways se mantiene "en línea" casi todo el tiempo, con algún corte
    // ocasional que se recupera solo en un par de ticks (igual que un
    // gateway real con buena señal, no un sistema caído).
    if (gw.pendingSync === 0 && Math.random() < 0.015) {
      gw.pendingSync = 2 + Math.floor(Math.random() * 4);
    } else if (gw.pendingSync > 0 && Math.random() < 0.7) {
      gw.pendingSync = Math.max(0, gw.pendingSync - (1 + Math.floor(Math.random() * 3)));
    }
    gw.batteryGw = Math.max(5, gw.batteryGw - Math.random() * 0.003);

    for (const [slave, sensor] of gw.sensors) {
      if (sensor.silentTicksLeft > 0) {
        sensor.silentTicksLeft--;
        continue; // simulando sensor caído — no manda nada este tick
      }
      // ~0.2% de probabilidad por tick de quedarse callado 8-15 ticks
      // (2-4 min a 15s/tick) — suficiente para que WatchdogService
      // (umbral 90s) lo marque offline y, al volver, dispare la alerta
      // "sensor_recovered" real (HU8), no una fingida.
      if (Math.random() < 0.002) {
        sensor.silentTicksLeft = 8 + Math.floor(Math.random() * 8);
        continue;
      }

      this.stepSensor(sensor);

      const payload: TwarmPayload = {
        GatewayID: gw.numericId, // no lo usa ingestPayload (identifier viaja explícito), solo llena la forma del tipo
        Slave: slave,
        Tipo: 'temperature',
        T: sensor.temperature,
        TimeStamp: Math.floor(Date.now() / 1000),
        RSSI: -50 - Math.random() * 30,
        SNR: 5 + Math.random() * 10,
        Bs: sensor.battery,
        Bg: gw.batteryGw,
        V: sensor.voltage,
        ConnMode: gw.connMode,
        PendingSync: gw.pendingSync,
      };

      await mqttService.ingestPayload(gw.identifier, payload, { name: gw.name, location: gw.location });
    }
  }
}

export const mockFleetService = new MockFleetService();
