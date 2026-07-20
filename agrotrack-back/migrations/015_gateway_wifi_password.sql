-- ============================================================
-- AgroTrack — Migration 015: contraseña WiFi del gateway
-- La ruta PUT /gateways/:id/wifi ya aceptaba `password` en el
-- body pero nunca se persistía (no había columna y el handler no
-- la pasaba al procedimiento). Se agrega la columna, mismo patrón
-- de texto plano ya usado para `pin` (migración 003), y se
-- actualiza iot.update_gateway_wifi para guardarla.
-- ============================================================

ALTER TABLE iot.gateways
    ADD COLUMN IF NOT EXISTS wifi_password TEXT;

CREATE OR REPLACE FUNCTION iot.update_gateway_wifi(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE iot.gateways SET
        wifi_ssid     = p_data->>'ssid',
        wifi_security = COALESCE(p_data->>'security', 'WPA2'),
        wifi_password = p_data->>'password',
        date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'id')::INTEGER;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Gateway no encontrado: %', p_data->>'id';
    END IF;

    RETURN JSON_BUILD_OBJECT('ok', TRUE, 'gateway_id', (p_data->>'id')::INTEGER, 'ssid', p_data->>'ssid');
END;
$$ LANGUAGE plpgsql;
