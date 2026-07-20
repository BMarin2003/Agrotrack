import { describe, it, expect, beforeEach, afterEach, mock, spyOn } from "bun:test";
import { rulesEngine } from "../src/services/rules.engine";
// Se importa el módulo real (no vía el alias "@core/db/connection") para
// espiar `execProcedure` con spyOn y poder restaurarlo con precisión vía
// `.mockRestore()` después de cada test. Nota: se probó primero con
// mock.module("@core/db/connection", ...), pero esa API reemplaza el
// módulo en el registro global de Bun para el resto del proceso — `bun
// test` corre todos los archivos en el mismo proceso, así que un
// mock.module aquí seguía filtrándose a archivos de caja negra que se
// ejecutan después (alfabéticamente) y necesitan la BD real, incluso
// intentando "restaurarlo" con otro mock.module en afterEach (se observó
// empíricamente: 10 tests de otros archivos fallaban con 401/404/422
// hasta cambiar a este enfoque). spyOn + mockRestore() sí deshace la
// sustitución de forma confiable porque solo muta la función en el objeto
// del módulo real, sin tocar el registro de resolución de módulos.
import * as connectionModule from "../src/core/db/connection";

describe("Caja blanca — RulesEngine.evaluate: deduplicación de alertas por umbral", () => {
  let triggerCalls: any[];

  beforeEach(() => {
    triggerCalls = [];
    // Se reemplaza triggerAlert por un espía que solo registra la llamada,
    // sin tocar la BD real — aísla la rama de decisión "¿ya está activa la
    // condición?" del efecto de persistencia.
    spyOn(rulesEngine, "triggerAlert").mockImplementation(async (alert: any) => {
      triggerCalls.push(alert);
    });
    // Se limpia el estado interno de umbrales activos entre tests para que
    // no haya fuga de estado de un caso a otro (mismo sensor_id reutilizado).
    (rulesEngine as any).activeBreaches = new Set<string>();
    (rulesEngine as any).lastTemperatures = new Map<number, number>();
    (rulesEngine as any).anomalyWindows = new Map<number, number[]>();
  });

  afterEach(() => {
    mock.restore();
  });

  it("primera lectura que rompe un umbral dispara UNA alerta (rama: breached && !activeBreaches.has(key))", async () => {
    spyOn(connectionModule, "execProcedure").mockImplementation(async () => ({
      error: null,
      result: [{ id: 1, user_id: 5, metric: "temperature", min_value: null, max_value: 8, alert_message: null }],
    }));

    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 12 });
    expect(triggerCalls.length).toBe(1);
    expect(triggerCalls[0].type).toBe("threshold_exceeded");
  });

  it("lecturas sucesivas que siguen rompiendo el MISMO umbral NO re-disparan (rama: breached && activeBreaches.has(key))", async () => {
    spyOn(connectionModule, "execProcedure").mockImplementation(async () => ({
      error: null,
      result: [{ id: 1, user_id: 5, metric: "temperature", min_value: null, max_value: 8, alert_message: null }],
    }));

    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 12 });
    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 13 });
    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 14 });

    expect(triggerCalls.length).toBe(1); // solo la primera dispara
  });

  it("la condición se resuelve y se vuelve a romper dispara una SEGUNDA alerta (rama: !breached borra la key)", async () => {
    spyOn(connectionModule, "execProcedure").mockImplementation(async () => ({
      error: null,
      result: [{ id: 1, user_id: 5, metric: "temperature", min_value: null, max_value: 8, alert_message: null }],
    }));

    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 12 }); // rompe -> dispara (1)
    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 4 });  // se resuelve -> borra key
    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 12 }); // rompe de nuevo -> dispara (2)

    expect(triggerCalls.length).toBe(2);
  });

  it("dos métricas distintas del mismo sensor mantienen breaches independientes (rama: key incluye metric)", async () => {
    spyOn(connectionModule, "execProcedure").mockImplementation(async () => ({
      error: null,
      result: [
        { id: 1, user_id: 5, metric: "temperature", min_value: null, max_value: 8, alert_message: null },
        { id: 2, user_id: 5, metric: "battery", min_value: 20, max_value: null, alert_message: null },
      ],
    }));

    await rulesEngine.evaluate({ sensor_id: 42, gateway_id: 1, temperature: 12, battery: 5 });
    expect(triggerCalls.length).toBe(2); // temperature Y battery rompen en la misma lectura
    expect(triggerCalls.map((c) => c.metric).sort()).toEqual(["battery", "temperature"]);
  });
});
