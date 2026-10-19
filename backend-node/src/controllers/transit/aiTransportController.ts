import { Router, Request, Response } from 'express';

export const aiTransportRouter = Router();

aiTransportRouter.post('/', async (req: Request, res: Response) => {
    res.json({ message: 'AI Transport controller initialized' });
});
