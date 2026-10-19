import { Request, Response } from 'express';

export async function planTrip(req: Request, res: Response) {
  try {
    res.json({ success: true, message: 'Planificador de viaje inicializado' });
  } catch (error) {
    res.status(500).json({ error: 'Error al planificar viaje' });
  }
}
