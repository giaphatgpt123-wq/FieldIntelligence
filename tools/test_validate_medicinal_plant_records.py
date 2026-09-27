import json,tempfile,unittest
from pathlib import Path
from tools.validate_medicinal_plant_records import validate
class TestStrictGate(unittest.TestCase):
 def test_accepts_taxonomy_record_with_source_and_license(self):
  self.assertEqual(validate({'libraryGroup':'Cây thuốc','scientificName':'Curcuma longa','sourceRecordId':'x','sourceRecordUrl':'https://tracuuduoclieu.vn/x','sourceId':'tracuuduoclieu','sourceLicense':'site-terms','provenance':{'license':'site-terms'}}),[])
 def test_rejects_medical_claim_without_independent_review(self):
  self.assertIn('medical-claim-without-independent-evidence',validate({'libraryGroup':'Cây thuốc','scientificName':'X','sourceRecordId':'x','sourceRecordUrl':'https://example.org/x','sourceId':'tracuuduoclieu','provenance':{'license':'site-terms'},'medicalClaims':'claim'}))
if __name__=='__main__': unittest.main()
