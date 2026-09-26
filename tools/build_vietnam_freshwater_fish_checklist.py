#!/usr/bin/env python3
"""Normalize a Vietnam freshwater-fish checklist into auditable NDJSON.

This stage records checklist presence status only. It does not infer specimen identity,
edibility, safety, toxicity, or medical advice.
"""
from __future__ import annotations
import argparse,csv,json
from pathlib import Path

STATUS_MAP={
 "present":"present","native":"present","introduced":"present",
 "possible":"review","possibly present":"review",
 "absent":"excluded","misidentification":"excluded","misidentified":"excluded",
}
def clean(v): return str(v or "").strip()
def normalize_status(v):
    x=clean(v).casefold()
    return STATUS_MAP.get(x,"review")
def read_rows(path):
    with path.open("r",encoding="utf-8-sig",errors="replace",newline="") as f:
        sample=f.read(8192); f.seek(0)
        try:d=csv.Sniffer().sniff(sample,delimiters=",\t;")
        except csv.Error:d=csv.excel_tab if "\t" in sample else csv.excel
        yield from csv.DictReader(f,dialect=d)
def build(inp,out):
    counts={"total":0,"present":0,"review":0,"excluded":0}
    seen=set(); records=[]
    for row in read_rows(inp):
        scientific=clean(row.get("scientificName") or row.get("Species") or row.get("species"))
        if not scientific: continue
        key=scientific.casefold()
        if key in seen: continue
        seen.add(key)
        status=normalize_status(row.get("presenceStatus") or row.get("status") or row.get("Presence"))
        rec={"schemaVersion":1,"categoryId":"freshwater-fish-vietnam","scientificName":scientific,
             "vernacularName":clean(row.get("vernacularName") or row.get("commonName")),
             "presenceStatus":status,"sourceStatus":clean(row.get("presenceStatus") or row.get("status") or row.get("Presence")),
             "sourceRecordUrl":clean(row.get("sourceRecordUrl") or row.get("url")),
             "safety":{"edibilityInferred":False,"specimenIdentityInferred":False}}
        records.append(rec); counts["total"]+=1; counts[status]+=1
    out.parent.mkdir(parents=True,exist_ok=True)
    with out.open("w",encoding="utf-8") as f:
        for r in records:f.write(json.dumps(r,ensure_ascii=False,separators=(",",":"))+"\n")
    return counts
def main():
    p=argparse.ArgumentParser();p.add_argument("--input",required=True,type=Path);p.add_argument("--output",required=True,type=Path);p.add_argument("--report",type=Path)
    a=p.parse_args(); counts=build(a.input,a.output)
    if a.report:a.report.write_text(json.dumps(counts,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(counts,ensure_ascii=False))
if __name__=="__main__":main()
