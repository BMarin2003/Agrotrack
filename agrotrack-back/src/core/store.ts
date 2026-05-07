import { RedisClient } from 'bun';
import { configServer } from 'src/config';
import { createHash } from 'crypto';
import { PermisoSlug } from './permisos.type';

export class UserStore {
  private client: RedisClient;

  constructor() {
    this.client = new RedisClient(configServer.redis.url);
  }

  private async exec<T>(operation: (client: RedisClient) => Promise<T>, retries = 3): Promise<T | null> {
    for (let i = 0; i < retries; i++) {
      try {
        return await operation(this.client);
      } catch (error) {
        console.error(`[Redis] Error en intento ${i + 1}:`, error);
        if (i === retries - 1) return null;
        await new Promise(resolve => setTimeout(resolve, 100 * (i + 1)));
      }
    }
    return null;
  }

  async setUserPermissions(userId: string, permissions: string[]) {
    const key = `user:${userId}:permissions`;
    await this.exec(async c => {
      await c.del(key);
      if (permissions && permissions.length > 0) await c.sadd(key, ...permissions);
    });
  }

  async hasPermission(userId: string, permission: PermisoSlug): Promise<boolean> {
    const key = `user:${userId}:permissions`;
    const result = await this.exec(c => c.sismember(key, permission));
    return !!result;
  }

  async addToken(token: string) {
    const hash = createHash('sha256').update(token).digest('hex');
    await this.exec(c => c.set(`whitelist:${hash}`, '1', 'EX', configServer.auth.expiresIn));
  }

  async removeToken(token: string) {
    const hash = createHash('sha256').update(token).digest('hex');
    await this.exec(c => c.del(`whitelist:${hash}`));
  }

  async isTokenValid(token: string): Promise<boolean> {
    const hash = createHash('sha256').update(token).digest('hex');
    const result = await this.exec(c => c.exists(`whitelist:${hash}`));
    return !!result;
  }
}

export const userStore = new UserStore();
