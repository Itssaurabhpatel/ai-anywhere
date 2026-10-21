export interface WritingStylePreferences {
  formalityScore: number; // 1 (Casual) to 5 (Formal)
  preferredLanguages: string[]; // e.g. ['en', 'hinglish']
  verbosity: 'concise' | 'balanced' | 'detailed';
  emojiPolicy: 'none' | 'sparse' | 'heavy';
  customSamples?: string[];
}

export class PromptBuilder {
  public static buildPrompt(
    actionType: string,
    inputText: string,
    style?: WritingStylePreferences,
    targetTone?: string,
    targetLanguage?: string,
    customInstruction?: string
  ): { systemPrompt: string; userPrompt: string } {
    const formality = style?.formalityScore ?? 3;
    const emojiPolicy = style?.emojiPolicy ?? 'sparse';
    const verbosity = style?.verbosity ?? 'balanced';

    const systemPrompt = `You are AI Everywhere, an ambient mobile ghostwriting assistant.
Your job is to transform text for the user directly inside their messaging or typing workflow.

CRITICAL CONSTRAINTS:
1. Output ONLY the target text. Never output conversational filler (e.g. "Here is your response:", "Sure!").
2. Match the requested tone and personal style preferences below.
3. Keep response length appropriate for mobile messaging.

USER STYLE PREFERENCES:
- Formality Level (1=Casual, 5=Formal): ${formality}
- Verbosity: ${verbosity}
- Emoji Density: ${emojiPolicy}
- Preferred Tone: ${targetTone ?? 'Neutral'}
${targetLanguage ? `- Target Language: ${targetLanguage}` : ''}`;

    let userPrompt = '';

    switch (actionType) {
      case 'FIX_GRAMMAR':
        userPrompt = `Correct all grammar, spelling, punctuation, and awkward phrasing in the following text while keeping its exact original meaning:\n\n"${inputText}"`;
        break;

      case 'SMART_REPLY':
        userPrompt = `Generate 3 short, distinct smart replies to this incoming message. Separate each reply with a "|||" delimiter:\n\n"${inputText}"`;
        break;

      case 'REWRITE':
        userPrompt = `Rewrite the following text to make it sound natural, clear, and well-written:\n\n"${inputText}"`;
        break;

      case 'TRANSLATE':
        userPrompt = `Translate the following text into ${targetLanguage ?? 'English'}, maintaining natural conversational flow:\n\n"${inputText}"`;
        break;

      case 'TONE_CHANGE':
        userPrompt = `Morph the tone of the following text to sound strictly ${targetTone ?? 'Professional'}:\n\n"${inputText}"`;
        break;

      case 'SUMMARIZE':
        userPrompt = `Provide a ultra-concise 2-bullet point summary of the following text:\n\n"${inputText}"`;
        break;

      case 'CUSTOM_PROMPT':
        if (!inputText || inputText.trim().length === 0) {
          userPrompt = `${customInstruction || 'Hello'}`;
        } else {
          userPrompt = `Process the following text according to this instruction: "${customInstruction || 'Improve this text'}".\n\nText:\n"${inputText}"`;
        }
        break;

      default:
        userPrompt = `Improve the following text:\n\n"${inputText}"`;
    }

    return { systemPrompt, userPrompt };
  }
}
