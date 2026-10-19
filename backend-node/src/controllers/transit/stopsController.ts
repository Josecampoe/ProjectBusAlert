import { Request, Response } from 'express';

export async function getAllStops(req: Request, res: Response) {
  try {
    res.json({ success: true, stops: [] });
  } catch (error) {
    res.status(500).json({ error: 'Error al obtener paradas' });
  }
}
