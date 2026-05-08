import { createHash } from 'crypto';
import { configServer } from 'src/config';
import { PermisoSlug } from './permisos.type';

class UserStore {
  private permissions = new Map<string, Set<string>>();
  private whitelist = new Map<string, number>(); // hash -> expiry timestamp (ms)

  async setUserPermissions(userId: string, perms: string[]) {
    this.permissions.set(String(userId), new Set(perms));
  }

  async hasPermission(userId: string, permission: PermisoSlug): Promise<boolean> {
    return this.permissions.get(String(userId))?.has(permission) ?? false;
  }

  async addToken(token: string) {
    const hash = createHash('sha256').update(token).digest('hex');
    this.whitelist.set(hash, Date.now() + configServer.auth.expiresIn * 1000);
  }

  async removeToken(token: string) {
    const hash = createHash('sha256').update(token).digest('hex');
    this.whitelist.delete(hash);
  }

  async isTokenValid(token: string): Promise<boolean> {
    const hash = createHash('sha256').update(token).digest('hex');
    const expiresAt = this.whitelist.get(hash);
    if (!expiresAt) return false;
    if (Date.now() > expiresAt) {
      this.whitelist.delete(hash);
      return false;
    }
    return true;
  }
}

export const userStore = new UserStore();
