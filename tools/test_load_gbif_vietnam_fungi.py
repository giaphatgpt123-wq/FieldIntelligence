import unittest
from tools.load_gbif_vietnam_fungi import collect, normalize


class FungiLoaderTests(unittest.TestCase):
    def test_accepts_only_vietnam_fungi_with_licensed_media(self):
        occurrence={'key':123,'countryCode':'VN','kingdom':'Fungi','species':'Phylloporia vietnamensis',
                    'speciesKey':456,'license':'CC0-1.0','media':[{'identifier':'https://example.org/photo.jpg',
                    'type':'StillImage','license':'CC-BY-4.0','creator':'A'}]}
        row=normalize(occurrence)
        self.assertEqual((row['libraryGroup'],row['sourceRecordId']),('Nấm','456'))
        self.assertEqual(row['mediaItems'][0]['gbifOccurrenceKey'],'123')
        self.assertIsNone(normalize({**occurrence,'countryCode':'US'}))
        self.assertIsNone(normalize({**occurrence,'media':[{'identifier':'https://example.org/x.jpg','type':'StillImage','license':'all rights reserved'}]}))

    def test_deduplicates_species(self):
        occ={'key':123,'countryCode':'VN','kingdom':'Fungi','species':'Phylloporia vietnamensis',
             'speciesKey':456,'license':'CC0-1.0','media':[{'identifier':'https://example.org/a.jpg',
             'type':'StillImage','license':'CC0-1.0'}]}
        self.assertEqual(len(collect(2,lambda _: {'results':[occ,occ],'endOfRecords':True})),1)


if __name__ == '__main__': unittest.main()
