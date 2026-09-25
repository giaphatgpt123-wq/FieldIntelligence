# Offline OSM vector pilot

This is a **bounded road schematic**, not a navigation map or a complete offline basemap. The existing Compose canvas has no terrain, addresses, route rules or road names. Do not bundle this pilot into the APK or publish it as a production update without device QA.

## Source and license

- Download the current Vietnam `.osm.pbf` from [Geofabrik](https://download.geofabrik.de/asia/vietnam.html). Record its download date and SHA-256. The full country extract is larger than the app's 64 MB package limit, so cut a small area first.
- OpenStreetMap data is [ODbL 1.0](https://www.openstreetmap.org/copyright). Any distributed derivative must provide attribution and meet the database license conditions. The builder writes `provenance.json` outside the ZIP; a future app installer must retain provenance and display attribution in the map UI.
- Use [osmium extract](https://docs.osmcode.org/osmium/latest/osmium-extract.html) for a bounding box and [osmium cat](https://docs.osmcode.org/osmium/latest/osmium-cat.html) to convert the small extract to XML.

## Local pilot only

Example coordinates below form an arbitrary small rectangle around the location in the device screenshot. They are **not** a validated region boundary.

```bash
osmium extract -b 105.4,10.2,105.9,10.6 vietnam-latest.osm.pbf -o pilot.osm.pbf
osmium cat pilot.osm.pbf -o pilot.osm
python3 tools/build_vector_pilot.py pilot.osm dist/pilot --id pilot-105-10 --bbox 10.2,105.4,10.6,105.9 --version 1
python3 -m unittest discover -s tools -p 'test_*.py'
```

The tool emits `offline-map.pack`, `manifest.json` and `provenance.json`. It accepts nodes and ways in standard OSM order, keeps only road segments whose vertices are inside the rectangle, excludes unrelated ways, and refuses excessive geometry or oversized files. Roads that cross the boundary can be clipped into incomplete segments; the result must not be used for route guidance.

## Gates before using real map data

1. Validate the source SHA, resulting package SHA and attribution. Audit roads, bridges, access restrictions and missing connections against source data.
2. Test memory, drawing time, pan/zoom and region coverage on a physical phone. The current canvas redraws line segments directly and is not a vector tile renderer.
3. Replace the pilot with an indexed offline tile engine and a manageable per-region distribution format. MapLibre Native Android has an [offline API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html), but integration and a compatible licensed tile source remain separate work.
4. Keep the private repository private. Remote package delivery needs an approved data-only host or safe backend; the built-in update channel remains disabled.
