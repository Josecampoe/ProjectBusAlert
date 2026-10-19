import { Request, Response } from 'express';

export async function getAllRoutes(req: Request, res: Response) {
  try {
    res.json({ success: true, routes: [] });
  } catch (error) {
    res.status(500).json({ error: 'Error al obtener rutas' });
  }
}
