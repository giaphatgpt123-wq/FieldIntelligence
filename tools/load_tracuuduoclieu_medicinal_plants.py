#!/usr/bin/env python3
"""Conservative crawler for the Vietnamese Tracuuduoclieu plant index."""
import argparse,html,json,re,urllib.parse,urllib.request
from html.parser import HTMLParser
from pathlib import Path
BASE='https://tracuuduoclieu.vn'; INDEX=BASE+'/danh-luc-cay-thuoc'
class P(HTMLParser):
 def __init__(self): super().__init__();self.links=[];self.img=[];self.text=[]
 def handle_starttag(self,t,a):
  d=dict(a)
  if t=='a' and d.get('href'):self.links.append(d['href'])
  if t=='img' and d.get('src'):self.img.append(d['src'])
 def handle_data(self,d):
  if d.strip():self.text.append(html.unescape(d.strip()))
def fetch(u):
 r=urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'FieldIntelligence medicinal-library-loader/1.0'}),timeout=20);return r.read().decode('utf8','replace')
def absu(u,b):return urllib.parse.urljoin(b,u)
def links(s):
 p=P();p.feed(s);o=[]
 for h in p.links:
  u=absu(h,INDEX)
  if urllib.parse.urlparse(u).netloc=='tracuuduoclieu.vn' and u.endswith('.html') and u not in o:o.append(u)
 return o
def parse(u,s):
 p=P();p.feed(s);t=' '.join(p.text)
 title_match=re.search(r'<h1[^>]*>(.*?)</h1>',s,re.I|re.S)
 title=html.unescape(re.sub(r'<[^>]+>','',title_match.group(1))).strip() if title_match else ''
 vietnamese=re.search(r'Tên tiếng Việt\s*[:：-]\s*(.*?)(?=\s+(?:Tên khoa học|Họ|Công dụng)\s*[:：-]|$)',t,re.I)
 if vietnamese:title=vietnamese.group(1).strip()
 sci=''
 for pat in (r'Tên khoa học\s*[:：-]\s*([^.;|]+)',r'Danh pháp\s*[:：-]\s*([^.;|]+)'):
  m=re.search(pat,t,re.I)
  if m:
   candidate=m.group(1).strip()
   name=re.match(r'([A-Z][a-z]+\s+[a-z][a-z-]+)',candidate)
   if name:sci=name.group(1);break
 imgs=[absu(x,u) for x in p.img if '/wp-content/uploads/' in absu(x,u)]
 return {'schemaVersion':1,'sourceId':'tracuuduoclieu','sourceRecordId':u.rstrip('/').split('/')[-1],'scientificName':sci,'vernacularName':title,'libraryGroup':'Cây thuốc','sourceRecordUrl':u,'imageUrls':list(dict.fromkeys(imgs)),'provenance':{'authority':'Tracuuduoclieu.vn','sourceUrl':u,'license':'site-terms-review-required','medicalReviewRequired':True}}
def main():
 a=argparse.ArgumentParser();a.add_argument('--output',required=True,type=Path);a.add_argument('--limit',type=int,default=500);x=a.parse_args();out=[]
 for u in links(fetch(INDEX))[:x.limit]:
  try:
   r=parse(u,fetch(u))
   if r['scientificName']:out.append(r)
  except Exception as e: pass
 x.output.parent.mkdir(parents=True,exist_ok=True);x.output.write_text('\n'.join(json.dumps(r,ensure_ascii=False) for r in out)+'\n',encoding='utf8');print(json.dumps({'records':len(out),'source':'tracuuduoclieu'},ensure_ascii=False))
if __name__=='__main__':main()
