#!/usr/bin/env python3
"""Normalize licensed GBIF/Darwin Core exports for the FieldIntelligence scientific library.

The adapter preserves source taxonomy, occurrence and media provenance. It does not infer
edibility, toxicity, danger, treatment advice or specimen identity from reference media.
"""
from __future__ import annotations
import argparse, csv, gzip, hashlib, json
from pathlib import Path
from typing import Dict, Iterator, List

ALLOWED_LICENSES={"CC0-1.0":False,"CC-BY-4.0":False,"CC-BY-NC-4.0":True}

def clean(value: object)->str: return str(value or "").strip()
def first(row: Dict[str,str], *keys:str)->str:
    for key in keys:
        value=clean(row.get(key))
        if value: return value
    return ""

def read_delimited(path:Path)->Iterator[Dict[str,str]]:
    sample=path.read_text(encoding="utf-8-sig",errors="replace")[:16384]
    try: dialect=csv.Sniffer().sniff(sample,delimiters=",\t;")
    except csv.Error: dialect=csv.excel_tab if "\t" in sample else csv.excel
    with path.open("r",encoding="utf-8-sig",errors="replace",newline="") as handle:
        yield from csv.DictReader(handle,dialect=dialect)

def library_group(row:Dict[str,str])->str:
    kingdom=first(row,"kingdom").lower()
    clazz=first(row,"class","classKey").lower()
    order=first(row,"order").lower()
    if kingdom=="plantae": return "Thực vật"
    if kingdom=="fungi": return "Nấm"
    fish_classes={"actinopterygii","elasmobranchii","sarcopterygii","myxini"}
    fish_orders={"cypriniformes","siluriformes","anabantiformes","perciformes","synbranchiformes","osteoglossiformes","clupeiformes","beloniformes","gobiiformes","cichliformes"}
    if kingdom=="animalia" and (clazz in fish_classes or order in fish_orders): return "Cá nước ngọt"
    if kingdom=="animalia" and clazz=="insecta": return "Côn trùng"
    if kingdom=="animalia": return "Động vật"
    return "Khác"

def media_item(row:Dict[str,str])->dict:
    return {
        "mediaType":first(row,"mediaType","type"),
        "identifier":first(row,"identifier","accessURI"),
        "references":first(row,"references"),
        "title":first(row,"title"),
        "description":first(row,"description"),
        "creator":first(row,"creator"),
        "rightsHolder":first(row,"rightsHolder"),
        "license":first(row,"mediaLicense","license"),
    }

def normalize(row:Dict[str,str],dataset_doi:str,publisher:str,license_id:str,media_rows:List[Dict[str,str]]|None=None,collapse_to_accepted_taxon:bool=False)->dict:
    source_scientific=first(row,"scientificName","species")
    if collapse_to_accepted_taxon:
        source_record_id=first(row,"acceptedTaxonKey","taxonKey","acceptedNameUsageID","taxonID","gbifID","occurrenceID","id")
        scientific=first(row,"acceptedScientificName","scientificName","species")
    else:
        source_record_id=first(row,"occurrenceID","gbifID","taxonID","taxonKey","id")
        scientific=first(row,"scientificName","acceptedScientificName","species")
    joined=[media_item(m) for m in (media_rows or []) if first(m,"identifier","accessURI")]
    legacy=media_item(row)
    if not joined and legacy["identifier"]: joined=[legacy]
    accepted_id=first(row,"acceptedTaxonKey","acceptedNameUsageID")
    source_names=[]
    for value in (source_scientific, scientific):
        value=clean(value)
        if value and value.casefold() not in {name.casefold() for name in source_names}:
            source_names.append(value)
    return {
        "schemaVersion":1,"sourceId":"gbif","sourceRecordId":source_record_id,
        "scientificName":scientific,"acceptedNameUsageId":accepted_id or (source_record_id if collapse_to_accepted_taxon else ""),
        "sourceScientificNames":source_names,
        "taxonomicStatus":first(row,"taxonomicStatus"),"kingdom":first(row,"kingdom"),
        "phylum":first(row,"phylum"),"class":first(row,"class"),"order":first(row,"order"),
        "family":first(row,"family"),"genus":first(row,"genus"),"species":first(row,"species"),
        "vernacularName":first(row,"vernacularName"),"libraryGroup":library_group(row),
        "media": joined[0] if joined else legacy, "mediaItems":joined,
        "occurrence":{"basisOfRecord":first(row,"basisOfRecord"),"countryCode":first(row,"countryCode"),
            "stateProvince":first(row,"stateProvince"),"locality":first(row,"locality"),
            "decimalLatitude":first(row,"decimalLatitude"),"decimalLongitude":first(row,"decimalLongitude"),
            "eventDate":first(row,"eventDate"),"recordedBy":first(row,"recordedBy"),"datasetKey":first(row,"datasetKey")},
        "provenance":{"authority":"GBIF Secretariat and dataset publisher","publisher":publisher,
            "datasetDoi":dataset_doi,"license":license_id,"scope":"taxonomy-occurrence-and-media-metadata",
            "nonCommercialRestriction":ALLOWED_LICENSES[license_id]},
    }

