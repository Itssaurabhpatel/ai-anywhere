import { Request, Response, NextFunction } from 'express';

// In-memory usage meter store (Production uses Redis)
const usageMap = new Map<string, { count: number; lastReset: number }>();
const DAILY_FREE_LIMIT = 25;

export const meteringMiddleware = (req: Request, res: Response, next: NextFunction) => {
  const deviceId = (req.headers['x-device-id'] as string) || req.ip || 'anonymous';
  const now = Date.now();
  const dayMs = 24 * 60 * 60 * 1000;

  let record = usageMap.get(deviceId);
  if (!record || now - record.lastReset > dayMs) {
    record = { count: 0, lastReset: now };
  }

  if (record.count >= DAILY_FREE_LIMIT && !req.headers['authorization']) {
    return res.status(429).json({
      error: 'Usage limit exceeded',
      message: `You have reached your daily quota of ${DAILY_FREE_LIMIT} free AI actions. Upgrade to Pro for unlimited usage.`,
      remaining_quota: 0
    });
  }

  record.count += 1;
  usageMap.set(deviceId, record);
  (req as any).remainingQuota = Math.max(0, DAILY_FREE_LIMIT - record.count);
  next();
};
