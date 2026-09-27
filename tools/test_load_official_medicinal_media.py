import unittest
from tools.load_official_medicinal_media import canonical_license, rows


class OfficialMedicinalTests(unittest.TestCase):
    def test_normalizes_gbif_cc_license_urls(self):
        self.assertEqual(canonical_license('http://creativecommons.org/licenses/by/4.0/'),'CC-BY-4.0')
    def test_restricts_to_official_listing_and_vietnam_licensed_media(self):
        evidence={'records':[{'sourceId':'moh-traditional-medicine','evidenceClass':'OFFICIAL_LISTING',
                 'scientificName':'Zingiber officinale Roscoe','evidenceId':'moh-ginger',
                 'sourceUrl':'https://emohbackup.moh.gov.vn/official.pdf'},
                 {'sourceId':'ema-hmpc','evidenceClass':'REGULATORY_MONOGRAPH',
                  'scientificName':'Other species','evidenceId':'ema-other'}]}
        occurrence={'key':1,'countryCode':'VN','kingdom':'Plantae','species':'Zingiber officinale',
                    'license':'CC0-1.0','media':[{'type':'StillImage','license':'CC-BY-4.0',
                    'identifier':'https://example.org/photo.jpg'}]}
        items=rows(evidence,lambda _: {'results':[occurrence]})
        self.assertEqual(len(items),1)
        self.assertEqual(items[0]['libraryGroup'],'Cây thuốc')
        self.assertNotIn('medicalClaims',items[0])
        self.assertEqual(rows(evidence,lambda _: {'results':[{**occurrence,'countryCode':'US'}]}),[])


if __name__=='__main__':unittest.main()
