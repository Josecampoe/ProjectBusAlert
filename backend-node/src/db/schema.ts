// Interfaces y contratos para la base de datos de tránsito BusAlert

export interface Route {
  id: string;
  name: string;
  short_name?: string;
  description?: string;
  type?: string;
  color?: string;
  created_at?: string;
}

export interface RouteDirection {
  id: string;
  route_id: string;
  direction_name: string;
  headsign: string;
}

export interface Stop {
  id: string;
  name: string;
  lat: number;
  lng: number;
  address?: string;
  zone?: string;
}

export interface RouteStop {
  route_id: string;
  stop_id: string;
  stop_sequence: number;
  direction_id?: string;
}

export interface Schedule {
  id: string;
  route_id: string;
  day_type: string;
  departure_time: string;
  arrival_time: string;
  frequency_minutes?: number;
}
