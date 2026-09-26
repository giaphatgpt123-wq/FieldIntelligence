#!/usr/bin/env python3
"""Select WFO taxonomy records in named genera for a smaller offline mobile pack."""
from __future__ import annotations
import argparse
import gzip
import json
from pathlib import Path

TROPICAL_GENERA = frozenset("""
Abrus Alpinia Amorphophallus Ananas Areca Artocarpus Bambusa Boesenbergia
Calamus Camellia Capsicum Carica Centella Cinnamomum Citrus Cocos Coffea
Colocasia Curcuma Cymbopogon Dioscorea Dipterocarpus Durio Etlingera Ficus
Garcinia Hedychium Ipomoea Mangifera Manihot Musa Nepenthes Nerium Nymphaea
Nypa Ocimum Oryza Pandanus Piper Psidium Pterocarpus Ricinus Shorea Solanum
Syzygium Theobroma Zingiber
""".split())


def select(source: Path, output: Path, genera: frozenset[str] = TROPICAL_GENERA) -> dict:
    count = 0
    by_genus: dict[str, int] = {}
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(source, "rt", encoding="utf-8") as reader, gzip.open(output, "wt", encoding="utf-8") as writer:
        for line in reader:
            if not line.strip():
                continue
            record = json.loads(line)
            genus = str(record.get("genus") or "").strip()
            if genus not in genera:
                continue
            writer.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
            by_genus[genus] = by_genus.get(genus, 0) + 1
            count += 1
    if not count:
        raise ValueError("No matching WFO records")
    return {"recordCount": count, "matchedGenera": len(by_genus), "requestedGenera": len(genera)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(select(args.input, args.output), ensure_ascii=False))


if __name__ == "__main__":
    main()
