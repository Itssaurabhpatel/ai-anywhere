import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { ENV } from './config/env';
import { aiRouter } from './routes/ai';
import { userRouter } from './routes/user';
import { contextRouter } from './routes/context';
import { translateRouter } from './routes/translate';
import { productivityRouter } from './routes/productivity';
import { voiceRouter } from './routes/voice';
import { billingRouter } from './routes/billing';
import { authMiddleware } from './middleware/auth';

const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use(authMiddleware);

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'online',
    service: 'AI Everywhere Orchestration Gateway',
    mock_mode: ENV.MOCK_AI_MODE,
    version: '3.0.0-phase3',
    timestamp: new Date().toISOString()
  });
});

// API Routes
app.use('/v1/ai', aiRouter);
app.use('/v1/user', userRouter);
app.use('/v1/ai/context', contextRouter);
app.use('/v1/ai/translate', translateRouter);
app.use('/v1/ai/productivity', productivityRouter);
app.use('/v1/ai/voice', voiceRouter);
app.use('/v1/billing', billingRouter);

app.listen(ENV.PORT, () => {
  console.log(`=======================================================`);
  console.log(`✨ AI Everywhere Orchestration Gateway Running (Phase 3 Complete)`);
  console.log(`📍 Port: ${ENV.PORT}`);
  console.log(`⚙️  Environment: ${ENV.NODE_ENV}`);
  console.log(`🤖 Mock AI Engine Mode: ${ENV.MOCK_AI_MODE}`);
  console.log(`=======================================================`);
});
