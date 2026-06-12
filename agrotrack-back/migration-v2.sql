-- =============================================================================
-- AgroTrack — Migration v2
-- Agrega: columnas wifi en iot.gateways + 2 funciones nuevas
-- Ejecutar en Supabase SQL Editor
-- =============================================================================

-- ─── 1. Columnas WiFi en iot.gateways ────────────────────────────────────────

ALTER TABLE iot.gateways
  ADD COLUMN IF NOT EXISTS wifi_ssid     TEXT,
  ADD COLUMN IF NOT EXISTS wifi_security TEXT DEFAULT 'WPA2';

-- ─── 2. iot.get_last_reading_by_sensor ───────────────────────────────────────
-- Retorna la lectura más reciente de un sensor específico.

CREATE OR REPLACE FUNCTION iot.get_last_reading_by_sensor(p_sensor_id INT)
RETURNS JSON
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
  v_result JSON;
BEGIN
  SELECT row_to_json(r) INTO v_result
  FROM (
    SELECT
      sensor_id,
      temperature,
      voltage,
      battery,
      extra_data,
      received_at
    FROM iot.readings
    WHERE sensor_id = p_sensor_id
    ORDER BY received_at DESC
    LIMIT 1
  ) r;

  RETURN v_result;  -- NULL si no hay lecturas para este sensor
END;
$$;

GRANT EXECUTE ON FUNCTION iot.get_last_reading_by_sensor(INT) TO authenticated, service_role;

-- ─── 3. iot.update_gateway_wifi ──────────────────────────────────────────────
-- Actualiza la configuración WiFi almacenada para un gateway.
-- La contraseña NO se persiste en BD por seguridad.

CREATE OR REPLACE FUNCTION iot.update_gateway_wifi(
  p_id       INT,
  p_ssid     TEXT,
  p_security TEXT DEFAULT 'WPA2'
)
RETURNS JSON
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
  v_result JSON;
BEGIN
  UPDATE iot.gateways
  SET
    wifi_ssid     = p_ssid,
    wifi_security = p_security,
    updated_at    = NOW()
  WHERE id = p_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Gateway no encontrado: %', p_id;
  END IF;

  SELECT row_to_json(g) INTO v_result
  FROM (
    SELECT id, name, identifier, wifi_ssid, wifi_security, updated_at
    FROM iot.gateways
    WHERE id = p_id
  ) g;

  RETURN v_result;
END;
$$;

GRANT EXECUTE ON FUNCTION iot.update_gateway_wifi(INT, TEXT, TEXT) TO authenticated, service_role;
