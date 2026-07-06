-- ============================================================
-- AgroTrack — Migration 004: Scoping por operador + estado de gateway
-- Corrige iot.enum_alert_type (bug latente: rules.engine.ts ya intenta
-- guardar 'anomalous_reading' y 'sensor_degraded', que no existían en el
-- enum), agrega alertas privadas por operador, alias de sensor por
-- operador, y estado de conectividad/sincronización de gateway.
-- ============================================================

-- ─── 1. iot.enum_alert_type: fix + valores nuevos ──────────────────────────
-- IMPORTANTE: no usar estos valores nuevos en ninguna sentencia de ESTE
-- mismo archivo — Postgres no permite usar un valor de enum recién
-- agregado dentro de la misma transacción en la que se agregó.
ALTER TYPE iot.enum_alert_type ADD VALUE IF NOT EXISTS 'anomalous_reading';
ALTER TYPE iot.enum_alert_type ADD VALUE IF NOT EXISTS 'sensor_degraded';
ALTER TYPE iot.enum_alert_type ADD VALUE IF NOT EXISTS 'sensor_recovered';

-- ─── 2. iot.alerts: alertas privadas por operador (HU10) ───────────────────
ALTER TABLE iot.alerts ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES core.users(id);
CREATE INDEX IF NOT EXISTS idx_alerts_user ON iot.alerts(user_id, created_at DESC);

-- ─── 3. iot.sensor_aliases: alias personal por operador (HU5/HU6) ──────────
CREATE TABLE IF NOT EXISTS iot.sensor_aliases (
    id         SERIAL PRIMARY KEY,
    sensor_id  INTEGER NOT NULL REFERENCES iot.sensors(id),
    user_id    INTEGER NOT NULL REFERENCES core.users(id),
    alias      VARCHAR(200) NOT NULL,
    date_cr    BIGINT DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT,
    date_up    BIGINT,
    UNIQUE (sensor_id, user_id)
);

-- ─── 4. iot.gateways: estado de conectividad y sincronización (HU2/HU3) ────
ALTER TABLE iot.gateways
    ADD COLUMN IF NOT EXISTS connectivity_mode  VARCHAR(20) DEFAULT 'unknown',
    ADD COLUMN IF NOT EXISTS pending_sync_count INTEGER     DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_synced_at     TIMESTAMPTZ;

-- ─── 5. Permiso nuevo: renombrar alias de sensor (HU5), otorgado a los 3 roles IoT
INSERT INTO core.permissions (name, slug) VALUES
    ('Renombrar sensor (alias)', 'iot.rename_sensor_alias')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO core.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM core.roles r, core.permissions p
WHERE r.name IN ('Operador', 'Técnico', 'Administrador') AND p.slug = 'iot.rename_sensor_alias'
ON CONFLICT DO NOTHING;

-- ─── 6a. iot.list_thresholds: filtra por user_id opcional (HU10) ───────────
-- (ya devolvía user_id en el JSON desde 001_initial_schema.sql; solo faltaba filtrar)
CREATE OR REPLACE FUNCTION iot.list_thresholds(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', t.id, 'sensor_id', t.sensor_id, 'user_id', t.user_id,
        'metric', t.metric, 'min_value', t.min_value,
        'max_value', t.max_value, 'alert_message', t.alert_message, 'enable', t.enable
    ) ORDER BY t.id) INTO v_result FROM iot.thresholds t
    WHERE (p_data->>'sensor_id' IS NULL OR t.sensor_id = (p_data->>'sensor_id')::INTEGER)
      AND ((p_data->>'user_id') IS NULL OR t.user_id = (p_data->>'user_id')::INTEGER);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

-- ─── 6. iot.get_thresholds_for_sensor: expone user_id (HU10) ───────────────
CREATE OR REPLACE FUNCTION iot.get_thresholds_for_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', t.id, 'user_id', t.user_id, 'metric', t.metric,
        'min_value', t.min_value, 'max_value', t.max_value, 'alert_message', t.alert_message
    )) INTO v_result
    FROM iot.thresholds t WHERE t.sensor_id = (p_data->>'sensor_id')::INTEGER AND t.enable = TRUE;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

-- ─── 7. iot.save_alert: acepta user_id opcional (HU8 pasa NULL, HU10 pasa el dueño)
CREATE OR REPLACE FUNCTION iot.save_alert(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO iot.alerts (sensor_id, gateway_id, user_id, type, metric, value, threshold, message)
    VALUES (
        (p_data->>'sensor_id')::INTEGER, (p_data->>'gateway_id')::INTEGER,
        (p_data->>'user_id')::INTEGER,
        COALESCE(p_data->>'type', 'threshold_exceeded')::iot.enum_alert_type,
        p_data->>'metric', (p_data->>'value')::NUMERIC,
        (p_data->>'threshold')::NUMERIC, p_data->>'message'
    ) RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id);
END;
$$ LANGUAGE plpgsql;

