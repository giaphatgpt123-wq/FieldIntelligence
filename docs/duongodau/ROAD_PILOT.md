# Road discovery pilot — P1 first slice

- Source: OpenStreetMap API 0.6 bounded map request recorded in `road_pilot_bbox.json`.
- Bounds: 10.33–10.35 N, 105.57–105.60 E. This is a small discovery sample, not a complete province or commune inventory.
- Extraction: named `way` elements with a `highway` tag; 16 distinct way IDs. Keep individual ways rather than pretending equal names imply a connected route.
- Provenance: source XML SHA-256, extraction time, OSM way ID/URL and per-way edit timestamp are embedded in the asset. Reproduction requires fetching the recorded request and comparing source SHA before extracting.
- License: ODbL 1.0; show `© OpenStreetMap contributors` in the app. A future data redistribution must retain attribution and satisfy ODbL obligations.
- Verification: `UNVERIFIED`. OSM road tags do not establish legal access, actual surface, bridge/ferry capacity or vehicle feasibility. Missing tags remain unknown.
- Administrative scope: the box has not been intersected with verified current commune polygons. The UI does not assign its ways to any commune. Selecting another province hides the sample; selecting any commune blocks the result until intersection is verified.
- Routing: no route graph or safety decision is computed from this pack. The old synthetic assessment and drawn Road View are not rendered.

Next gate: acquire licensed polygon/adjacency data, assign segments by geometry with a traceable source, then broaden extraction in bounded regional packs and test route feasibility separately.
