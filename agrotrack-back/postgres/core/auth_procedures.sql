-- =============================================================================
-- CORE: Procedimientos de Autenticación
-- =============================================================================

CREATE OR REPLACE FUNCTION core.get_user_credentials(p_data JSON)
RETURNS JSON AS $$
DECLARE
    v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id',            u.id,
        'password_hash', u.password_hash,
        'enable',        u.enable
    ) INTO v_result
    FROM core.users u
    WHERE u.email = p_data->>'email'
    LIMIT 1;

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.get_user_login_data(p_data JSON)
RETURNS JSON AS $$
DECLARE
    v_result JSON;
BEGIN
    SELECT JSON_BUILD_OBJECT(
        'id',    u.id,
        'names', u.names,
        'email', u.email,
        'roles', COALESCE((
            SELECT JSON_AGG(JSON_BUILD_OBJECT('id', r.id, 'name', r.name))
            FROM core.roles r
            WHERE r.id = u.role_id
        ), '[]'::JSON),
        'permisos', COALESCE((
            SELECT JSON_AGG(p.slug)
            FROM core.role_permissions rp
            JOIN core.permissions p ON p.id = rp.permission_id
            WHERE rp.role_id = u.role_id
        ), '[]'::JSON)
    ) INTO v_result
    FROM core.users u
    WHERE u.id = (p_data->>'id')::INTEGER
      AND u.enable = TRUE;

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.update_user_password(p_data JSON)
RETURNS JSON AS $$
BEGIN
    UPDATE core.users
    SET password_hash = p_data->>'password_hash',
        date_up = EXTRACT(EPOCH FROM NOW())::BIGINT
    WHERE id = (p_data->>'id')::INTEGER;

    RETURN JSON_BUILD_OBJECT('ok', TRUE);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.list_users(p_data JSON DEFAULT '{}')
RETURNS JSON AS $$
DECLARE
    v_result JSON;
BEGIN
    SELECT JSON_AGG(
        JSON_BUILD_OBJECT(
            'id',      u.id,
            'names',   u.names,
            'email',   u.email,
            'enable',  u.enable,
            'role_id', u.role_id,
            'role',    r.name
        ) ORDER BY u.id
    ) INTO v_result
    FROM core.users u
    LEFT JOIN core.roles r ON r.id = u.role_id;

    RETURN COALESCE(v_result, '[]'::JSON);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION core.save_user(p_data JSON)
RETURNS JSON AS $$
DECLARE
    v_id INTEGER;
BEGIN
    IF p_data->>'id' IS NOT NULL THEN
        UPDATE core.users SET
            names         = COALESCE(p_data->>'names',         names),
            email         = COALESCE(p_data->>'email',         email),
            password_hash = COALESCE(p_data->>'password_hash', password_hash),
            enable        = COALESCE((p_data->>'enable')::BOOLEAN, enable),
            role_id       = COALESCE((p_data->>'role_id')::INTEGER, role_id),
            date_up       = EXTRACT(EPOCH FROM NOW())::BIGINT
        WHERE id = (p_data->>'id')::INTEGER
        RETURNING id INTO v_id;
    ELSE
        INSERT INTO core.users (names, email, password_hash, role_id)
        VALUES (
            p_data->>'names',
            p_data->>'email',
            p_data->>'password_hash',
            (p_data->>'role_id')::INTEGER
        )
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
