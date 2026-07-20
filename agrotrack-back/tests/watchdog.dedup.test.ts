import { describe, it, expect, beforeEach, afterEach, spyOn, mock } from "bun:test";
import * as connectionModule from "../src/core/db/connection";
import * as wsModule from "../src/modules/iot/telemetry.ws";
import { watchdogService } from "../src/services/watchdog.service";

// Nota sobre el umbral: la idea original de este archivo era bajar
// `OFFLINE_THRESHOLD_MS` seteando `process.env.WATCHDOG_OFFLINE_MS` antes
// del import de `watchdog.service.ts` (esa constante se lee una sola vez al
// cargar el módulo). Se comprobó empíricamente que eso NO funciona de forma
// confiable dentro de la suite completa: `bun test` comparte un único
// registro de módulos para TODO el proceso (la misma razón por la que
// `mock.module()` filtraba entre archivos, según se documentó en
// `rules.engine.dedup.test.ts`), así que si otro archivo de test importa
// `watchdog.service.ts` primero (p. ej. un blackbox test que arranca rutas
// reales), el módulo ya queda cacheado con el default de 90000ms y el env
// var de este archivo llega tarde — sea con import estático o dinámico.
//
// En vez de depender del valor de `OFFLINE_THRESHOLD_MS` (desconocido en
// runtime porque no está exportado), se usa `spyOn(Date, "now")` para
// "adelantar el reloj" SOLO durante el heartbeat inicial de nuestro sensor
// sintético, dejando su `last_seen` con una marca de tiempo muy antigua.
// `check()` corre después con el reloj real, así que únicamente el
// `silentMs` de nuestro sensor supera cualquier umbral posible (el default
// de 90000ms incluido); los demás sensores que otros archivos ya hayan
// registrado en el mismo Map compartido conservan su `last_seen` real y no
// se ven afectados — evita introducir una nueva fuga de estado entre
// archivos.
describe("Caja blanca — WatchdogService: rama de heartbeat tras estar offline", () => {
  let saveAlertCalls: any[];

  beforeEach(() => {
    saveAlertCalls = [];
    spyOn(connectionModule, "execProcedure").mockImplementation(async (proc: string, args: any) => {
      if (proc === "iot.save_alert") saveAlertCalls.push(args[0]);
      return { result: { id: 1 } };
    });
    spyOn(wsModule, "broadcastToAll").mockImplementation(() => {});
    spyOn(wsModule, "broadcastToGateway").mockImplementation(() => {});
  });

  afterEach(() => {
    mock.restore();
  });

  it("heartbeat de un sensor que NUNCA estuvo offline no dispara alerta de recuperación (rama: !offlineSensors.has(id))", async () => {
    const sensorId = 900001; // id sintético, no colisiona con sensores reales
    await watchdogService.heartbeat(sensorId, 1);
    expect(saveAlertCalls.length).toBe(0);
  });

  it("tras superar el umbral de offline sin heartbeat, check() marca offline y el siguiente heartbeat dispara sensor_recovered (rama: offlineSensors.has(id))", async () => {
    const sensorId = 900002;

    // Se registra el heartbeat inicial con un last_seen muy antiguo (10
    // minutos atrás), sin esperar tiempo real y sin tocar el last_seen de
    // ningún otro sensor ya presente en el Map compartido.
    const pastTimestamp = Date.now() - 10 * 60 * 1000;
    const nowSpy = spyOn(Date, "now").mockReturnValue(pastTimestamp);
    await watchdogService.heartbeat(sensorId, 1);
    nowSpy.mockRestore();

    await watchdogService.check(); // reloj real: silentMs (~10 min) supera cualquier OFFLINE_THRESHOLD_MS posible

    await watchdogService.heartbeat(sensorId, 1);
    expect(saveAlertCalls.some((a) => a.type === "sensor_recovered" && a.sensor_id === sensorId)).toBe(true);
  });
});
