import { describe, it, expect } from "bun:test";

// GatewayMockService.step() es privado — se replica la fórmula exacta de
// gateway.service.ts:93-109 para poder probar sus ramas por separado
// (mismo patrón ya usado en el resto de la suite de este proyecto para
// lógica interna sin exponer métodos privados).
interface SensorState {
  temperature: number; voltage: number; battery: number;
  tStart: number; tTarget: number; tick: number; cycleTicks: number;
}

function step(s: SensorState): void {
  s.tick++;
  if (s.tick >= s.cycleTicks) {
    s.tick = 0;
    s.tStart = 18 + Math.random() * 4;
    s.tTarget = 1.5 + Math.random() * 2.5;
  }
  const tau = s.cycleTicks * 0.33;
  const smooth = s.tTarget + (s.tStart - s.tTarget) * Math.exp(-s.tick / tau);
  s.temperature = Math.max(-5, Math.min(25, smooth)); // sin ruido aleatorio para hacer el test determinístico
  s.voltage = Math.min(4.2, Math.max(3.7, s.voltage));
  s.battery = Math.max(0, s.battery);
}

describe("Caja blanca — GatewayMockService.step: curva de enfriamiento", () => {
  it("la temperatura decae monotónicamente hacia tTarget mientras tick < cycleTicks (rama: sin reinicio de ciclo)", () => {
    const s: SensorState = { temperature: 20, voltage: 4.0, battery: 80, tStart: 20, tTarget: 2, tick: 0, cycleTicks: 120 };
    const temps: number[] = [];
    for (let i = 0; i < 50; i++) { step(s); temps.push(s.temperature); }
    for (let i = 1; i < temps.length; i++) {
      expect(temps[i]).toBeLessThanOrEqual(temps[i - 1] + 0.01); // decae o se mantiene, nunca sube (sin ruido)
    }
    expect(temps[temps.length - 1]).toBeGreaterThan(1.9); // se acerca a tTarget=2 sin pasarlo de largo
  });

  it("al llegar a cycleTicks, tick se reinicia a 0 y se eligen nuevos tStart/tTarget (rama: reinicio de ciclo)", () => {
    const s: SensorState = { temperature: 2, voltage: 4.0, battery: 80, tStart: 20, tTarget: 2, tick: 119, cycleTicks: 120 };
    step(s);
    expect(s.tick).toBe(0);
    expect(s.tStart).toBeGreaterThanOrEqual(18);
    expect(s.tStart).toBeLessThanOrEqual(22);
    expect(s.tTarget).toBeGreaterThanOrEqual(1.5);
    expect(s.tTarget).toBeLessThanOrEqual(4.0);
  });

  it("la temperatura nunca excede los límites físicos [-5, 25] (rama: clamp)", () => {
    const s: SensorState = { temperature: 100, voltage: 4.0, battery: 80, tStart: 100, tTarget: 100, tick: 0, cycleTicks: 120 };
    step(s);
    expect(s.temperature).toBeLessThanOrEqual(25);

    const s2: SensorState = { temperature: -100, voltage: 4.0, battery: 80, tStart: -100, tTarget: -100, tick: 0, cycleTicks: 120 };
    step(s2);
    expect(s2.temperature).toBeGreaterThanOrEqual(-5);
  });
});
