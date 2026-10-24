"""Convierte data/raw/guia_rutas_setp.md en archivos de datos del backend.

Uso (desde backend-node/):  python scripts/import_guide.py data
Después: npm run data:validate && npm run seed

E1 no se toca (datos Ruti con códigos). Las paradas de la guía que ya existen
en E1 se enlazan a esas paradas; el resto se crean sin código.
"""
import json
import re
import sys
import unicodedata

DATA = sys.argv[1]
SOURCE = 'guia_rutas_setp'
TODAY = '2026-10-24'


def norm(s):
    s = unicodedata.normalize('NFD', s.lower())
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^a-z0-9]+', ' ', s).strip()


def load(p):
    return json.load(open(f'{DATA}/{p}', encoding='utf8'))


def save(p, obj):
    with open(f'{DATA}/{p}', 'w', encoding='utf8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


e1_stops = load('stops/stops_e1.json')
by_ext = {s['external_id']: s for s in e1_stops if s['external_id']}
by_name_uncoded = {s['name']: s for s in e1_stops if not s['external_id']}

# Nombre de la guía → parada existente de E1 (código Ruti, o nombre si no tiene código).
E1_LINKS = {
    'Vía La Laguna': '3915', 'Dolores': '4020', 'Corpominga': '4470', 'Mocondino': '4545',
    'Variante Daza': '4635', 'CAI Popular': '3590', 'Pinal del Río': '3055', 'Las Brisas': '2875',
    'Santa Mónica': '3095', 'Hospital Departamental': '3110', 'Parque Bolívar': '2990',
    'Fátima': '3040', 'San Juan Bosco': '2740', 'Parque Infantil': '1855',
    'Clínica Bellatriz': '1345', 'Villa del Parque': '1410', 'U. Cooperativa': '1360',
    'Udenar Torobajo': '1275', 'Udenar': '1275', 'Veterinaria': '1265', 'El Dorado': '1495',
    'Cedenar': '1475', 'Carmelitas': '2035', 'San Agustín': '2390', 'Bernal': '3295',
    'Las Mercedes': '3315', 'Villa María': '1420', 'Santacruz': '3160', 'Caicedonia': '3000',
    'Mercedario': '3195', 'Morasurco': '1240', 'Valle de Atriz': '1515', 'Alhambra': '2920',
    'María Goretti': '2585',
    'Federación de Cafeteros': 'Federación de Cafeteros', 'Uniminuto': 'Uniminuto Carrera 27',
    'Cl 16 Cra 27': 'Calle 16 Carrera 27',
}
# Alias que se agregan a paradas de E1 para buscarlas con el nombre de la guía.
E1_EXTRA_ALIASES = {
    '1265': ['Veterinaria'], '1360': ['U. Cooperativa'], '1375': ['U. Cooperativa'],
    '2585': ['María Goretti'], '2635': ['María Goretti'],
}
# Casos especiales de la guía: un texto → una o varias paradas (nombre, alias).
SPECIAL = {
    'Panamericana (Éxito)': [('Panamericana Éxito', ['Panamericana (Éxito)'])],
    'Panamericana (Atahualpa, Éxito)': [('Atahualpa', []), ('Panamericana Éxito', [])],
    'Cl 16 (Cra 22 a 33)': [('Calle 16', [])],
    'Terminal': [('Terminal de Transportes', [])],
    'Daza': [('Alto de Daza', [])],
    'Laureles': [('Laureles', ['Los Laureles'])],
    'Galerías Cra 27': [('Galerías Carrera 27', [])],
}
ABBREVIATIONS = [
    (r'\bCl\b', 'Calle'), (r'\bCra\b', 'Carrera'), (r'^Av\. ', 'Avenida '), (r'^U\. ', 'Universidad '),
]

e1_search = {}
for s in e1_stops:
    for n in [s['name'], *s.get('aliases', [])]:
        e1_search.setdefault(norm(n), s['external_id'] or s['name'])

new_stops = {}  # nombre → registro


def new_stop(name, aliases):
    if norm(name) in e1_search:
        raise SystemExit(f'"{name}" coincide con una parada de E1: agrégalo a E1_LINKS')
    rec = new_stops.setdefault(name, {
        'external_id': None, 'name': name, 'aliases': [], 'latitude': None, 'longitude': None,
        'address': None, 'neighborhood': None, 'active': True, 'source': SOURCE,
        'verified': True, 'notes': None,
    })
    for a in aliases:
        if a != name and a not in rec['aliases']:
            rec['aliases'].append(a)
    return {'stop_external_id': None, 'stop_name': name}


def resolve(raw):
    if raw in E1_LINKS:
        ref = E1_LINKS[raw]
        if ref in by_ext:
            return [{'stop_external_id': ref, 'stop_name': by_ext[ref]['name']}]
        assert ref in by_name_uncoded, ref
        return [{'stop_external_id': None, 'stop_name': ref}]
    if raw in SPECIAL:
        return [new_stop(n, a) for n, a in SPECIAL[raw]]
    name = raw
    for pattern, repl in ABBREVIATIONS:
        name = re.sub(pattern, repl, name)
    return [new_stop(name, [raw])]


text = open(f'{DATA}/raw/guia_rutas_setp.md', encoding='utf8').read()
blocks = re.split(r'\n(?=[EC]\d+ )', text)
routes, directions, route_stops = [], [], {}
for block in blocks:
    m = re.match(r'([EC]\d+) (.+)', block)
    if not m:
        continue
    code, title = m.group(1), m.group(2).strip()
    if code == 'E1':
        continue
    circular = '(circular)' in title
    clean_title = title.replace('(circular)', '').strip()
    origin, destination = [p.strip() for p in clean_title.split('–')]
    lines = {k: v for k, v in re.findall(r'^(Ida|Retorno|Tramo 1|Tramo 2): (.+?)\.?$', block, re.M)}
    note_lines = [ln.strip() for ln in block.splitlines()[1:]
                  if ln.strip() and not re.match(r'^(Ida|Retorno|Tramo \d):', ln.strip()) and not ln.startswith('#')]
    seqs = ([('D1', lines['Tramo 1'] + ', ' + lines['Tramo 2'])] if circular
            else [('D1', lines['Ida']), ('D2', lines['Retorno'])])
    routes.append({
        'code': code,
        'name': f'{code} — {origin} / {destination}' + (' (circular)' if circular else ''),
        'type': 'estrategica' if code.startswith('E') else 'complementaria',
        'description': None, 'origin': origin, 'destination': destination, 'active': True,
        'source': SOURCE, 'verified': True, 'data_quality': 'partial',
        'notes': ' '.join(['Paradas según la guía de rutas entregada por el dueño; sin códigos Ruti, coordenadas ni horarios.', *note_lines]),
        'last_verified_at': TODAY, 'color': None,
    })
    ida_entries = None
    for suffix, seq in seqs:
        entries = [e for raw in [x.strip() for x in re.split(r',(?![^()]*\))', seq)] for e in resolve(raw)]
        added = []
        if suffix == 'D1':
            ida_entries = entries
        else:
            # La guía no repite las terminales en el retorno: el bus sale de donde
            # terminó la ida y vuelve a donde la ida empezó.
            if entries[0] != ida_entries[-1]:
                entries = [ida_entries[-1], *entries]
                added.append(f'inicio ({ida_entries[-1]["stop_name"]})')
            if entries[-1] != ida_entries[0]:
                entries = [*entries, ida_entries[0]]
                added.append(f'final ({ida_entries[0]["stop_name"]})')
        key = f'{code}_{suffix}'
        name = f'{entries[0]["stop_name"]} → {entries[-1]["stop_name"]}' + (' (circular)' if circular else '')
        directions.append({
            'route_code': code, 'direction_key': key, 'name': name,
            'origin': entries[0]['stop_name'], 'destination': entries[-1]['stop_name'],
            'description': ('Recorrido circular.' if circular else ('Ida' if suffix == 'D1' else 'Retorno') + ' según la guía.'
                            + (' Se agregó la terminal de la ida al ' + ' y al '.join(added) + ', que la guía no repite.' if added else '')),
            'stop_count': len(entries), 'verified': True, 'source': SOURCE,
        })
        route_stops[key] = {'direction_key': key, 'route_code': code, 'name': name,
                            'stops': [{'order': i + 1, **e} for i, e in enumerate(entries)]}

# Escritura
sources = load('sources/sources.json')
sources = [s for s in sources if s['id'] != SOURCE] + [{
    'id': SOURCE, 'name': 'Guía de rutas SETP Pasto (entregada por el dueño del proyecto)', 'url': None,
    'source_type': 'verified_user_input', 'last_checked': TODAY, 'reliability': 'medium',
    'notes': 'Orden de paradas por sentido de las rutas E2–E7 y C1–C16. Sin códigos de parada, coordenadas ni horarios. Texto original en data/raw/guia_rutas_setp.md.',
}]
save('sources/sources.json', sources)

for s in e1_stops:
    for a in E1_EXTRA_ALIASES.get(s['external_id'], []):
        if a not in s['aliases']:
            s['aliases'].append(a)
save('stops/stops_e1.json', e1_stops)

save('stops/stops_guia_setp.json', sorted(new_stops.values(), key=lambda s: norm(s['name'])))

all_routes = [r for r in load('routes/routes.json') if r['code'] == 'E1'] + routes
save('routes/routes.json', all_routes)
all_dirs = [d for d in load('routes/route_directions.json') if d['route_code'] == 'E1'] + directions
save('routes/route_directions.json', all_dirs)
rs = load('routes/route_stops.json')
rs = {k: v for k, v in rs.items() if k.startswith('_') or v['route_code'] == 'E1'}
rs['_comment'] = ('Orden exacto de paradas por dirección. stop_external_id = código Ruti (null = sin código). '
                  'E1: Ruti SETP. E2–E7 y C1–C16: guía de rutas (data/raw/guia_rutas_setp.md).')
rs.update(route_stops)
save('routes/route_stops.json', rs)
print(f'rutas nuevas: {len(routes)}, sentidos: {len(directions)}, paradas nuevas: {len(new_stops)}')
