import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { extractToken, generateToken, validateToken } from '@core/jwt';
import { userStore } from '@core/store';
import { authPlugin } from '@core/auth.guard';
import { configServer } from 'src/config';

const { verifySync } = Bun.password;
const path = '/auth';

export const AuthApi = new Elysia()
  .post(`${path}/login`, async ({ body, status, cookie }) => {
    const { email, password } = body as { email: string; password: string };

    const credentialsResult = await execProcedure('core.get_user_credentials', [{ email }]);
    if (credentialsResult.error) return status(400, { message: credentialsResult.error });

    const credentials = credentialsResult.result;
    if (!credentials?.id || !credentials?.password_hash) {
      return status(401, { message: 'Credenciales inválidas' });
    }
    if (credentials.enable === false) {
      return status(403, { message: 'Cuenta inactiva. Contacte al administrador.' });
    }

    const isPasswordValid = verifySync(password, String(credentials.password_hash));
    if (!isPasswordValid) return status(401, { message: 'Credenciales inválidas' });

    const userResult = await execProcedure('core.get_user_login_data', [{ id: credentials.id }]);
    if (userResult.error) return status(400, { message: userResult.error });

    const user = userResult.result;
    if (!user) return status(401, { message: 'Credenciales inválidas' });

    const token = generateToken({ id: user.id, email: user.email, names: user.names, roles: user.roles });

    await userStore.addToken(token);
    await userStore.setUserPermissions(user.id, user.permisos || []);

    if (cookie?.session_token) {
      cookie.session_token.set({
        value: token,
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: configServer.auth.expiresIn,
      });
    }

    return { user, token, expiresIn: configServer.auth.expiresIn };
  }, {
    body: t.Object({ email: t.String(), password: t.String() }),
  })

  .post(`${path}/logout`, async ({ headers, cookie }) => {
    const token = extractToken(headers, cookie);
    if (token) userStore.removeToken(token);
    if (cookie?.session_token) cookie.session_token.remove();
    return { message: 'Logout exitoso' };
  })

  .post(`${path}/verify-token`, async ({ headers, status, cookie }) => {
    const user = await validateToken(headers, cookie);
    if (user.error) return status(401, { message: user.error });

    const userResult = await execProcedure('core.get_user_login_data', [{ id: user.id }]);
    if (userResult.error) return status(400, { message: userResult.error });

    const token = generateToken({
      id: userResult.result.id,
      email: userResult.result.email,
      names: userResult.result.names,
      roles: userResult.result.roles,
    });

    await userStore.addToken(token);
    await userStore.setUserPermissions(userResult.result.id, userResult.result.permisos || []);

    if (cookie?.session_token) {
      cookie.session_token.set({
        value: token,
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: configServer.auth.expiresIn,
      });
    }

    return { user: userResult.result, token, expiresIn: configServer.auth.expiresIn };
  }, {
    headers: t.Object({ authorization: t.String() }),
  })

  .group(path, app => app
    .use(authPlugin)
    .put('/update-password', async ({ body, user, status }) => {
      const { currentPassword, newPassword } = body as { currentPassword: string; newPassword: string };

      const credentialsResult = await execProcedure('core.get_user_credentials', [{ email: (user as any).email }]);
      if (credentialsResult.error || !credentialsResult.result) {
        return status(400, { message: 'No se pudo verificar la identidad del usuario' });
      }

      const isPasswordValid = verifySync(currentPassword, String(credentialsResult.result.password_hash));
      if (!isPasswordValid) return status(401, { message: 'La contraseña actual es incorrecta' });

      const password_hash = Bun.password.hashSync(newPassword);
      const result = await execProcedure('core.update_user_password', [{ id: (user as any).id, password_hash }]);
      if (result.error) return status(400, { message: result.error });

      return { message: 'Contraseña actualizada correctamente' };
    }, {
      body: t.Object({ currentPassword: t.String(), newPassword: t.String({ minLength: 8 }) }),
    })
  );
