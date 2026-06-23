-- ============================================================
-- AgroTrack — Migration 003: PIN de gateway
-- PIN de 4 dígitos por gateway, controla acceso físico a los
-- módulos de configuración y calibración en la pantalla del
-- dispositivo. Se distribuye al gateway por MQTT (topic TBD).
-- ============================================================

ALTER TABLE iot.gateways
    ADD COLUMN IF NOT EXISTS pin CHAR(4) CHECK (pin ~ '^\d{4}$');


CREATE OR REPLACE FUNCTION iot.set_gateway_pin(p_data JSON)
RETURNS JSON AS $$
DECLARE
    v_pin  TEXT;
    v_count INTEGER;
BEGIN
    v_pin := p_data->>'pin';

    IF v_pin IS NULL OR v_pin !~ '^\d{4}$' THEN
        RAISE EXCEPTION 'PIN inválido: debe ser exactamente 4 dígitos numéricos';
    END IF;

    UPDATE iot.gateways SET
        pin     = v_pin,
        date_up = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = ANY(
        ARRAY(SELECT (json_array_elements_text(p_data->'gateway_ids'))::INTEGER)
    );

    GET DIAGNOSTICS v_count = ROW_COUNT;

    RETURN JSON_BUILD_OBJECT('ok', TRUE, 'updated', v_count);
END;
$$ LANGUAGE plpgsql;

GRANT ALL ON TABLE  iot.gateways                     TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION iot.set_gateway_pin(JSON)  TO anon, authenticated, service_role;
