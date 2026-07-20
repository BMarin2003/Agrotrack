-- ============================================================
-- AgroTrack — Migration 014: iot.delete_threshold valida ownership
-- iot.delete_threshold aceptaba cualquier `id` sin verificar que el
-- umbral perteneciera a quien lo borra — un operador que adivinara el
-- id de un umbral ajeno podía desactivarlo. GET/POST /thresholds ya
-- escriben este scoping (user_id = NULL para admin, user_id propio
-- para el resto); esta migración alinea DELETE con el mismo patrón.
-- ============================================================

CREATE OR REPLACE FUNCTION iot.delete_threshold(p_data JSON)
RETURNS JSON AS $$
DECLARE
  v_user_id INTEGER := (p_data->>'user_id')::INTEGER;
  v_id      INTEGER;
BEGIN
  UPDATE iot.thresholds
  SET enable = FALSE
  WHERE id = (p_data->>'id')::INTEGER
    AND (v_user_id IS NULL OR user_id = v_user_id)
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'Umbral no encontrado o sin permiso para eliminarlo';
  END IF;

  RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION iot.delete_threshold(JSON) TO anon, authenticated, service_role;
