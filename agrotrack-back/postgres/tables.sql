-- =============================================================================
-- AGROTRACK - ANTIGRAVITY BACKEND
-- Esquema de Base de Datos PostgreSQL
-- Optimizado para telemetría IoT de alta frecuencia (lecturas cada 5s)
--
-- Nota: Para producción con alto volumen considerar:
--   1. TimescaleDB: CREATE EXTENSION timescaledb;
--                   SELECT create_hypertable('iot.sensor_readings', 'received_at');
--   2. Particionamiento nativo por rango de fecha (ver comentario al final)
-- =============================================================================

-- Extensiones
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schemas
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS iot;


-- =============================================================================
-- ENUMs
-- =============================================================================
DO $$ BEGIN CREATE TYPE iot.enum_sensor_type AS ENUM ('temperature','humidity','voltage','pressure','co2','other'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE iot.enum_alert_type  AS ENUM ('threshold_exceeded','sensor_offline','gateway_offline','other'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;


-- =============================================================================
-- SCHEMA: core
-- =============================================================================

CREATE TABLE IF NOT EXISTS core.roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    status      BOOLEAN DEFAULT TRUE,
    date_cr     BIGINT DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT
);

CREATE TABLE IF NOT EXISTS core.permissions (
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    slug    VARCHAR(100) NOT NULL UNIQUE,
    status  BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS core.users (
    id             SERIAL PRIMARY KEY,
    names          VARCHAR(200) NOT NULL,
    email          VARCHAR(200) NOT NULL UNIQUE,
    password_hash  TEXT NOT NULL,
    enable         BOOLEAN DEFAULT TRUE,
    role_id        INTEGER REFERENCES core.roles(id),
    date_cr        BIGINT DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT,
    date_up        BIGINT
);

CREATE TABLE IF NOT EXISTS core.role_permissions (
    role_id       INTEGER NOT NULL REFERENCES core.roles(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES core.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);


-- =============================================================================
-- SCHEMA: iot
-- =============================================================================

CREATE TABLE IF NOT EXISTS iot.gateways (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    identifier  VARCHAR(100) NOT NULL UNIQUE,
    location    VARCHAR(300),
    enable      BOOLEAN DEFAULT TRUE,
    api_key     TEXT NOT NULL UNIQUE,
    date_cr     BIGINT DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT,
    date_up     BIGINT
);

CREATE TABLE IF NOT EXISTS iot.sensors (
    id          SERIAL PRIMARY KEY,
    gateway_id  INTEGER NOT NULL REFERENCES iot.gateways(id),
    name        VARCHAR(200) NOT NULL,
    identifier  VARCHAR(100) NOT NULL,
    type        iot.enum_sensor_type NOT NULL DEFAULT 'temperature',
    unit        VARCHAR(20) DEFAULT '°C',
    location    VARCHAR(300),
    enable      BOOLEAN DEFAULT TRUE,
    date_cr     BIGINT DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT,
    date_up     BIGINT,
    UNIQUE (gateway_id, identifier)
);

-- Tabla principal de telemetría.
-- Alto volumen: ~17,280 filas/día por sensor (1 lectura cada 5s).
-- Para TimescaleDB: ejecutar después de crear la tabla:
--   SELECT create_hypertable('iot.sensor_readings', 'received_at');
CREATE TABLE IF NOT EXISTS iot.sensor_readings (
    id          BIGSERIAL PRIMARY KEY,
    sensor_id   INTEGER NOT NULL REFERENCES iot.sensors(id),
    gateway_id  INTEGER NOT NULL REFERENCES iot.gateways(id),
    temperature NUMERIC(6,2),
    voltage     NUMERIC(6,3),
    battery     NUMERIC(5,2),
    extra_data  JSONB,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS iot.thresholds (
    id            SERIAL PRIMARY KEY,
    sensor_id     INTEGER NOT NULL REFERENCES iot.sensors(id),
    user_id       INTEGER REFERENCES core.users(id),
    metric        VARCHAR(50) NOT NULL DEFAULT 'temperature',
    min_value     NUMERIC(10,3),
    max_value     NUMERIC(10,3),
    alert_message TEXT,
    enable        BOOLEAN DEFAULT TRUE,
    date_cr       BIGINT DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT,
    date_up       BIGINT,
    UNIQUE (sensor_id, user_id, metric)
);

CREATE TABLE IF NOT EXISTS iot.alerts (
    id          BIGSERIAL PRIMARY KEY,
    sensor_id   INTEGER NOT NULL REFERENCES iot.sensors(id),
    gateway_id  INTEGER NOT NULL REFERENCES iot.gateways(id),
    type        iot.enum_alert_type NOT NULL DEFAULT 'threshold_exceeded',
    metric      VARCHAR(50),
    value       NUMERIC(10,3),
    threshold   NUMERIC(10,3),
    message     TEXT NOT NULL,
    resolved    BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);


-- =============================================================================
-- ÍNDICES
-- =============================================================================

-- Telemetría: acceso por sensor + tiempo (query más frecuente)
CREATE INDEX IF NOT EXISTS idx_readings_sensor_time
    ON iot.sensor_readings (sensor_id, received_at DESC);

-- Telemetría: acceso por gateway + tiempo (dashboard)
CREATE INDEX IF NOT EXISTS idx_readings_gateway_time
    ON iot.sensor_readings (gateway_id, received_at DESC);

-- Alertas activas
CREATE INDEX IF NOT EXISTS idx_alerts_active
    ON iot.alerts (resolved, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_sensor
    ON iot.alerts (sensor_id, created_at DESC);

-- Umbrales habilitados por sensor
CREATE INDEX IF NOT EXISTS idx_thresholds_sensor
    ON iot.thresholds (sensor_id, enable);


-- =============================================================================
-- SEEDS (roles y permisos base)
-- =============================================================================

INSERT INTO core.roles (name, description) VALUES
    ('Administrador', 'Acceso total al sistema'),
    ('Operador',      'Monitoreo y gestión de sensores'),
    ('Auditor',       'Solo lectura de reportes y alertas')
ON CONFLICT (name) DO NOTHING;

INSERT INTO core.permissions (name, slug) VALUES
    ('Ver sensores',        'iot.view_sensors'),
    ('Gestionar sensores',  'iot.manage_sensors'),
    ('Ver telemetría',      'iot.view_telemetry'),
    ('Ver alertas',         'iot.view_alerts'),
    ('Resolver alertas',    'iot.resolve_alerts'),
    ('Gestionar umbrales',  'iot.manage_thresholds'),
    ('Ver reportes',        'iot.view_reports'),
    ('Gestionar gateways',  'iot.manage_gateways'),
    ('Gestionar usuarios',  'admin.manage_users'),
    ('Gestionar roles',     'admin.manage_roles')
ON CONFLICT (slug) DO NOTHING;
