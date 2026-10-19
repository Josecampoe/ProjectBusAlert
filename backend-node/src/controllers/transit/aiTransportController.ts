import { Router, Request, Response } from 'express';
import { getDb, findStopsByName, StopRow } from '../../db/db';

export const aiTransportRouter = Router();

function buildTransportContext(question: string) {
    const db = getDb();
    const words = question.split(/\s+/).filter(w => w.length > 3);
    const stopSet = new Map<number, StopRow>();
    for (const word of words) {
        const found = findStopsByName(db, word);
        for (const s of found) stopSet.set(s.id, s);
    }
    return { query: question, stops: Array.from(stopSet.values()) };
}

aiTransportRouter.post('/', async (req: Request, res: Response) => {
    const { question } = req.body;
    const context = buildTransportContext(question || '');
    res.json({ question, context });
});
