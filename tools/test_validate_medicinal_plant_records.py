import json,tempfile,unittest
from pathlib import Path
from tools.validate_medicinal_plant_records import validate
class TestStrictGate(unittest.TestCase):
 def test_accepts_taxonomy_record_with_source_and_license(self):
  self.assertEqual(validate({'libraryGroup':'Cây thuốc','scientificName':'Curcuma longa','sourceRecordId':'x','sourceRecordUrl':'https://tracuuduoclieu.vn/x','sourceId':'tracuuduoclieu','sourceLicense':'CC-BY-4.0','provenance':{'license':'CC-BY-4.0'}}),[])
 def test_blocks_unconfirmed_reuse(self):
  self.assertIn('reuse-rights-unconfirmed',validate({'libraryGroup':'Cây thuốc','scientificName':'Curcuma longa','sourceRecordId':'x','sourceRecordUrl':'https://tracuuduoclieu.vn/x','sourceId':'tracuuduoclieu','provenance':{'license':'site-terms-review-required'}}))
 def test_rejects_medical_claim_without_independent_review(self):
  self.assertIn('medical-claim-without-independent-evidence',validate({'libraryGroup':'Cây thuốc','scientificName':'X','sourceRecordId':'x','sourceRecordUrl':'https://example.org/x','sourceId':'tracuuduoclieu','provenance':{'license':'site-terms'},'medicalClaims':'claim'}))
if __name__=='__main__': unittest.main()
