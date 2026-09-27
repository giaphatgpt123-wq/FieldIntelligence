#!/usr/bin/env python3
"""Strict gate for Vietnamese medicinal-plant records before offline packaging."""
from __future__ import annotations
import argparse, gzip, json
from pathlib import Path
from urllib.parse import urlparse

ALLOWED_SOURCES={"tracuuduoclieu","gbif","eol","commons-wikidata","wfo-taxonomic-backbone","moh-traditional-medicine"}
MEDICAL_FIELDS={"uses","medicalClaims","treatment","dosage","contraindications","interactions","toxicity"}

def rows(path):
    opener=gzip.open if path.suffix=='.gz' else open
    with opener(path,'rt',encoding='utf-8') as f:
        for n,line in enumerate(f,1):
            if line.strip(): yield n,json.loads(line)

def validate(r):
    errors=[]; p=r.get('provenance') or {}; sid=str(r.get('sourceId') or '').strip(); url=str(r.get('sourceRecordUrl') or p.get('sourceUrl') or '').strip()
    if r.get('libraryGroup') != 'Cây thuốc': errors.append('wrong-library-group')
    for k in ('scientificName','sourceRecordId'):
        if not str(r.get(k) or '').strip(): errors.append('missing-'+k)
    if sid not in ALLOWED_SOURCES: errors.append('unapproved-source')
    if not url or urlparse(url).scheme not in ('https','http') or not urlparse(url).netloc: errors.append('missing-source-url')
    if not str(p.get('license') or r.get('sourceLicense') or '').strip(): errors.append('missing-license')
    if str(p.get('license') or r.get('sourceLicense') or '').strip() in {'site-terms-review-required','requires-permission-or-site-terms','site-terms'}: errors.append('reuse-rights-unconfirmed')
    if sid=='moh-traditional-medicine':
        if urlparse(url).hostname!='emohbackup.moh.gov.vn' or p.get('license')!='factual-taxonomy-metadata-only':
            errors.append('invalid-official-listing-provenance')
        if any(k in r for k in MEDICAL_FIELDS):errors.append('official-listing-cannot-publish-medical-claims')
    if any(k in r for k in MEDICAL_FIELDS):
        if not p.get('medicalReviewRequired'): errors.append('medical-claim-without-review-flag')
        if not p.get('medicalEvidenceSource'): errors.append('medical-claim-without-independent-evidence')
    return errors

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--input',required=True,type=Path); ap.add_argument('--output',required=True,type=Path); a=ap.parse_args()
    accepted=[]; rejected=[]
    for n,r in rows(a.input):
        e=validate(r)
        (accepted if not e else rejected).append({'line':n,'record':r,'errors':e})
    out={'schemaVersion':1,'policy':'strict-medicinal-plant-offline-gate','acceptedCount':len(accepted),'rejectedCount':len(rejected),'rejected':[{'line':x['line'],'errors':x['errors'],'sourceRecordId':x['record'].get('sourceRecordId','')} for x in rejected]}
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if rejected or not accepted: raise SystemExit(f'gate failed: accepted={len(accepted)} rejected={len(rejected)}')
    print(json.dumps(out,ensure_ascii=False))
if __name__=='__main__': main()
