import unittest

from tools.load_tracuuduoclieu_medicinal_plants import links, parse


class MedicinalPageTests(unittest.TestCase):
    def test_index_and_page_extract_only_taxonomy(self):
        url = "https://tracuuduoclieu.vn/schefflera-alongensis-r-vig.html"
        self.assertEqual(links('<a href="/schefflera-alongensis-r-vig.html">Cây thuốc</a>'), [url])
        page = '<h1>Schefflera alongensis R. Vig.</h1><p>Tên tiếng Việt: Chân chim hạ long Tên khoa học: Schefflera alongensis R. Vig. Họ: Araliaceae Công dụng: chỉ dùng kiểm thử</p>'
        record = parse(url, page)
        self.assertEqual(record['scientificName'], 'Schefflera alongensis')
        self.assertEqual(record['vernacularName'], 'Chân chim hạ long')
        self.assertNotIn('medicalClaims', record)
        self.assertNotIn('mediaItems', record)


if __name__ == '__main__':
    unittest.main()
