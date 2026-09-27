#!/usr/bin/env python3
"""Build taxonomy-only medicinal entries from official MOH listings and licensed GBIF media."""
import argparse
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path

LICENSES = {'CC0-1.0','CC-BY-4.0','CC-BY-NC-4.0',
            'https://creativecommons.org/publicdomain/zero/1.0/',
            'https://creativecommons.org/licenses/by/4.0/',
            'https://creativecommons.org/licenses/by-nc/4.0/'}

API = 'https://api.gbif.org/v1/occurrence/search'


def rows(evidence, fetch):
    result=[]
    seen=set()
    for item in evidence['records']:
        if item.get('sourceId')!='moh-traditional-medicine' or item.get('evidenceClass')!='OFFICIAL_LISTING':
            continue
        match=re.match(r'^([A-Z][a-z]+\s+[a-z][a-z-]+)',item.get('scientificName',''))
        if not match or match.group(1) in seen:continue
        species=match.group(1)
        seen.add(species)
        query=urllib.parse.urlencode({'country':'VN','scientificName':species,'mediaType':'StillImage','limit':300})
        media=[]
        for occ in fetch(f'{API}?{query}').get('results',[]):
            if occ.get('countryCode')!='VN' or occ.get('kingdom')!='Plantae' or occ.get('license') not in LICENSES:
                continue
            if occ.get('species')!=species:
                continue
            for image in occ.get('media') or []:
                url=image.get('identifier') or ''
                if url.startswith('https://') and image.get('license') in LICENSES and str(image.get('type') or '').casefold() in ('stillimage','image'):
                    media.append({'identifier':url,'mediaType':'StillImage','license':image['license'],
                                  'references':f"https://www.gbif.org/occurrence/{occ['key']}",
                                  'creator':image.get('creator') or '', 'rightsHolder':image.get('rightsHolder') or '',
                                  'sourceProvider':'GBIF occurrence media','gbifOccurrenceKey':str(occ['key'])})
            if media:break
        if not media:continue
        source=item['sourceUrl']
        result.append({'schemaVersion':1,'sourceId':'moh-traditional-medicine','sourceRecordId':item['evidenceId'],
                       'scientificName':species,'kingdom':'Plantae','libraryGroup':'Cây thuốc',
                       'sourceRecordUrl':source,'mediaItems':media,
                       'provenance':{'authority':'Bộ Y tế Việt Nam','license':'factual-taxonomy-metadata-only',
                                     'scope':'official-listing-and-licensed-reference-media','sourceUrl':source,
                                     'medicalReviewRequired':True}})
    return result


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    def fetch(url):
        req=urllib.request.Request(url,headers={'User-Agent':'FieldIntelligence/1.0 (scientific-library)'})
        with urllib.request.urlopen(req,timeout=30) as response:return json.load(response)
    items=rows(json.loads(args.input.read_text(encoding='utf-8')),fetch)
    if not items:raise SystemExit('No licensed medicinal reference images: refusing empty output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(''.join(json.dumps(item,ensure_ascii=False)+'\n' for item in items),encoding='utf-8')
    print(json.dumps({'officialMedicinalCandidates':len(items)}))


if __name__=='__main__':main()
