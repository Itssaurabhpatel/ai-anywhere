/**
 * Phase 2 Vector Style Embedding Engine
 * Calculates style similarity scores using cosine vector distance to match user preferences.
 */
export interface StyleVector {
  formality: number;    // Normalized 0.0 - 1.0
  verbosity: number;    // Normalized 0.0 - 1.0
  emojiDensity: number; // Normalized 0.0 - 1.0
  hinglishWeight: number; // Normalized 0.0 - 1.0
}

export class VectorStyleEngine {
  public static calculateCosineSimilarity(vecA: number[], vecB: number[]): number {
    let dotProduct = 0.0;
    let normA = 0.0;
    let normB = 0.0;

    for (let i = 0; i < vecA.length; i++) {
      dotProduct += vecA[i] * vecB[i];
      normA += vecA[i] * vecA[i];
      normB += vecB[i] * vecB[i];
    }

    if (normA === 0 || normB === 0) return 0.0;
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  public static styleToVector(style: { formality_score?: number; verbosity?: string; emoji_policy?: string }): number[] {
    const formalityNorm = ((style.formality_score ?? 3) - 1) / 4.0;
    const verbosityNorm = style.verbosity === 'concise' ? 0.2 : style.verbosity === 'detailed' ? 0.9 : 0.5;
    const emojiNorm = style.emoji_policy === 'none' ? 0.0 : style.emoji_policy === 'heavy' ? 1.0 : 0.4;
    const hinglishNorm = 0.7; // High default preference for regional blend

    return [formalityNorm, verbosityNorm, emojiNorm, hinglishNorm];
  }

  public static adaptPrompt(systemPrompt: string, styleVector: number[]): string {
    const [formality, verbosity, emoji, hinglish] = styleVector;
    return `${systemPrompt}
    [VECTOR STYLE INSTRUCTION]: FormalityLevel=${(formality * 100).toFixed(0)}%, VerbosityTarget=${verbosity < 0.4 ? 'Ultra-Short' : 'Standard'}, EmojiDensity=${emoji > 0.7 ? 'High' : 'Minimal'}, RegionalBlendWeight=${hinglish > 0.5 ? 'Active' : 'Disabled'}.`;
  }
}