def valid(record:dict)->bool:
    return bool(clean(record.get("sourceRecordId")) and clean(record.get("scientificName")))

def validate_provenance(dataset_doi:str,publisher:str,license_id:str)->None:
    if not dataset_doi.startswith("10.") or "/" not in dataset_doi: raise SystemExit("GBIF import requires a DOI-like dataset/download identifier")
    if not publisher.strip(): raise SystemExit("GBIF import requires the dataset publisher")
    if license_id not in ALLOWED_LICENSES: raise SystemExit("Unsupported GBIF licence")

def _merge_taxon_record(base:dict,incoming:dict,max_media_per_taxon:int)->None:
    base["occurrenceEvidenceCount"]=int(base.get("occurrenceEvidenceCount",1))+1
    if not clean(base.get("vernacularName")) and clean(incoming.get("vernacularName")):
        base["vernacularName"]=incoming["vernacularName"]
    names=list(base.get("sourceScientificNames") or [])
    seen_names={clean(name).casefold() for name in names if clean(name)}
    for name in incoming.get("sourceScientificNames") or []:
        value=clean(name)
        if value and value.casefold() not in seen_names:
            names.append(value); seen_names.add(value.casefold())
    base["sourceScientificNames"]=names
    if clean(incoming.get("taxonomicStatus")).casefold() in {"accepted","valid"}:
        base["taxonomicStatus"]=incoming["taxonomicStatus"]
    existing=list(base.get("mediaItems") or [])
    seen={(clean(item.get("identifier")),clean(item.get("license"))) for item in existing if clean(item.get("identifier"))}
    for item in incoming.get("mediaItems") or []:
        key=(clean(item.get("identifier")),clean(item.get("license")))
        if not key[0] or key in seen: continue
        if len(existing)>=max_media_per_taxon: break
        existing.append(item); seen.add(key)
    base["mediaItems"]=existing
    if existing:
        base["media"]=existing[0]

