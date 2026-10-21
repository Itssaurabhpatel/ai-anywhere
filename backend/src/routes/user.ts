import { Router, Request, Response } from 'express';

export const userRouter = Router();

// In-memory mock storage for user writing style preferences
const styleStore = new Map<string, any>();

userRouter.get('/style', (req: Request, res: Response) => {
  const deviceId = (req.headers['x-device-id'] as string) || 'default';
  const userStyle = styleStore.get(deviceId) || {
    formality_score: 3,
    verbosity: 'balanced',
    emoji_policy: 'sparse',
    preferred_languages: ['en', 'hinglish']
  };
  return res.json({ style: userStyle });
});

userRouter.post('/style', (req: Request, res: Response) => {
  const deviceId = (req.headers['x-device-id'] as string) || 'default';
  const { formality_score, verbosity, emoji_policy, preferred_languages, custom_samples } = req.body;

  const updatedStyle = {
    formality_score: formality_score ?? 3,
    verbosity: verbosity ?? 'balanced',
    emoji_policy: emoji_policy ?? 'sparse',
    preferred_languages: preferred_languages ?? ['en'],
    custom_samples: custom_samples ?? []
  };

  styleStore.set(deviceId, updatedStyle);
  return res.json({ status: 'success', style: updatedStyle });
});
