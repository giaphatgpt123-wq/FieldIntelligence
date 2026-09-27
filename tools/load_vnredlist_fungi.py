#!/usr/bin/env python3
"""Load VNRedList Vietnamese fungi pages while preserving source and image provenance."""
from __future__ import annotations
import argparse, html, json, re, urllib.parse, urllib.request
from html.parser import HTMLParser
from pathlib import Path

BASE = "https://vnredlist.vast.vn"
CATEGORY = BASE + "/thuc-vat/"

class Parser(HTMLParser):
    def __init__(self):
        super().__init__(); self.links=[]; self.images=[]; self.text=[]; self._capture=False
    def handle_starttag(self, tag, attrs):
        a=dict(attrs)
        if tag == "a" and a.get("href"): self.links.append((a["href"], ""))
        if tag == "img" and a.get("src"): self.images.append(a["src"])
    def handle_data(self, data):
        if data.strip(): self.text.append(html.unescape(data.strip()))

def fetch(url: str) -> str:
    req=urllib.request.Request(url, headers={"User-Agent":"FieldIntelligence/1.0 source-loader"})
    with urllib.request.urlopen(req, timeout=20) as r: return r.read().decode("utf-8", "replace")

def absolute(url: str, base: str) -> str: return urllib.parse.urljoin(base, url)

def category_links(source: str) -> list[str]:
    p=Parser(); p.feed(source); out=[]
    for href,_ in p.links:
        u=absolute(href,CATEGORY)
        if urllib.parse.urlparse(u).netloc == "vnredlist.vast.vn" and u.startswith(BASE) and u not in out and u != CATEGORY and "/category/" not in u and "/wp-" not in u:
            out.append(u)
    return out

def parse_detail(url: str, source: str) -> dict:
    p=Parser(); p.feed(source); text=" ".join(p.text); title=""
    m=re.search(r"<h1[^>]*>(.*?)</h1>", source, re.S|re.I)
    if m: title=re.sub(r"<[^>]+>","",html.unescape(m.group(1))).strip()
    labels={}
    for label,key in (("Tên tiếng Việt:","vernacularName"),("Tên khoa học:","scientificName"),("Tình trạng:","conservationStatus"),("Phân bố:","distribution"),("Mô tả:","description")):
        m=re.search(re.escape(label)+r"\s*(.*?)(?=\s+(?:Tên tiếng Việt:|Tên khoa học:|Tình trạng:|Phân bố:|Mô tả:|Công dụng:|Cập nhật:)|$)",text,re.I)
        if m: labels[key]=m.group(1).strip(" :-")
    imgs=[absolute(i,url) for i in p.images if "/wp-content/uploads/" in absolute(i,url)]
    labels.update({"title":title,"sourceId":"vnredlist","sourceRecordUrl":url,"imageUrls":list(dict.fromkeys(imgs)),"sourceLicense":"requires-permission-or-site-terms","provenance":{"publisher":"Vietnam Academy of Science and Technology","sourceUrl":url}})
    return labels

def load(category_url: str=CATEGORY, fetcher=fetch) -> list[dict]:
    links=category_links(fetcher(category_url)); records=[]
    for url in links:
        try:
            rec=parse_detail(url,fetcher(url));
            if rec.get("scientificName") and any(k in (rec.get("title","")+" "+rec.get("description","")).casefold() for k in ("nấm","fungi","fungus","basidiomycota","ascomycota")):
                rec["libraryGroup"]="Nấm"; records.append(rec)
        except Exception as exc: records.append({"sourceRecordUrl":url,"sourceId":"vnredlist","loadError":str(exc)})
    return records

def main():
    p=argparse.ArgumentParser(); p.add_argument("--category-url",default=CATEGORY); p.add_argument("--output",required=True,type=Path)
    a=p.parse_args(); rows=load(a.category_url); a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text("\n".join(json.dumps(x,ensure_ascii=False) for x in rows)+"\n",encoding="utf-8"); print(json.dumps({"records":len(rows),"source":"vnredlist","group":"Nấm"},ensure_ascii=False))
if __name__ == "__main__": main()