-- ─── 8. iot.get_active_alerts / iot.get_alert_history: filtran por user_id opcional
-- (p_data.user_id IS NULL => sin filtro, usado por Admin; ver plan de HU10)
CREATE OR REPLACE FUNCTION iot.get_active_alerts(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', a.id, 'sensor_id', a.sensor_id, 'gateway_id', a.gateway_id, 'user_id', a.user_id,
        'type', a.type, 'metric', a.metric, 'value', a.value,
        'threshold', a.threshold, 'message', a.message,
        'resolved', a.resolved, 'created_at', a.created_at
    ) ORDER BY a.created_at DESC) INTO v_result
    FROM iot.alerts a
    WHERE a.resolved = COALESCE((p_data->>'resolved')::BOOLEAN, FALSE)
      AND (p_data->>'gateway_id' IS NULL OR a.gateway_id = (p_data->>'gateway_id')::INTEGER)
      AND (
        (p_data->>'user_id') IS NULL
        OR a.user_id IS NULL
        OR a.user_id = (p_data->>'user_id')::INTEGER
      );
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_alert_history(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    -- gateway_id y threshold se incluyen para que el Android AlertDto (que ya
    -- exige gateway_id: Int no-nulo) sirva igual para alertas activas e historial.
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', a.id, 'sensor_id', a.sensor_id, 'gateway_id', a.gateway_id, 'user_id', a.user_id,
        'type', a.type, 'metric', a.metric, 'value', a.value, 'threshold', a.threshold,
        'message', a.message, 'resolved', a.resolved, 'created_at', a.created_at
    ) ORDER BY a.created_at DESC) INTO v_result
    FROM iot.alerts a
    WHERE a.gateway_id = (p_data->>'gateway_id')::INTEGER
      AND (p_data->>'from_ts' IS NULL OR a.created_at >= (p_data->>'from_ts')::TIMESTAMPTZ)
      AND (p_data->>'to_ts'   IS NULL OR a.created_at <= (p_data->>'to_ts')::TIMESTAMPTZ)
      AND (
        (p_data->>'user_id') IS NULL
        OR a.user_id IS NULL
        OR a.user_id = (p_data->>'user_id')::INTEGER
      );
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

-- ─── 9. iot.upsert_sensor_alias / iot.get_sensor_alias (HU5) ───────────────
CREATE OR REPLACE FUNCTION iot.upsert_sensor_alias(p_data JSON)
RETURNS JSON AS $$
DECLARE v_id INTEGER;
BEGIN
    INSERT INTO iot.sensor_aliases (sensor_id, user_id, alias)
    VALUES ((p_data->>'sensor_id')::INTEGER, (p_data->>'user_id')::INTEGER, p_data->>'alias')
    ON CONFLICT (sensor_id, user_id) DO UPDATE SET
        alias   = EXCLUDED.alias,
        date_up = EXTRACT(EPOCH FROM NOW())::BIGINT
    RETURNING id INTO v_id;
    RETURN JSON_BUILD_OBJECT('id', v_id, 'alias', p_data->>'alias');
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_sensor_alias(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT('alias', al.alias) INTO v_result
    FROM iot.sensor_aliases al
    WHERE al.sensor_id = (p_data->>'sensor_id')::INTEGER
      AND al.user_id = (p_data->>'user_id')::INTEGER;
    RETURN COALESCE(v_result, JSON_BUILD_OBJECT('alias', NULL));
END;
$$ LANGUAGE plpgsql;

-- ─── 10. iot.list_sensors / iot.get_sensor: alias visible cuando se pasa user_id
-- (sin user_id => comportamiento idéntico al actual, no rompe nada existente)
CREATE OR REPLACE FUNCTION iot.list_sensors(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', s.id, 'gateway_id', s.gateway_id, 'gateway', g.name,
        'name', COALESCE(al.alias, s.name), 'identifier', s.identifier, 'type', s.type,
        'unit', s.unit, 'location', s.location, 'enable', s.enable
    ) ORDER BY s.id) INTO v_result
    FROM iot.sensors s
    JOIN iot.gateways g ON g.id = s.gateway_id
    LEFT JOIN iot.sensor_aliases al
      ON al.sensor_id = s.id AND al.user_id = (p_data->>'user_id')::INTEGER
    WHERE (p_data->>'gateway_id' IS NULL OR s.gateway_id = (p_data->>'gateway_id')::INTEGER);
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION iot.get_sensor(p_data JSON)
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id', s.id, 'gateway_id', s.gateway_id, 'name', COALESCE(al.alias, s.name),
        'identifier', s.identifier, 'type', s.type, 'unit', s.unit,
        'location', s.location, 'enable', s.enable
    ) INTO v_result
    FROM iot.sensors s
    LEFT JOIN iot.sensor_aliases al
      ON al.sensor_id = s.id AND al.user_id = (p_data->>'user_id')::INTEGER
    WHERE s.id = (p_data->>'id')::INTEGER;
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- ─── 11. iot.update_gateway_status (HU2/HU3) ───────────────────────────────
CREATE OR REPLACE FUNCTION iot.update_gateway_status(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.gateways SET
        connectivity_mode  = COALESCE(p_data->>'connectivity_mode', connectivity_mode),
        pending_sync_count = COALESCE((p_data->>'pending_sync_count')::INTEGER, pending_sync_count),
        last_synced_at     = CASE WHEN COALESCE((p_data->>'pending_sync_count')::INTEGER, 0) = 0
                                   THEN NOW() ELSE last_synced_at END,
        date_up            = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'gateway_id')::INTEGER;
    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

-- ─── 12. iot.list_gateways: incluye los 2 campos nuevos ────────────────────
CREATE OR REPLACE FUNCTION iot.list_gateways(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE v_result JSON;
BEGIN
    SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', g.id, 'name', g.name, 'identifier', g.identifier,
        'location', g.location, 'enable', g.enable,
        'connectivity_mode', g.connectivity_mode,
        'pending_sync_count', g.pending_sync_count
    ) ORDER BY g.id) INTO v_result FROM iot.gateways g;
    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;

-- ─── 13. Permisos de ejecución (Supabase RPC) ──────────────────────────────
GRANT ALL ON TABLE iot.sensor_aliases TO anon, authenticated, service_role;
GRANT ALL ON SEQUENCE iot.sensor_aliases_id_seq TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.upsert_sensor_alias(JSON)   TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.update_gateway_status(JSON) TO anon, authenticated, service_role;
