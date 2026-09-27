import unittest
from tools.load_vnredlist_freshwater_fish import category_links, parse_detail

class VnRedListLoaderTest(unittest.TestCase):
    def test_category_extracts_only_fish_detail_pages(self):
        html='<a href="http://vnredlist.vast.vn/channa-striata/">Fish</a><a href="/category/">Cat</a><a href="http://example.com/x">X</a>'
        self.assertEqual(["http://vnredlist.vast.vn/channa-striata/"], category_links(html))
    def test_detail_preserves_image_and_provenance(self):
        html='''<h1>Channa striata</h1><p>Tên tiếng Việt: Cá lóc</p><p>Tên khoa học: Channa striata</p><img src="http://vnredlist.vast.vn/wp-content/uploads/fish.jpg">'''
        row=parse_detail('http://vnredlist.vast.vn/channa-striata/',html)
        self.assertEqual('Channa striata',row['scientificName']); self.assertEqual('Cá lóc',row['vernacularName']); self.assertEqual('vnredlist',row['sourceId']); self.assertEqual(['http://vnredlist.vast.vn/wp-content/uploads/fish.jpg'],row['imageUrls'])

if __name__ == '__main__': unittest.main()