def build(input_path:Path,output_path:Path,metadata_path:Path,dataset_doi:str,publisher:str,license_id:str,multimedia_path:Path|None=None,collapse_to_accepted_taxon:bool=False,max_media_per_taxon:int=50)->dict:
    validate_provenance(dataset_doi,publisher,license_id)
    if max_media_per_taxon < 1: raise SystemExit("max_media_per_taxon must be at least 1")
    output_path.parent.mkdir(parents=True,exist_ok=True)
    media_by_gbif:dict[str,List[Dict[str,str]]]={}
    if multimedia_path:
        for row in read_delimited(multimedia_path):
            gbif_id=first(row,"gbifID")
            if gbif_id: media_by_gbif.setdefault(gbif_id,[]).append(row)

    source_row_count=0
    records:list[dict]=[]
    if collapse_to_accepted_taxon:
        grouped:dict[str,dict]={}
        for row in read_delimited(input_path):
            source_row_count+=1
            record=normalize(row,dataset_doi,publisher,license_id,media_by_gbif.get(first(row,"gbifID"),[]),True)
            if not valid(record): continue
            key=clean(record["sourceRecordId"]).casefold()
            if key not in grouped:
                record["occurrenceEvidenceCount"]=1
                if len(record.get("mediaItems") or [])>max_media_per_taxon:
                    record["mediaItems"]=record["mediaItems"][:max_media_per_taxon]
                    record["media"]=record["mediaItems"][0]
                grouped[key]=record
            else:
                _merge_taxon_record(grouped[key],record,max_media_per_taxon)
        records=list(grouped.values())
    else:
        seen=set()
        for row in read_delimited(input_path):
            source_row_count+=1
            record=normalize(row,dataset_doi,publisher,license_id,media_by_gbif.get(first(row,"gbifID"),[]),False)
            if not valid(record): continue
            key=(record["sourceRecordId"].lower(),record["scientificName"].lower())
            if key in seen: continue
            seen.add(key); records.append(record)

    groups={}; records_with_media=0; sha=hashlib.sha256()
    with gzip.open(output_path,"wt",encoding="utf-8",newline="\n") as out:
        for record in records:
            if record.get("mediaItems"): records_with_media+=1
            line=json.dumps(record,ensure_ascii=False,separators=(",",":"))+"\n"
            out.write(line); sha.update(line.encode("utf-8"))
            group=record["libraryGroup"]; groups[group]=groups.get(group,0)+1

    metadata={"schemaVersion":1,"sourceId":"gbif","datasetDoi":dataset_doi,"publisher":publisher,
        "license":license_id,"nonCommercialRestriction":ALLOWED_LICENSES[license_id],"recordCount":len(records),
        "sourceRowCount":source_row_count,"collapseToAcceptedTaxon":collapse_to_accepted_taxon,
        "recordsWithMedia":records_with_media,"multimediaRows":sum(map(len,media_by_gbif.values())),
        "groupCounts":groups,"normalizedNdjsonSha256":sha.hexdigest(),
        "safety":{"taxonomyDoesNotIdentifyPhotos":True,"occurrenceDoesNotProveCurrentPresence":True,
            "recordsDoNotImplyDanger":True,"recordsDoNotImplyEdibility":True,"recordsDoNotImplyTreatment":True}}
    metadata_path.parent.mkdir(parents=True,exist_ok=True)
    metadata_path.write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding="utf-8")
    return metadata

def main()->None:
    p=argparse.ArgumentParser()
    p.add_argument("--input",required=True,type=Path); p.add_argument("--output",required=True,type=Path)
    p.add_argument("--multimedia",type=Path); p.add_argument("--metadata",required=True,type=Path)
    p.add_argument("--dataset-doi",required=True); p.add_argument("--publisher",required=True)
    p.add_argument("--license",required=True,choices=sorted(ALLOWED_LICENSES))
    p.add_argument("--collapse-to-accepted-taxon",action="store_true",help="Build one library profile per accepted GBIF taxon instead of one record per occurrence")
    p.add_argument("--max-media-per-taxon",type=int,default=50)
    a=p.parse_args(); meta=build(a.input,a.output,a.metadata,a.dataset_doi,a.publisher,a.license,a.multimedia,a.collapse_to_accepted_taxon,a.max_media_per_taxon)
    print(json.dumps({"recordCount":meta["recordCount"],"sourceRowCount":meta["sourceRowCount"],"groupCounts":meta["groupCounts"],"collapseToAcceptedTaxon":meta["collapseToAcceptedTaxon"]},ensure_ascii=False))
if __name__=="__main__": main()
