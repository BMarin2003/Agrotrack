---
title: Desplegar el backend
description: Cómo poner agrotrack-back en producción — base de datos, variables de entorno, bróker MQTT y el contenedor Docker existente.
sidebar:
  order: 1
---

Esta guía asume un servidor Linux con Docker instalado y un dominio con registro DNS apuntando a ese servidor. El dominio de referencia usado en el resto de este manual (y ya configurado en el build de release de la app Android) es `api.agrotrack.corall.pe` — si se usa un dominio distinto, ver la nota correspondiente en [Compilar y distribuir la app Android](/despliegue/app-android/).

## Base de datos — Supabase

1. Crear un proyecto nuevo en [Supabase](https://supabase.com).
2. Abrir el **SQL Editor** del proyecto y ejecutar, en orden numérico, cada archivo de `agrotrack-back/migrations/`:

```
001_initial_schema.sql
002_wifi_calibration_maintenance.sql
003_gateway_pin.sql
004_operator_scoping.sql
005_gateway_battery.sql
006_threshold_enable_persistence.sql
007_dashboard_alias_and_maintenance.sql
008_helpdesk_tickets.sql
009_calibration_ack.sql
010_gateway_online_status.sql
011_gateway_mqtt_topic.sql
012_gateway_sensor_count.sql
013_gateway_general_reports.sql
014_threshold_delete_ownership.sql
015_gateway_wifi_password.sql
```

Es un proceso manual — no hay una herramienta de migración automática (tipo ORM) que las aplique por sí sola. Cada archivo debe pegarse y ejecutarse completo, en este orden, una sola vez.

## Variables de entorno

Crear un archivo `.env.production` (nunca comiteado al repositorio) con estas variables, agrupadas igual que `agrotrack-back/example.env`:

```
NODE_ENV=production
PORT=3000

# ─── Supabase PostgreSQL (Session Pooler — IPv4) ─────────────────────────────
# Supabase Dashboard → Settings → Database → Session Pooler
DB_HOST=aws-0-<region>.pooler.supabase.com
DB_PORT=5432
DB_NAME=postgres
DB_USER=postgres.<project-ref>
DB_PASSWORD=<password-de-bd-de-supabase>
DB_MAX_POOL_SIZE=10
DB_SSL=true

# ─── Supabase API ─────────────────────────────────────────────────────────────
# Supabase Dashboard → Settings → API
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=sb_publishable_...
SUPABASE_SERVICE_ROLE_KEY=sb_secret_...

# ─── Auth ─────────────────────────────────────────────────────────────────────
JWT_SECRET=<secreto-largo-y-aleatorio-propio>
JWT_EXPIRE_IN=604800

# ─── MQTT ──────────────────────────────────────────────────────────────────────
MQTT_ENABLED=true
MQTT_BROKER_URL=mqtt://<host-del-broker>:1883
MQTT_USERNAME=<usuario-del-broker>
MQTT_PASSWORD=<password-del-broker>
MQTT_CLIENT_ID=agrotrack-server
MQTT_TOPIC_SUBSCRIBE=
MQTT_GATEWAY_UID=

TEAM_NAME=Corall D&R
TZ=America/Lima
```

`JWT_SECRET` debe ser un valor propio, largo y aleatorio — nunca el `change_me_in_production` de ejemplo. `DB_PASSWORD`/`SUPABASE_*` se obtienen del panel de Supabase creado en el paso anterior.

## Bróker MQTT

El backend no incluye un bróker MQTT — es el único suscriptor, no el servidor. Se necesita uno propio. La forma más simple es correr [Eclipse Mosquitto](https://mosquitto.org/) en un contenedor Docker aparte:

```bash
docker run -d --name mosquitto \
  -p 1883:1883 \
  -v mosquitto-data:/mosquitto/data \
  eclipse-mosquitto:2
```

Para producción, configurar autenticación (usuario/contraseña) siguiendo la [documentación de Mosquitto](https://mosquitto.org/documentation/) y usar esas credenciales en `MQTT_USERNAME`/`MQTT_PASSWORD` del `.env.production`.

## Build y ejecución del contenedor

El repo ya incluye un `Dockerfile` en `agrotrack-back/`:

```bash
cd agrotrack-back
docker build -t agrotrack-back:latest .
docker run -d --name agrotrack-back \
  -p 3000:3000 \
  --env-file .env.production \
  agrotrack-back:latest
```

El contenedor expone el puerto 3000. Delante de él va un reverse proxy con TLS — la app Android usa `wss://` en producción, así que un certificado válido no es opcional. Con [Caddy](https://caddyserver.com/) (TLS automático vía Let's Encrypt, sin configuración manual de certificados), el `Caddyfile` completo es:

```
api.agrotrack.corall.pe {
    reverse_proxy localhost:3000
}
```

(Nginx con Certbot es una alternativa equivalente si ya se usa en la infraestructura existente.)

## Primer usuario administrador

No hay un flujo de aprovisionamiento de administrador pensado para producción — el único mecanismo que existe hoy es el script de seed, pensado originalmente para pruebas:

```bash
cd agrotrack-back
bun install
bun run scripts/seed.ts
```

Esto crea 3 cuentas con contraseñas fijas y conocidas (`Operador2024!`, `Tecnico2024!`, `Admin2024!`). **Paso obligatorio inmediatamente después**: cambiar las 3 contraseñas antes de dar el sistema por disponible. La app no tiene todavía una pantalla para esto, así que se hace directamente contra la API:

```bash
# 1. Login para obtener el token
curl -X POST https://api.agrotrack.corall.pe/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@agrotrack.com","password":"Admin2024!"}'
# copiar el valor de "token" de la respuesta

# 2. Cambiar la contraseña con ese token
curl -X PUT https://api.agrotrack.corall.pe/api/auth/update-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token-copiado>" \
  -d '{"currentPassword":"Admin2024!","newPassword":"<contraseña-nueva-de-al-menos-8-caracteres>"}'
```

Repetir para las 3 cuentas (`operador@agrotrack.com`, `tecnico@agrotrack.com`, `admin@agrotrack.com`).

## Verificación

- Repetir el comando de login (`POST /api/auth/login`) con la contraseña ya cambiada debe devolver un token (200).
- Iniciar sesión desde la app Android (ver [Compilar y distribuir la app Android](/despliegue/app-android/)) y confirmar que el Dashboard carga.
- Si hay un gateway físico transmitiendo, confirmar que su lectura llega en tiempo real al Dashboard (WebSocket funcionando de extremo a extremo).
