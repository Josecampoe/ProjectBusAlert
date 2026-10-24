import { describe, expect, it } from 'vitest';
import {
  buildUserContent,
  parseAiJson,
  toResponseSchema,
  TripAnswerSchema,
  tripSystemInstruction,
} from './prompts';

const valid = {
  chosenOptionIndex: 0,
  spokenText: 'Toma la E1.',
  displayTitle: 'E1',
  displaySubtitle: 'Parque Bolívar',
};

describe('parseAiJson', () => {
  it('acepta JSON válido y recorta espacios', () => {
    expect(
      parseAiJson(JSON.stringify({ ...valid, displayTitle: '  E1 ' }), TripAnswerSchema),
    ).toEqual(valid);
  });

  it('acepta JSON dentro de un bloque ```json', () => {
    expect(parseAiJson('```json\n' + JSON.stringify(valid) + '\n```', TripAnswerSchema)).toEqual(
      valid,
    );
  });

  it.each([
    ['texto libre', 'Toma la E1'],
    ['JSON truncado', '{"chosenOptionIndex": 0'],
    ['índice negativo', JSON.stringify({ ...valid, chosenOptionIndex: -1 })],
    ['índice decimal', JSON.stringify({ ...valid, chosenOptionIndex: 0.5 })],
    ['campo vacío', JSON.stringify({ ...valid, spokenText: '   ' })],
    ['tipo incorrecto', JSON.stringify({ ...valid, displayTitle: 1 })],
  ])('rechaza %s', (_label, raw) => {
    expect(parseAiJson(raw, TripAnswerSchema)).toBeUndefined();
  });
});

describe('construcción del prompt', () => {
  it('declara el idioma y la ciudad en las instrucciones de sistema', () => {
    expect(tripSystemInstruction('en', 'Pasto')).toContain('English');
    expect(tripSystemInstruction('es', 'Pasto')).toContain('Pasto');
  });

  it('serializa contexto y datos como JSON delimitado', () => {
    const content = buildUserContent({ options: [] }, { destination: 'centro "x"' });
    expect(content).toBe(
      '<context>\n{"options":[]}\n</context>\n<user_data>\n{"destination":"centro \\"x\\""}\n</user_data>',
    );
  });

  it('genera un JSON Schema sin la meta-clave $schema', () => {
    const schema = toResponseSchema(TripAnswerSchema);
    expect(schema).not.toHaveProperty('$schema');
    expect(schema).toMatchObject({
      type: 'object',
      required: expect.arrayContaining(['chosenOptionIndex']),
    });
  });
});
