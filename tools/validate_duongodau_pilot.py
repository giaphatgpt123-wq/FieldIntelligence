"""Fail closed on malformed or mislabelled bounded road discovery data."""
import json
import re
from pathlib import Path

asset = Path('duongodau-app/src/main/assets/road_pilot_bbox.json')
data = json.loads(asset.read_text(encoding='utf-8'))
assert data['schemaVersion'] == 1
assert data['scope'] == 'BBOX_ONLY_UNMAPPED_ADMIN'
assert data['verification'] == 'UNVERIFIED'
assert data['license'] == 'ODbL 1.0'
assert data['attribution'] == '© OpenStreetMap contributors'
assert data['bbox'] == [10.33, 105.57, 10.35, 105.60]
assert re.fullmatch(r'[0-9a-f]{64}', data['sourceSha256'])
assert data['sourceRequest'].startswith('https://api.openstreetmap.org/api/0.6/map?bbox=')
roads = data['roads']
assert len(roads) == 16, len(roads)
assert len({road['id'] for road in roads}) == len(roads)
for road in roads:
    assert re.fullmatch(r'osm-way-[0-9]+', road['id'])
    assert road['name'].strip() and road['roadClass'].strip()
    assert road['sourceUrl'] == 'https://www.openstreetmap.org/way/' + road['id'][8:]
    assert road['sourceTimestamp']
    assert 'verified' not in road and 'passability' not in road
print(f'ROAD_PILOT_OK roads={len(roads)} scope={data["scope"]}')
