import { GoogleGenerativeAI } from '@google/generative-ai';

const apiKey = process.env.GEMINI_API_KEY || '';
const genAI = new GoogleGenerativeAI(apiKey);

export const TRANSIT_SYSTEM_PROMPT = `
Eres BusAlert Assistant, un asistente experto de transporte publico.
Tu mision es orientar al usuario sobre que ruta tomar, paradas cercanas y alertas de abordaje.
`;

export async function generateTransitAdvice(prompt: string): Promise<string> {
  const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });
  const result = await model.generateContent(prompt);
  return result.response.text();
}
