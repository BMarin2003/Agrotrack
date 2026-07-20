---
title: Resultados de pruebas
description: Cobertura unitaria, de caja blanca y de caja negra del backend, con resultados reales de ejecución.
---

## Resumen ejecutivo

| Plataforma | Unitarias | Caja blanca | Caja negra | Total casos |
|---|---|---|---|---|
| Backend | 231 | 9 (Tasks 8-9) | 59 (Tasks 2-7) | 299 |
| Android | pendiente — ver plan `2026-07-20-pruebas-android.md` | pendiente | pendiente | pendiente |

Nota: para unidades sin ramas internas relevantes, caja blanca y unitarias son
los mismos casos justificados desde dos ángulos distintos — no se cuentan dos
veces en la tabla de arriba salvo que un archivo esté dedicado explícitamente
a cobertura de rama (Tasks 8 y 9 de este plan).

**Resultado final de la suite completa:** `299 pass, 0 fail` (27 archivos de test en total, incluyendo unitarias, caja negra y caja blanca).

## Backend — caja negra HTTP (contra la app Elysia real + Supabase real)

| Módulo | Archivo de test | Casos | Comando | Resultado |
|---|---|---|---|---|
| Autenticación | `tests/auth.blackbox.test.ts` | 11 | `bun test tests/auth.blackbox.test.ts` | 11 pass |
| Usuarios | `tests/users.blackbox.test.ts` | 6 | `bun test tests/users.blackbox.test.ts` | 6 pass |
| Sensores/alias/calibración | `tests/sensors.blackbox.test.ts` | 10 | `bun test tests/sensors.blackbox.test.ts` | 10 pass |
| Gateways/wifi/pin/mqtt/mantenimiento | `tests/gateways.blackbox.test.ts` | 9 | `bun test tests/gateways.blackbox.test.ts` | 9 pass |
| Umbrales + Alertas | `tests/thresholds.blackbox.test.ts`, `tests/alerts.blackbox.test.ts` | 11 | `bun test tests/thresholds.blackbox.test.ts tests/alerts.blackbox.test.ts` | 11 pass |
| Reportes + Mesa de ayuda + Telemetría | `tests/reports.blackbox.test.ts`, `tests/helpdesk.blackbox.test.ts`, `tests/telemetry.blackbox.test.ts` | 12 | `bun test tests/reports.blackbox.test.ts tests/helpdesk.blackbox.test.ts tests/telemetry.blackbox.test.ts` | 12 pass |

**Total caja negra:** 59 pass

### Nota sobre test fallando (expected failure)

El archivo `tests/thresholds.blackbox.test.ts` contiene UN test marcado como `it.failing(...)` (no un `it(...)` normal):

```
it.failing("un operador NO puede borrar un umbral de OTRO operador (caja negra del fix de esta sesión)", ...)
```

Este test **es correcto y funciona como se espera** — está marcado como fallido esperado porque la migración `014_threshold_delete_ownership.sql` (el fix real que implementa la restricción de propiedad del umbral) aún no ha sido aplicada a la base de datos Supabase en vivo que usa la suite.

El comentario en el código dice exactamente:
```
// TODO: revert to it(...) once migration 014_threshold_delete_ownership.sql is applied to the live Supabase database
```

**Acción requerida:** Cuando un administrador aplique la migración 014 en el SQL Editor de la base de datos, este test debe cambiar de `it.failing(...)` a `it(...)` (eliminando el `.failing`), momento en el cual pasará como un test normal.

## Backend — caja blanca (cobertura de rama)

| Archivo | Qué rama cubre | Casos | Resultado |
|---|---|---|---|
| `tests/rules.engine.dedup.test.ts` | Deduplicación de alertas (primera vez / ya activa / resuelta / métricas independientes) — lógica agregada en `rules.engine.ts` esta sesión | 4 | 4 pass |
| `tests/watchdog.dedup.test.ts` | Deduplicación de alertas de recuperación tras offline | 2 | 2 pass |
| `tests/gateway.service.step.test.ts` | Transición de estado térmico interno: progreso hacia un valor objetivo, reinicio de ciclo, límites físicos configurados (clamp) | 3 | 3 pass |

**Total caja blanca:** 9 pass

## Exclusiones documentadas

- `DELETE /alerts/clear` — solo se prueban 401 sin token; el camino de éxito (200) borraría TODAS las alertas reales de la base de datos, usada activamente para demos. No se ejecuta.
- `POST /telemetry/ingest` — solo se prueban los casos negativos (401 sin/con API key inválida); un ingest exitoso requeriría la API key real de un gateway físico y escribiría una lectura sintética fuera del flujo normal.
- `PUT /auth/update-password` (camino de éxito) — cambiaría la contraseña real de una de las 3 cuentas sembradas que el resto de la suite depende tener estables.

## Mantenimiento — limpieza de fixtures sintéticos

La suite de caja negra crea gateways, sensores y tickets sintéticos reales en
la base de datos Supabase compartida (usada activamente como demo) cada vez
que se ejecuta: `tests/gateways.blackbox.test.ts` y
`tests/thresholds.blackbox.test.ts` crean un gateway (y, en el segundo caso,
también un sensor) en su `beforeAll`, y `tests/helpdesk.blackbox.test.ts` deja
tickets reales creados durante sus casos. No existe un endpoint
`DELETE /sensors/gateways/:id` en la API, por lo que estos registros no se
pueden borrar desde la propia aplicación y se van acumulando con cada corrida
repetida de la suite. Todos los fixtures están claramente prefijados
(`test-cn-*`, `test-thresholds-*`, tickets con subject `"Ticket de prueba..."`
o `"Ticket para probar..."`) y nunca se confunden con datos reales, pero de
todas formas conviene limpiarlos periódicamente. Para eso existe
`agrotrack-back/scripts/purge-test-fixtures.ts`: ejecutar
`bun run scripts/purge-test-fixtures.ts` de forma manual cada cierto tiempo
(por ejemplo, antes de una demo o presentación) para borrar los fixtures
sintéticos acumulados. Este script no se ejecuta automáticamente ni forma
parte de CI, siguiendo la convención del proyecto de scripts de mantenimiento
manuales.

## Fuera de alcance (responsabilidad del usuario, no de este documento)

- Pruebas en equipos físicos.
- Pruebas con usuarios finales.
