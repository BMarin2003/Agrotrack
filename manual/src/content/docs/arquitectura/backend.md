---
title: Backend — stack y justificación
description: Runtime, framework HTTP y capa de datos de agrotrack-back.
sidebar:
  order: 1
---

## Bun como runtime

El backend corre sobre **Bun** (`bun 1.3.x`) en vez de Node.js. Motivos concretos para este proyecto:

- **TypeScript nativo sin paso de build en desarrollo** — `bun run --watch src/index.ts` ejecuta `.ts` directamente; no hay `tsc`/`ts-node`/`nodemon` de por medio en el ciclo de desarrollo, solo se usa `tsc --noEmit` como *type-checker* puro (`package.json:12`).
- **Test runner integrado** (`bun test`) — evita sumar Jest/Vitest como dependencia aparte; toda la suite de pruebas del proyecto corre con la misma herramienta que ya trae el runtime.
- **Arranque en frío rápido** — relevante para un servicio que recibe telemetría IoT de forma continua y necesita reinicios ágiles durante desarrollo/despliegue.

## ElysiaJS como framework HTTP

Elysia (`elysia 1.4.x`) fue elegido sobre alternativas como Express o Fastify por:

- **Validación de esquema de extremo a extremo con TypeBox** (`t.Object({...})` en cada ruta) — el tipo de `body`/`query` se valida en runtime y se infiere en compile-time simultáneamente, sin duplicar la definición en un DTO aparte.
- **Sistema de plugins componible** (`.use(authPlugin)`) — el guard de autenticación/autorización (`core/auth.guard.ts`) se inyecta como plugin reusable en cada grupo de rutas, en vez de middleware imperativo repetido.
- **Rendimiento** — Elysia está diseñado sobre el router nativo de Bun, relevante para un servicio que en producción recibirá ráfagas de mensajes MQTT que se traducen en escrituras HTTP internas equivalentes.

## Supabase vía RPC/PostgREST, no un ORM tradicional

Toda la persistencia pasa por **procedimientos PL/pgSQL** invocados vía `execProcedure(nombre, [params])` (`core/db/connection.ts`), que internamente llama `supabaseAdmin.schema(schema).rpc(fn, {p_data})` — nunca hay una conexión TCP directa a Postgres ni un ORM (Prisma/Drizzle/TypeORM) de por medio.

Por qué esta decisión:

- **La lógica de negocio vive en la base de datos, no dispersa en el backend** — agregaciones (reportes por gateway/general), upserts con `ON CONFLICT`, y reglas de scoping por operador se resuelven en SQL, con transaccionalidad garantizada por Postgres en vez de reconstruida a mano en TypeScript.
- **Un solo contrato `p_data JSON` de entrada/salida** — cada función recibe un JSON y devuelve JSON (`JSON_BUILD_OBJECT`/`JSON_AGG`), lo que hace que agregar un campo nuevo sea un cambio de migración + un cambio de tipo TypeScript, sin tocar un ORM intermedio.
- **Migraciones versionadas y numeradas** (`agrotrack-back/migrations/`) — cada cambio de esquema es un archivo `.sql` inmutable una vez aplicado, aplicado manualmente contra Supabase; el historial de migraciones documenta la evolución del modelo de datos igual que lo haría un ORM con migraciones generadas, pero con SQL explícito y auditable.

## Cliente MQTT

La librería `mqtt` (cliente MQTT.js) conecta el backend al broker como único suscriptor de la telemetría de los gateways. Se eligió por ser el cliente MQTT de referencia en el ecosistema Node/Bun, con soporte QoS, reconexión automática y manejo de eventos por callback que encaja naturalmente con el modelo de eventos de `EventEmitter` ya usado en el resto del backend.

## Estructura de módulos

```
agrotrack-back/src/
├── core/                 # transversal: auth, permisos, conexión a BD, JWT
│   ├── auth.guard.ts
│   ├── permissions.constants.ts
│   └── db/connection.ts
├── modules/
│   ├── core/             # auth.api.ts, users.api.ts
│   └── iot/               # sensors.api.ts, reports.api.ts, thresholds.api.ts,
│                           # helpdesk.api.ts, telemetry.api.ts, telemetry.ws.ts
├── services/              # lógica de dominio sin HTTP: mqtt.service.ts,
│                           # rules.engine.ts, watchdog.service.ts
├── config.ts
└── run-server.ts
```

**Convención de nomenclatura**: cada módulo HTTP se llama `<dominio>.api.ts` y exporta una constante `<Dominio>Api` (`ReportsApi`, `ThresholdsApi`) — un `Elysia()` con su propio `.group(path, ...)`, montado en `router.ts`. La lógica que no depende de HTTP (ingesta MQTT, motor de reglas, watchdog) vive en `services/` con sufijo `.service.ts` y se expone como una instancia singleton exportada (`export const mqttService = new MqttService()`), no como clase inyectada — decisión consciente para mantener el backend sin un contenedor de DI, dado su tamaño.

Los nombres de columnas/parámetros SQL usan `snake_case` (convención Postgres); los DTOs de respuesta HTTP preservan ese mismo `snake_case` en el JSON de salida (`sensor_id`, `gateway_id`, `received_at`) — la traducción a `camelCase` ocurre recién en el cliente Android, en la capa de DTO (`@SerializedName`), manteniendo el backend fiel a la convención nativa de su base de datos.

## Resumen de decisiones

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| Bun | Node.js + ts-node | TS nativo, test runner integrado, arranque más rápido |
| ElysiaJS | Express/Fastify | Validación de esquema end-to-end, plugins componibles |
| RPC/PostgREST | ORM (Prisma/Drizzle) | Lógica de negocio y transaccionalidad en la BD, migraciones SQL explícitas |
