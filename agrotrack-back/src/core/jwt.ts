import jwt, { JwtPayload } from 'jsonwebtoken';
import { configServer } from '../config';
import { userStore } from './store';

const { expiresIn, secret } = configServer.auth;

export const generateToken = (payload: any) => {
  return jwt.sign(payload, secret, { expiresIn });
};

export const verifyToken = (token: string) => {
  try {
    return jwt.verify(token, secret) as JwtPayload;
  } catch {
    return null;
  }
};

export const extractToken = (headers: any, cookie?: any): string | null => {
  if (cookie?.session_token?.value) return cookie.session_token.value;
  const authorization = headers?.authorization as string | undefined;
  if (!authorization || !authorization.startsWith('Bearer ')) return null;
  return authorization.split(' ')[1];
};

export const validateToken = async (headers: any, cookie?: any) => {
  const token = extractToken(headers, cookie);
  if (!token) return { error: 'No se ha enviado el token', status: 401 };

  try {
    const res = jwt.verify(token, secret) as JwtPayload;
    const isValid = await userStore.isTokenValid(token);
    if (!isValid) return { error: 'Token inválido o expirado', status: 401 };

    const roles = res.roles as any[];
    res.isAdmin = roles?.some(r => r.name === 'Administrador' || r.id === 1) ?? false;

    return res;
  } catch {
    return { error: 'Token inválido', status: 401 };
  }
};
