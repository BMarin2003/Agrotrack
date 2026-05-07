-- =============================================================================
-- IOT: Procedimientos de Gateways, Sensores, Telemetría, Umbrales y Alertas
-- =============================================================================


-- ─── GATEWAYS ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.list_gateways(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id',         g.id,
            'name',       g.name,
            'identifier', g.identifier,
            'location',   g.location,
            'enable',     g.enable
        ) ORDER BY g.id
    ) INTO v_result FROM iot.gateways g;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.get_gateway_by_identifier(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT('id', g.id, 'name', g.name, 'enable', g.enable)
    INTO v_result
    FROM iot.gateways g
    WHERE g.identifier = p_data->>'identifier' AND g.enable = TRUE
    LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.get_gateway_by_api_key(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT('id', g.id, 'name', g.name, 'identifier', g.identifier)
    INTO v_result
    FROM iot.gateways g
    WHERE g.api_key = p_data->>'api_key' AND g.enable = TRUE
    LIMIT 1;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.save_gateway(p_data JSON)
RETURNS JSON AS $$
DECLARE
    v_id      INTEGER;
    v_api_key TEXT;
BEGIN
    v_api_key := encode(gen_random_bytes(24), 'hex');

    IF p_data->>'id' IS NOT NULL THEN
        UPDATE iot.gateways SET
            name     = COALESCE(p_data->>'name',     name),
            location = COALESCE(p_data->>'location', location),
            enable   = COALESCE((p_data->>'enable')::BOOLEAN, enable),
            date_up  = EXTRACT(EPOCH FROM NOW())::BIGINT
        WHERE id = (p_data->>'id')::INTEGER
        RETURNING id INTO v_id;
    ELSE
        INSERT INTO iot.gateways (name, identifier, location, api_key)
        VALUES (p_data->>'name', p_data->>'identifier', p_data->>'location', v_api_key)
        RETURNING id INTO v_id;
    END IF;

    RETURN JSON_BUILD_OBJECT('id', v_id, 'api_key', v_api_key);
END;
$$ LANGUAGE plpgsql;


-- ─── SENSORES ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.list_sensors(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id',         s.id,
            'gateway_id', s.gateway_id,
            'gateway',    g.name,
            'name',       s.name,
            'identifier', s.identifier,
            'type',       s.type,
            'unit',       s.unit,
            'location',   s.location,
            'enable',     s.enable
        ) ORDER BY s.id
    ) INTO v_result
    FROM iot.sensors s
    JOIN iot.gateways g ON g.id = s.gateway_id
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
    ) INTO v_result
    FROM iot.sensors s
    WHERE s.id = (p_data->>'id')::INTEGER;
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
        WHERE id = (p_data->>'id')::INTEGER
        RETURNING id INTO v_id;
    ELSE
        INSERT INTO iot.sensors (gateway_id, name, identifier, type, unit, location)
        VALUES (
            (p_data->>'gateway_id')::INTEGER,
            p_data->>'name',
            p_data->>'identifier',
            COALESCE(p_data->>'type', 'temperature')::iot.enum_sensor_type,
            COALESCE(p_data->>'unit', '°C'),
            p_data->>'location'
        )
        RETURNING id INTO v_id;
    END IF;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;


