#!/usr/bin/env python3
"""Copy only verified fish checklist gate metadata from fish SQLite to a merged scientific SQLite."""
from __future__ import annotations
import argparse, json, sqlite3
from pathlib import Path

KEYS = (
    "fishChecklistReported", "fishChecklistPresent", "fishChecklistReview", "fishChecklistExcluded",
    "fishPresentChecklistResolved", "fishPresentChecklistUnresolved", "fishPresentAcceptedTaxa",
    "fishPresentTaxaWithLocalMedia", "fishCoverageGateVersion",
)


def read_meta(db):
    result = {}
    for key, value in db.execute("SELECT key,value FROM meta"):
        try: result[key] = json.loads(value)
        except json.JSONDecodeError: result[key] = value
    return result


def propagate(target: Path, source: Path):
    src = sqlite3.connect(source)
    dst = sqlite3.connect(target)
    try:
        source_meta = read_meta(src)
        target_meta = read_meta(dst)
        missing = [key for key in KEYS if key not in source_meta]
        if missing:
            raise ValueError("fish coverage metadata missing: " + ", ".join(missing))
        gate = int(source_meta["fishCoverageGateVersion"])
        if gate not in (0, 1):
            raise ValueError("unsupported fish coverage gate version")
        resolved = int(source_meta["fishPresentChecklistResolved"])
        unresolved = int(source_meta["fishPresentChecklistUnresolved"])
        accepted = int(source_meta["fishPresentAcceptedTaxa"])
        local = int(source_meta["fishPresentTaxaWithLocalMedia"])
        if resolved <= 0 or unresolved < 0 or resolved + unresolved != int(source_meta["fishChecklistPresent"]):
            raise ValueError("source fish checklist resolution counts are inconsistent")
        if accepted <= 0 or accepted > resolved or local < 0 or local > accepted:
            raise ValueError("source fish taxon/media counts are inconsistent")
        if gate == 1 and (unresolved or local != accepted):
            raise ValueError("complete fish coverage gate is inconsistent")
        if int(target_meta.get("fishWithLocalMedia", 0)) < local:
            raise ValueError("merged target does not retain enough fish taxa with local media")
        for key in KEYS:
            dst.execute("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", (key, json.dumps(source_meta[key], ensure_ascii=False)))
        dst.commit()
        if str(dst.execute("PRAGMA integrity_check").fetchone()[0]).casefold() != "ok":
            raise ValueError("merged target integrity_check failed after coverage propagation")
        return {key: source_meta[key] for key in KEYS}
    finally:
        src.close(); dst.close()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--target", required=True, type=Path)
    p.add_argument("--source", required=True, type=Path)
    a = p.parse_args()
    print(json.dumps(propagate(a.target, a.source), ensure_ascii=False))


if __name__ == "__main__":
    main()
