-- =============================================================================
-- AGROTRACK — Migración completa para Supabase
--
-- INSTRUCCIONES:
--   1. Ir a Supabase Dashboard → SQL Editor
--   2. Pegar este archivo completo y ejecutar (Run)
--   3. Ir a Supabase Dashboard → Project Settings → API
--      En "Exposed schemas" añadir:  core, iot
--      (sin esto, el backend no puede llamar los stored procedures vía REST)
--   4. Ejecutar: bun run seed
-- =============================================================================


-- ─── EXTENSIONES ──────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- requerida por iot.save_gateway


-- ─── SCHEMAS ──────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS iot;


-- ─── ENUMs ────────────────────────────────────────────────────────────────────
DO $$ BEGIN
  CREATE TYPE iot.enum_sensor_type AS ENUM ('temperature','humidity','voltage','pressure','co2','other');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE iot.enum_alert_type AS ENUM ('threshold_exceeded','sensor_offline','gateway_offline','other');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;


-- =============================================================================
-- TABLAS: core
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
-- TABLAS: iot
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


-- ─── ÍNDICES ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_readings_sensor_time  ON iot.sensor_readings (sensor_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_readings_gateway_time ON iot.sensor_readings (gateway_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_active         ON iot.alerts (resolved, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_sensor         ON iot.alerts (sensor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_thresholds_sensor     ON iot.thresholds (sensor_id, enable);


-- =============================================================================
-- PROCEDIMIENTOS: core.auth
-- =============================================================================

CREATE OR REPLACE FUNCTION core.get_user_credentials(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT('id', u.id, 'password_hash', u.password_hash, 'enable', u.enable)
    INTO v_result FROM core.users u WHERE u.email = p_data->>'email' LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.get_user_login_data(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id',    u.id,
        'names', u.names,
        'email', u.email,
        'roles', COALESCE((
            SELECT JSON_AGG(JSON_BUILD_OBJECT('id', r.id, 'name', r.name))
            FROM core.roles r WHERE r.id = u.role_id
        ), '[]'::JSON),
        'permisos', COALESCE((
            SELECT JSON_AGG(p.slug)
            FROM core.role_permissions rp
            JOIN core.permissions p ON p.id = rp.permission_id
            WHERE rp.role_id = u.role_id
        ), '[]'::JSON)
    ) INTO v_result
    FROM core.users u
    WHERE u.id = (p_data->>'id')::INTEGER AND u.enable = TRUE;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.update_user_password(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE core.users SET
        password_hash = p_data->>'password_hash',
        date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'id')::INTEGER;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.list_users(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', u.id, 'names', u.names, 'email', u.email,
        'enable', u.enable, 'role_id', u.role_id, 'role', r.name
    ) ORDER BY u.id) INTO v_result
    FROM core.users u LEFT JOIN core.roles r ON r.id = u.role_id;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.save_user(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    IF p_data->>'id' IS NOT NULL THEN
        UPDATE core.users SET
            names         = COALESCE(p_data->>'names',                   names),
            email         = COALESCE(p_data->>'email',                   email),
            password_hash = COALESCE(p_data->>'password_hash',           password_hash),
            enable        = COALESCE((p_data->>'enable')::BOOLEAN,       enable),
            role_id       = COALESCE((p_data->>'role_id')::INTEGER,      role_id),
            date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
        WHERE id = (p_data->>'id')::INTEGER RETURNING id INTO v_id;
    ELSE
        INSERT INTO core.users (names, email, password_hash, role_id)
        VALUES (p_data->>'names', p_data->>'email', p_data->>'password_hash', (p_data->>'role_id')::INTEGER)
        RETURNING id INTO v_id;
    END IF;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.delete_user(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE core.users SET enable = FALSE WHERE id = (p_data->>'id')::INTEGER;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- PROCEDIMIENTOS: iot
-- =============================================================================

CREATE OR REPLACE FUNCTION iot.list_gateways(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', g.id, 'name', g.name, 'identifier', g.identifier,
        'location', g.location, 'enable', g.enable
    ) ORDER BY g.id) INTO v_result FROM iot.gateways g;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_gateway_by_identifier(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT('id', g.id, 'name', g.name, 'enable', g.enable)
    INTO v_result FROM iot.gateways g
    WHERE g.identifier = p_data->>'identifier' AND g.enable = TRUE LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_gateway_by_api_key(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT('id', g.id, 'name', g.name, 'identifier', g.identifier)
    INTO v_result FROM iot.gateways g
    WHERE g.api_key = p_data->>'api_key' AND g.enable = TRUE LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.save_gateway(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER; v_api_key TEXT;
BEGIN
    v_api_key := encode(gen_random_bytes(24), 'hex');
    IF p_data->>'id' IS NOT NULL THEN
        UPDATE iot.gateways SET
            name     = COALESCE(p_data->>'name',     name),
            location = COALESCE(p_data->>'location', location),
            enable   = COALESCE((p_data->>'enable')::BOOLEAN, enable),
            date_up  = EXTRACT(EPOCH FROM NOW())::BIGINT
        WHERE id = (p_data->>'id')::INTEGER RETURNING id INTO v_id;
    ELSE
        INSERT INTO iot.gateways (name, identifier, location, api_key)
        VALUES (p_data->>'name', p_data->>'identifier', p_data->>'location', v_api_key)
        RETURNING id INTO v_id;
    END IF;
    RETURN JSON_BUILD_OBJECT('id', v_id, 'api_key', v_api_key);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.list_sensors(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', s.id, 'gateway_id', s.gateway_id, 'gateway', g.name,
        'name', s.name, 'identifier', s.identifier, 'type', s.type,
        'unit', s.unit, 'location', s.location, 'enable', s.enable
    ) ORDER BY s.id) INTO v_result
    FROM iot.sensors s JOIN iot.gateways g ON g.id = s.gateway_id
    WHERE (p_data->>'gateway_id' IS NULL OR s.gateway_id = (p_data->>'gateway_id')::INTEGER);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id', s.id, 'gateway_id', s.gateway_id, 'name', s.name,
        'identifier', s.identifier, 'type', s.type, 'unit', s.unit,
        'location', s.location, 'enable', s.enable
    ) INTO v_result FROM iot.sensors s WHERE s.id = (p_data->>'id')::INTEGER;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.save_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    IF p_data->>'id' IS NOT NULL THEN
        UPDATE iot.sensors SET
            name     = COALESCE(p_data->>'name',     name),
            location = COALESCE(p_data->>'location', location),
            enable   = COALESCE((p_data->>'enable')::BOOLEAN, enable),
            date_up  = EXTRACT(EPOCH FROM NOW())::BIGINT
        WHERE id = (p_data->>'id')::INTEGER RETURNING id INTO v_id;
    ELSE
        INSERT INTO iot.sensors (gateway_id, name, identifier, type, unit, location)
        VALUES (
            (p_data->>'gateway_id')::INTEGER, p_data->>'name', p_data->>'identifier',
            COALESCE(p_data->>'type', 'temperature')::iot.enum_sensor_type,
            COALESCE(p_data->>'unit', '°C'), p_data->>'location'
        ) RETURNING id INTO v_id;
    END IF;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.save_reading(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO iot.sensor_readings (sensor_id, gateway_id, temperature, voltage, battery, extra_data)
    VALUES (
        (p_data->>'sensor_id')::INTEGER, (p_data->>'gateway_id')::INTEGER,
        (p_data->>'temperature')::NUMERIC, (p_data->>'voltage')::NUMERIC,
        (p_data->>'battery')::NUMERIC, (p_data->>'extra_data')::JSONB
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_latest_readings_by_gateway(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(t ORDER BY t.sensor_id) INTO v_result
    FROM (
        SELECT DISTINCT ON (r.sensor_id)
            r.id, r.sensor_id, s.name AS sensor_name, s.unit,
            r.temperature, r.voltage, r.battery, r.extra_data, r.received_at
        FROM iot.sensor_readings r JOIN iot.sensors s ON s.id = r.sensor_id
        WHERE r.gateway_id = (p_data->>'gateway_id')::INTEGER
        ORDER BY r.sensor_id, r.received_at DESC
    ) t;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_sensor_history(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', r.id, 'temperature', r.temperature, 'voltage', r.voltage,
        'battery', r.battery, 'extra_data', r.extra_data, 'received_at', r.received_at
    ) ORDER BY r.received_at DESC) INTO v_result
    FROM iot.sensor_readings r
    WHERE r.sensor_id = (p_data->>'sensor_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR r.received_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR r.received_at <= (p_data->>'to_ts')::TIMESTAMPTZ)
    LIMIT COALESCE((p_data->>'limit')::INTEGER, 500);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_sensor_summary(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'sensor_id', (p_data->>'sensor_id')::INTEGER, 'count', COUNT(*),
        'temp_min', MIN(temperature), 'temp_max', MAX(temperature),
        'temp_avg', ROUND(AVG(temperature)::NUMERIC, 2),
        'voltage_min', MIN(voltage), 'voltage_max', MAX(voltage),
        'battery_min', MIN(battery), 'from_ts', MIN(received_at), 'to_ts', MAX(received_at)
    ) INTO v_result FROM iot.sensor_readings
    WHERE sensor_id = (p_data->>'sensor_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR received_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR received_at <= (p_data->>'to_ts')::TIMESTAMPTZ);
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_thresholds_for_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', t.id, 'metric', t.metric,
        'min_value', t.min_value, 'max_value', t.max_value, 'alert_message', t.alert_message
    )) INTO v_result
    FROM iot.thresholds t WHERE t.sensor_id = (p_data->>'sensor_id')::INTEGER AND t.enable = TRUE;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.list_thresholds(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', t.id, 'sensor_id', t.sensor_id, 'user_id', t.user_id,
        'metric', t.metric, 'min_value', t.min_value,
        'max_value', t.max_value, 'alert_message', t.alert_message, 'enable', t.enable
    ) ORDER BY t.id) INTO v_result FROM iot.thresholds t
    WHERE (p_data->>'sensor_id' IS NULL OR t.sensor_id = (p_data->>'sensor_id')::INTEGER);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.upsert_threshold(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    INSERT INTO iot.thresholds (sensor_id, user_id, metric, min_value, max_value, alert_message)
    VALUES (
        (p_data->>'sensor_id')::INTEGER, (p_data->>'user_id')::INTEGER,
        COALESCE(p_data->>'metric', 'temperature'),
        (p_data->>'min_value')::NUMERIC, (p_data->>'max_value')::NUMERIC,
        p_data->>'alert_message'
    )
    ON CONFLICT (sensor_id, user_id, metric) DO UPDATE SET
        min_value     = EXCLUDED.min_value,
        max_value     = EXCLUDED.max_value,
        alert_message = EXCLUDED.alert_message,
        enable        = TRUE,
        date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
    RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.delete_threshold(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.thresholds SET enable = FALSE WHERE id = (p_data->>'id')::INTEGER;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.save_alert(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO iot.alerts (sensor_id, gateway_id, type, metric, value, threshold, message)
    VALUES (
        (p_data->>'sensor_id')::INTEGER, (p_data->>'gateway_id')::INTEGER,
        COALESCE(p_data->>'type', 'threshold_exceeded')::iot.enum_alert_type,
        p_data->>'metric', (p_data->>'value')::NUMERIC,
        (p_data->>'threshold')::NUMERIC, p_data->>'message'
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_active_alerts(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', a.id, 'sensor_id', a.sensor_id, 'gateway_id', a.gateway_id,
        'type', a.type, 'metric', a.metric, 'value', a.value,
        'threshold', a.threshold, 'message', a.message,
        'resolved', a.resolved, 'created_at', a.created_at
    ) ORDER BY a.created_at DESC) INTO v_result
    FROM iot.alerts a
    WHERE a.resolved = COALESCE((p_data->>'resolved')::BOOLEAN, FALSE)
      AND (p_data->>'gateway_id' IS NULL OR a.gateway_id = (p_data->>'gateway_id')::INTEGER);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.resolve_alert(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.alerts SET resolved = TRUE, resolved_at = NOW() WHERE id = (p_data->>'id')::BIGINT;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_alert_history(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', a.id, 'sensor_id', a.sensor_id, 'type', a.type,
        'metric', a.metric, 'value', a.value, 'message', a.message,
        'resolved', a.resolved, 'created_at', a.created_at
    ) ORDER BY a.created_at DESC) INTO v_result
    FROM iot.alerts a
    WHERE a.gateway_id = (p_data->>'gateway_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR a.created_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR a.created_at <= (p_data->>'to_ts')::TIMESTAMPTZ);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- SEEDS: roles, permisos y asignaciones
-- =============================================================================

INSERT INTO core.roles (name, description) VALUES
    ('Administrador', 'Acceso total al sistema'),
    ('Operador',      'Monitoreo de sensores y gestión de umbrales'),
    ('Técnico',       'Gestión técnica de gateways, sensores y calibración'),
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

-- Operador: monitoreo + umbrales + reportes
INSERT INTO core.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM core.roles r, core.permissions p
WHERE r.name = 'Operador' AND p.slug IN (
    'iot.view_sensors', 'iot.view_telemetry', 'iot.view_alerts',
    'iot.resolve_alerts', 'iot.manage_thresholds', 'iot.view_reports'
)
ON CONFLICT DO NOTHING;

-- Técnico: todo iot (gestión completa de gateways y sensores)
INSERT INTO core.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM core.roles r, core.permissions p
WHERE r.name = 'Técnico' AND p.slug IN (
    'iot.view_sensors', 'iot.manage_sensors', 'iot.view_telemetry',
    'iot.view_alerts', 'iot.resolve_alerts', 'iot.manage_thresholds',
    'iot.view_reports', 'iot.manage_gateways'
)
ON CONFLICT DO NOTHING;

-- Administrador: todos los permisos
INSERT INTO core.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM core.roles r, core.permissions p
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

-- Auditor: solo lectura
INSERT INTO core.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM core.roles r, core.permissions p
WHERE r.name = 'Auditor' AND p.slug IN (
    'iot.view_sensors', 'iot.view_telemetry', 'iot.view_alerts', 'iot.view_reports'
)
ON CONFLICT DO NOTHING;


-- =============================================================================
-- PERMISOS DE SCHEMAS (requeridos para PostgREST y Supabase RPC)
-- =============================================================================

GRANT USAGE ON SCHEMA core TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA iot  TO anon, authenticated, service_role;

GRANT ALL ON ALL TABLES    IN SCHEMA core TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES    IN SCHEMA iot  TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA core TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA iot  TO anon, authenticated, service_role;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA core TO anon, authenticated, service_role;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA iot  TO anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT ALL ON TABLES    TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA core GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA iot  GRANT ALL ON TABLES    TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA iot  GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA iot  GRANT ALL ON FUNCTIONS TO anon, authenticated, service_role;
