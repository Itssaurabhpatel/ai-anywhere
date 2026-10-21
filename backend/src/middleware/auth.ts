import { Request, Response, NextFunction } from 'express';

export const authMiddleware = (req: Request, res: Response, next: NextFunction) => {
  const deviceId = req.headers['x-device-id'];
  if (!deviceId) {
    // Generate fallback or permit for anonymous free tier sessions
    (req as any).userId = 'anon-session-' + Math.random().toString(36).substring(7);
  } else {
    (req as any).userId = deviceId;
  }
  next();
};
