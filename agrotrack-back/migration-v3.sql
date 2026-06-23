-- =============================================================================
-- AgroTrack — Migration v3
-- Incluye todo lo de v2 (corregido) + calibración + mantenimiento
-- Ejecutar en Supabase SQL Editor o con: supabase db query --linked -f migration-v3.sql
-- =============================================================================

-- ─── 1. Columnas WiFi en iot.gateways (de v2) ───────────────────────────────

ALTER TABLE iot.gateways
    ADD COLUMN IF NOT EXISTS wifi_ssid     TEXT,
    ADD COLUMN IF NOT EXISTS wifi_security TEXT DEFAULT 'WPA2';


-- ─── 2. iot.get_last_reading_by_sensor (v2 tenía tabla y firma incorrectas) ──

DROP FUNCTION IF EXISTS iot.get_last_reading_by_sensor(INT);

CREATE OR REPLACE FUNCTION iot.get_last_reading_by_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'sensor_id',  r.sensor_id,
        'temperature', r.temperature,
        'voltage',    r.voltage,
        'battery',    r.battery,
        'extra_data', r.extra_data,
        'received_at', r.received_at
    ) INTO v_result
    FROM iot.sensor_readings r
    WHERE r.sensor_id = (p_data->>'sensor_id')::INTEGER
    ORDER BY r.received_at DESC
    LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION iot.get_last_reading_by_sensor(JSON) TO anon, authenticated, service_role;


-- ─── 3. iot.update_gateway_wifi (v2 tenía params posicionales y updated_at) ──

DROP FUNCTION IF EXISTS iot.update_gateway_wifi(INT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION iot.update_gateway_wifi(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.gateways SET
        wifi_ssid     = p_data->>'ssid',
        wifi_security = COALESCE(p_data->>'security', 'WPA2'),
        date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'id')::INTEGER;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Gateway no encontrado: %', p_data->>'id';
    END IF;

    RETURN JSON_BUILD_OBJECT('ok', TRUE, 'gateway_id', (p_data->>'id')::INTEGER, 'ssid', p_data->>'ssid');
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION iot.update_gateway_wifi(JSON) TO anon, authenticated, service_role;


-- ─── 4. Tabla: iot.sensor_calibrations ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS iot.sensor_calibrations (
    id          SERIAL PRIMARY KEY,
    sensor_id   INTEGER NOT NULL REFERENCES iot.sensors(id),
    gain        NUMERIC(10,6) NOT NULL DEFAULT 1.0,
    intercept   NUMERIC(10,6) NOT NULL DEFAULT 0.0,
    notes       TEXT,
    applied_by  INTEGER REFERENCES core.users(id),
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_calibrations_sensor ON iot.sensor_calibrations(sensor_id, applied_at DESC);


-- ─── 5. iot.get_calibration ─────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.get_calibration(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id',         c.id,
        'sensor_id',  c.sensor_id,
        'gain',       c.gain,
        'intercept',  c.intercept,
        'notes',      c.notes,
        'applied_at', c.applied_at
    ) INTO v_result
    FROM iot.sensor_calibrations c
    WHERE c.sensor_id = (p_data->>'sensor_id')::INTEGER
    ORDER BY c.applied_at DESC
    LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


-- ─── 6. iot.save_calibration ────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.save_calibration(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    INSERT INTO iot.sensor_calibrations (sensor_id, gain, intercept, notes, applied_by)
    VALUES (
        (p_data->>'sensor_id')::INTEGER,
        COALESCE((p_data->>'gain')::NUMERIC,      1.0),
        COALESCE((p_data->>'intercept')::NUMERIC, 0.0),
        p_data->>'notes',
        (p_data->>'user_id')::INTEGER
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;


-- ─── 7. Tabla: iot.gateway_maintenance ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS iot.gateway_maintenance (
    id             SERIAL PRIMARY KEY,
    gateway_id     INTEGER NOT NULL REFERENCES iot.gateways(id),
    notes          TEXT,
    performed_by   INTEGER REFERENCES core.users(id),
    performed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    next_scheduled DATE
);

CREATE INDEX IF NOT EXISTS idx_maintenance_gateway ON iot.gateway_maintenance(gateway_id, performed_at DESC);


-- ─── 8. iot.list_maintenance ────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.list_maintenance(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id',             m.id,
        'gateway_id',     m.gateway_id,
        'notes',          m.notes,
        'performed_at',   m.performed_at,
        'next_scheduled', m.next_scheduled
    ) ORDER BY m.performed_at DESC) INTO v_result
    FROM iot.gateway_maintenance m
    WHERE m.gateway_id = (p_data->>'gateway_id')::INTEGER;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


-- ─── 9. iot.register_maintenance ────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.register_maintenance(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    INSERT INTO iot.gateway_maintenance (gateway_id, notes, performed_by, next_scheduled)
    VALUES (
        (p_data->>'gateway_id')::INTEGER,
        p_data->>'notes',
        (p_data->>'user_id')::INTEGER,
        (p_data->>'next_scheduled')::DATE
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;


-- ─── 10. Permisos ───────────────────────────────────────────────────────────

GRANT ALL ON TABLE  iot.sensor_calibrations  TO anon, authenticated, service_role;
GRANT ALL ON TABLE  iot.gateway_maintenance  TO anon, authenticated, service_role;
GRANT ALL ON SEQUENCE iot.sensor_calibrations_id_seq TO anon, authenticated, service_role;
GRANT ALL ON SEQUENCE iot.gateway_maintenance_id_seq  TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.get_calibration(JSON)      TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.save_calibration(JSON)     TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.list_maintenance(JSON)     TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.register_maintenance(JSON) TO anon, authenticated, service_role;
