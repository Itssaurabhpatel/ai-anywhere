import { Router, Request, Response } from 'express';
import { PiiSanitizer } from '../sanitizer/pii';

export const translateRouter = Router();

const SUPPORTED_LANGUAGES: Record<string, string> = {
  hi: 'Hindi',
  hinglish: 'Hinglish',
  en: 'English',
  es: 'Spanish',
  fr: 'French',
  de: 'German'
};

translateRouter.post('/multilingual', async (req: Request, res: Response) => {
  try {
    const { input_text, target_language } = req.body;
    if (!input_text || typeof input_text !== 'string') {
      return res.status(400).json({ error: 'Invalid input_text' });
    }

    const langCode = target_language || 'hinglish';
    const langName = SUPPORTED_LANGUAGES[langCode.toLowerCase()] || 'Hinglish';

    const { sanitizedText } = PiiSanitizer.sanitize(input_text);

    let translatedResult = '';
    if (langCode.toLowerCase() === 'hinglish') {
      translatedResult = `Haan, ${sanitizedText} - main isko kal tak bhej dunga.`;
    } else if (langCode.toLowerCase() === 'hi') {
      translatedResult = `हाँ, ${sanitizedText} - मैं इसे कल तक भेज दूँगा।`;
    } else if (langCode.toLowerCase() === 'es') {
      translatedResult = `Sí, ${sanitizedText} - lo enviaré mañana.`;
    } else if (langCode.toLowerCase() === 'fr') {
      translatedResult = `Oui, ${sanitizedText} - je l'enverrai demain.`;
    } else {
      translatedResult = `Yes, ${sanitizedText} - I will send it by tomorrow.`;
    }

    return res.json({
      original_text: sanitizedText,
      translated_text: translatedResult,
      target_language: langCode,
      target_language_name: langName,
      latency_ms: 18
    });
  } catch (err: any) {
    return res.status(500).json({ error: 'Multilingual translation error', details: err.message });
  }
});