-- ─── TELEMETRÍA ──────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.save_reading(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO iot.sensor_readings (sensor_id, gateway_id, temperature, voltage, battery, extra_data)
    VALUES (
        (p_data->>'sensor_id')::INTEGER,
        (p_data->>'gateway_id')::INTEGER,
        (p_data->>'temperature')::NUMERIC,
        (p_data->>'voltage')::NUMERIC,
        (p_data->>'battery')::NUMERIC,
        (p_data->>'extra_data')::JSONB
    )
    RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;


-- Última lectura por cada sensor del gateway (snapshot en tiempo real)
CREATE OR REPLACE FUNCTION iot.get_latest_readings_by_gateway(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(t ORDER BY t.sensor_id)
    INTO v_result
    FROM (
        SELECT DISTINCT ON (r.sensor_id)
            r.id, r.sensor_id, s.name AS sensor_name, s.unit,
            r.temperature, r.voltage, r.battery, r.extra_data,
            r.received_at
        FROM iot.sensor_readings r
        JOIN iot.sensors s ON s.id = r.sensor_id
        WHERE r.gateway_id = (p_data->>'gateway_id')::INTEGER
        ORDER BY r.sensor_id, r.received_at DESC
    ) t;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


-- Historial paginado por sensor y rango de fechas
CREATE OR REPLACE FUNCTION iot.get_sensor_history(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id', r.id, 'temperature', r.temperature,
            'voltage', r.voltage, 'battery', r.battery,
            'extra_data', r.extra_data, 'received_at', r.received_at
        ) ORDER BY r.received_at DESC
    ) INTO v_result
    FROM iot.sensor_readings r
    WHERE r.sensor_id = (p_data->>'sensor_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR r.received_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR r.received_at <= (p_data->>'to_ts')::TIMESTAMPTZ)
    LIMIT COALESCE((p_data->>'limit')::INTEGER, 500);

    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


-- Resumen estadístico (min, max, avg, count)
CREATE OR REPLACE FUNCTION iot.get_sensor_summary(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'sensor_id',   (p_data->>'sensor_id')::INTEGER,
        'count',       COUNT(*),
        'temp_min',    MIN(temperature),
        'temp_max',    MAX(temperature),
        'temp_avg',    ROUND(AVG(temperature)::NUMERIC, 2),
        'voltage_min', MIN(voltage),
        'voltage_max', MAX(voltage),
        'battery_min', MIN(battery),
        'from_ts',     MIN(received_at),
        'to_ts',       MAX(received_at)
    ) INTO v_result
    FROM iot.sensor_readings
    WHERE sensor_id = (p_data->>'sensor_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR received_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR received_at <= (p_data->>'to_ts')::TIMESTAMPTZ);

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


-- ─── UMBRALES ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.get_thresholds_for_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id', t.id, 'metric', t.metric,
            'min_value', t.min_value, 'max_value', t.max_value,
            'alert_message', t.alert_message
        )
    ) INTO v_result
    FROM iot.thresholds t
    WHERE t.sensor_id = (p_data->>'sensor_id')::INTEGER
      AND t.enable = TRUE;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.list_thresholds(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id', t.id, 'sensor_id', t.sensor_id, 'user_id', t.user_id,
            'metric', t.metric, 'min_value', t.min_value,
            'max_value', t.max_value, 'alert_message', t.alert_message, 'enable', t.enable
        ) ORDER BY t.id
    ) INTO v_result
    FROM iot.thresholds t
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
        (p_data->>'sensor_id')::INTEGER,
        (p_data->>'user_id')::INTEGER,
        COALESCE(p_data->>'metric', 'temperature'),
        (p_data->>'min_value')::NUMERIC,
        (p_data->>'max_value')::NUMERIC,
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


-- ─── ALERTAS ─────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION iot.save_alert(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO iot.alerts (sensor_id, gateway_id, type, metric, value, threshold, message)
    VALUES (
        (p_data->>'sensor_id')::INTEGER,
        (p_data->>'gateway_id')::INTEGER,
        COALESCE(p_data->>'type', 'threshold_exceeded')::iot.enum_alert_type,
        p_data->>'metric',
        (p_data->>'value')::NUMERIC,
        (p_data->>'threshold')::NUMERIC,
        p_data->>'message'
    )
    RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.get_active_alerts(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id', a.id, 'sensor_id', a.sensor_id, 'gateway_id', a.gateway_id,
            'type', a.type, 'metric', a.metric, 'value', a.value,
            'threshold', a.threshold, 'message', a.message,
            'resolved', a.resolved, 'created_at', a.created_at
        ) ORDER BY a.created_at DESC
    ) INTO v_result
    FROM iot.alerts a
    WHERE a.resolved = COALESCE((p_data->>'resolved')::BOOLEAN, FALSE)
      AND (p_data->>'gateway_id' IS NULL OR a.gateway_id = (p_data->>'gateway_id')::INTEGER);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.resolve_alert(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.alerts SET resolved = TRUE, resolved_at = NOW()
    WHERE id = (p_data->>'id')::BIGINT;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION iot.get_alert_history(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id', a.id, 'sensor_id', a.sensor_id, 'type', a.type,
            'metric', a.metric, 'value', a.value, 'message', a.message,
            'resolved', a.resolved, 'created_at', a.created_at
        ) ORDER BY a.created_at DESC
    ) INTO v_result
    FROM iot.alerts a
    WHERE a.gateway_id = (p_data->>'gateway_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR a.created_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR a.created_at <= (p_data->>'to_ts')::TIMESTAMPTZ);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;
