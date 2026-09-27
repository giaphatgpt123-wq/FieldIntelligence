#!/usr/bin/env python3
"""Collect Vietnamese fungal occurrence evidence and licensed image candidates from GBIF.

This produces taxonomy and provenance only. It never infers identity, edibility or toxicity.
"""
import argparse
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path

API = 'https://api.gbif.org/v1/occurrence/search'
LICENSES = {'CC0-1.0', 'CC-BY-4.0', 'CC-BY-NC-4.0',
            'https://creativecommons.org/publicdomain/zero/1.0/',
            'https://creativecommons.org/licenses/by/4.0/',
            'https://creativecommons.org/licenses/by-nc/4.0/'}


def canonical_license(value):
    raw=str(value or '').strip()
    if raw in LICENSES:return raw
    match=re.fullmatch(r'https?://creativecommons\.org/(publicdomain/zero/1\.0|licenses/by/4\.0|licenses/by-nc/4\.0)/(?:legalcode)?/?',raw,re.I)
    return {'publicdomain/zero/1.0':'CC0-1.0','licenses/by/4.0':'CC-BY-4.0',
            'licenses/by-nc/4.0':'CC-BY-NC-4.0'}.get(match.group(1).lower()) if match else ''


def normalize(occurrence):
    if (str(occurrence.get('kingdom') or '').casefold() != 'fungi' or occurrence.get('countryCode') != 'VN'
            or not canonical_license(occurrence.get('license'))):
        return None
    species = str(occurrence.get('species') or '').strip()
    key = occurrence.get('speciesKey')
    if not key or len(species.split()) < 2 or occurrence.get('taxonRank') not in (None, 'SPECIES', 'SUBSPECIES', 'VARIETY'):
        return None
    media = []
    for item in occurrence.get('media') or []:
        url = str(item.get('identifier') or '').strip()
        license_id = canonical_license(item.get('license'))
        if url.startswith('https://') and license_id in LICENSES and str(item.get('type') or '').casefold() in ('stillimage', 'image'):
            media.append({'identifier':url, 'mediaType':'StillImage', 'license':license_id,
                          'references':f"https://www.gbif.org/occurrence/{occurrence['key']}",
                          'creator':item.get('creator') or '', 'rightsHolder':item.get('rightsHolder') or '',
                          'sourceProvider':'GBIF occurrence media', 'gbifOccurrenceKey':str(occurrence['key'])})
    if not media:
        return None
    url = f"https://www.gbif.org/occurrence/{occurrence['key']}"
    return {'schemaVersion':1,'sourceId':'gbif-fungi-vn','sourceRecordId':str(key),
            'scientificName':species,'libraryGroup':'Nấm','kingdom':'Fungi',
            'taxonomicStatus':'Accepted','sourceRecordUrl':url,'mediaItems':media,
            'occurrence':{'countryCode':'VN','datasetKey':str(occurrence.get('datasetKey') or ''),
                          'basisOfRecord':str(occurrence.get('basisOfRecord') or '')},
            'provenance':{'authority':'GBIF occurrence publishers','license':str(occurrence.get('license') or ''),
                          'scope':'taxonomy-occurrence-media','sourceUrl':url}}


def collect(pages=5, fetch=None):
    def default_fetch(url):
        request=urllib.request.Request(url,headers={'User-Agent':'FieldIntelligence/1.0 (scientific-library)'})
        with urllib.request.urlopen(request,timeout=30) as response:
            return json.load(response)
    fetch = fetch or default_fetch
    records={}
    for page in range(pages):
        query=urllib.parse.urlencode({'country':'VN','kingdomKey':5,'mediaType':'StillImage','limit':300,'offset':page*300})
        payload=fetch(f'{API}?{query}')
        for occurrence in payload.get('results',[]):
            row=normalize(occurrence)
            if row:
                old=records.get(row['sourceRecordId'])
                if old is None or len(row['mediaItems']) > len(old['mediaItems']):
                    records[row['sourceRecordId']]=row
        if payload.get('endOfRecords') or not payload.get('results'):
            break
    return list(records.values())


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--pages',type=int,default=5)
    parser.add_argument('--max-records',type=int,default=0,help='Cap initial offline-media verification; 0 scans all pages')
    args=parser.parse_args()
    if not 1 <= args.pages <= 20: parser.error('--pages must be 1..20')
    if args.max_records < 0:parser.error('--max-records must not be negative')
    records=collect(args.pages)
    if args.max_records:records=records[:args.max_records]
    if not records: raise SystemExit('No verified Vietnamese fungi with licensed media; refusing empty output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(''.join(json.dumps(row,ensure_ascii=False)+'\n' for row in records),encoding='utf-8')
    print(json.dumps({'fungiCandidates':len(records)},ensure_ascii=False))


if __name__ == '__main__':main()
