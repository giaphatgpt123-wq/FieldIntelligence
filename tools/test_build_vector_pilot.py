import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from build_vector_pilot import build


OSM_XML = """<osm version="0.6">
<node id="1" lat="10.38" lon="105.62"><tag k="place" v="village"/><tag k="name" v="Test"/></node>
<node id="2" lat="10.39" lon="105.63"/>
<node id="3" lat="10.40" lon="105.64"/>
<node id="4" lat="11.00" lon="106.00"/>
<way id="5"><nd ref="1"/><nd ref="2"/><nd ref="3"/><nd ref="4"/><tag k="highway" v="residential"/></way>
<way id="6"><nd ref="1"/><nd ref="2"/><tag k="waterway" v="stream"/></way>
</osm>"""


class VectorPilotTest(unittest.TestCase):
    def test_pack_has_verified_geometry_and_provenance(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "tiny.osm"
            source.write_text(OSM_XML, encoding="utf-8")
            out = root / "out"
            manifest = build(source, out, "test-region", (10.2, 105.4, 10.6, 105.9), 3)
            self.assertEqual(manifest["version"], 3)
            self.assertEqual(json.loads((out / "provenance.json").read_text())["roadSegments"], 1)
            with zipfile.ZipFile(out / "offline-map.pack") as archive:
                self.assertEqual(set(archive.namelist()), {"test-region.region", "test-region.lines", "test-region.points"})
                self.assertEqual(archive.read("test-region.lines").decode().strip(), "10.380000,105.620000;10.390000,105.630000;10.400000,105.640000")

    def test_invalid_bounds_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "tiny.osm"
            source.write_text(OSM_XML, encoding="utf-8")
            with self.assertRaises(ValueError):
                build(source, root / "out", "test-region", (10.6, 105.4, 10.2, 105.9), 3)


if __name__ == "__main__":
    unittest.main()
