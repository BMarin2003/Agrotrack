interface AttemptRecord {
  count: number;
  firstAttempt: number;
  lockedUntil?: number;
}

export class RateLimiter {
  private readonly store = new Map<string, AttemptRecord>();

  constructor(
    private readonly maxAttempts = 5,
    private readonly windowMs = 15 * 60 * 1000,
    private readonly lockoutMs = 15 * 60 * 1000,
  ) {}

  check(key: string): { allowed: boolean; retryAfter?: number; remaining?: number } {
    const now = Date.now();
    const record = this.store.get(key);

    if (!record) return { allowed: true, remaining: this.maxAttempts };

    if (record.lockedUntil) {
      if (now < record.lockedUntil) {
        return { allowed: false, retryAfter: Math.ceil((record.lockedUntil - now) / 1000) };
      }
      this.store.delete(key);
      return { allowed: true, remaining: this.maxAttempts };
    }

    if (now - record.firstAttempt > this.windowMs) {
      this.store.delete(key);
      return { allowed: true, remaining: this.maxAttempts };
    }

    const remaining = this.maxAttempts - record.count;
    if (remaining <= 0) {
      record.lockedUntil = now + this.lockoutMs;
      return { allowed: false, retryAfter: Math.ceil(this.lockoutMs / 1000) };
    }

    return { allowed: true, remaining };
  }

  record(key: string): void {
    const now = Date.now();
    const existing = this.store.get(key);
    if (!existing) {
      this.store.set(key, { count: 1, firstAttempt: now });
    } else {
      existing.count++;
      if (existing.count >= this.maxAttempts && !existing.lockedUntil) {
        existing.lockedUntil = now + this.lockoutMs;
      }
    }
  }

  reset(key: string): void {
    this.store.delete(key);
  }

  /** Visible para tests */
  _getRecord(key: string) { return this.store.get(key); }
}

export const loginRateLimiter = new RateLimiter();
