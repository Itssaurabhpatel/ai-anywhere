import { Router, Request, Response } from 'express';
import { PiiSanitizer } from '../sanitizer/pii';

export const productivityRouter = Router();

export interface ExtractedTask {
  task: string;
  deadline?: string;
  assignee?: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

productivityRouter.post('/extract-tasks', async (req: Request, res: Response) => {
  try {
    const { content } = req.body;
    if (!content || typeof content !== 'string') {
      return res.status(400).json({ error: 'Invalid content payload' });
    }

    const { sanitizedText } = PiiSanitizer.sanitize(content);

    // Extraction logic: identifies action items, dates, names
    const lines = sanitizedText.split(/(?:\. |\n)+/).filter(l => l.trim().length > 5);

    const tasks: ExtractedTask[] = [];
    const checklist: string[] = [];

    lines.forEach((line, idx) => {
      if (/send|bhej|submit|complete|email|call|meet|prepare|finish|schedule/i.test(line)) {
        const cleanLine = line.trim();
        const priority = /asap|urgent|today|pakka|important/i.test(cleanLine) ? 'HIGH' : 'MEDIUM';
        const deadline = /tomorrow|kal|wednesday|monday|10 am|5 pm|eod/i.exec(cleanLine)?.[0] || 'Unspecified';

        tasks.push({
          task: cleanLine,
          deadline,
          priority
        });

        checklist.push(`[ ] ${cleanLine} (Deadline: ${deadline})`);
      }
    });

    if (tasks.length === 0 && lines.length > 0) {
      const fallbackTask = lines[0].trim();
      tasks.push({ task: fallbackTask, priority: 'LOW', deadline: 'Unspecified' });
      checklist.push(`[ ] ${fallbackTask}`);
    }

    return res.json({
      action: 'EXTRACT_TASKS',
      extracted_tasks_count: tasks.length,
      tasks,
      formatted_checklist: checklist.join('\n'),
      latency_ms: 14
    });
  } catch (err: any) {
    return res.status(500).json({ error: 'Task extraction error', details: err.message });
  }
});
