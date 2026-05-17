import { Elysia, t } from 'elysia';
import { execProcedure } from '@core/db/connection';
import { extractToken, generateToken, validateToken } from '@core/jwt';
import { userStore } from '@core/store';
import { authPlugin } from '@core/auth.guard';
import { loginRateLimiter } from '@core/rate-limiter';
import { configServer } from 'src/config';

const { verifySync } = Bun.password;
const path = '/auth';

export const AuthApi = new Elysia()
  .post(`${path}/login`, async ({ body, set, cookie, request, server }) => {
    const { email, password } = body as { email: string; password: string };

    const ip = server?.requestIP(request)?.address ?? 'unknown';
    const rateLimitKey = `${ip}:${email}`;
    const rateCheck = loginRateLimiter.check(rateLimitKey);
    if (!rateCheck.allowed) {
      set.status = 429;
      return { message: 'Demasiados intentos fallidos. Intenta de nuevo más tarde.', retryAfter: rateCheck.retryAfter };
    }

    const credentialsResult = await execProcedure('core.get_user_credentials', [{ email }]);
    if (credentialsResult.error) { set.status = 400; return { message: credentialsResult.error }; }

    const credentials = credentialsResult.result;
    if (!credentials?.id || !credentials?.password_hash) {
      loginRateLimiter.record(rateLimitKey);
      set.status = 401; return { message: 'Credenciales inválidas' };
    }
    if (credentials.enable === false) {
      set.status = 403; return { message: 'Cuenta inactiva. Contacte al administrador.' };
    }

    const isPasswordValid = verifySync(password, String(credentials.password_hash));
    if (!isPasswordValid) {
      loginRateLimiter.record(rateLimitKey);
      set.status = 401; return { message: 'Credenciales inválidas' };
    }

    loginRateLimiter.reset(rateLimitKey);

    const userResult = await execProcedure('core.get_user_login_data', [{ id: credentials.id }]);
    if (userResult.error) { set.status = 400; return { message: userResult.error }; }

    const user = userResult.result;
    if (!user) { set.status = 401; return { message: 'Credenciales inválidas' }; }

    const token = generateToken({ id: user.id, email: user.email, names: user.names, roles: user.roles });

    await userStore.addToken(token);
    await userStore.setUserPermissions(user.id, user.permisos || []);

    if (cookie?.session_token) {
      cookie.session_token.set({
        value: token,
        httpOnly: true,
        secure: configServer.isProduction,
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

  .post(`${path}/verify-token`, async ({ headers, set, cookie }) => {
    const user = await validateToken(headers, cookie);
    if (user.error) { set.status = 401; return { message: user.error }; }

    const userResult = await execProcedure('core.get_user_login_data', [{ id: user.id }]);
    if (userResult.error) { set.status = 400; return { message: userResult.error }; }

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
        secure: configServer.isProduction,
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
    .put('/update-password', async ({ body, user, set }) => {
      const { currentPassword, newPassword } = body as { currentPassword: string; newPassword: string };

      const credentialsResult = await execProcedure('core.get_user_credentials', [{ email: (user as any).email }]);
      if (credentialsResult.error || !credentialsResult.result) {
        set.status = 400; return { message: 'No se pudo verificar la identidad del usuario' };
      }

      const isPasswordValid = verifySync(currentPassword, String(credentialsResult.result.password_hash));
      if (!isPasswordValid) { set.status = 401; return { message: 'La contraseña actual es incorrecta' }; }

      const password_hash = Bun.password.hashSync(newPassword);
      const result = await execProcedure('core.update_user_password', [{ id: (user as any).id, password_hash }]);
      if (result.error) { set.status = 400; return { message: result.error }; }

      return { message: 'Contraseña actualizada correctamente' };
    }, {
      body: t.Object({ currentPassword: t.String(), newPassword: t.String({ minLength: 8 }) }),
    })
  );
