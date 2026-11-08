"""Persistent source anchors for tender requirement IDs."""
import csv
import hashlib
import re
import unicodedata
from pathlib import Path


def canonical(text: str) -> str:
    folded = "".join(
        char for char in unicodedata.normalize("NFKD", text.casefold())
        if not unicodedata.combining(char)
    )
    return " ".join(folded.split())


class MatrixIdRegistry:
    def __init__(self, path: Path):
        self.path = path
        self.entries: dict[str, dict[str, str]] = {}
        self.ids: dict[str, str] = {}
        if path.exists():
            with path.open(encoding="utf-8-sig", newline="") as stream:
                for row in csv.DictReader(stream, delimiter=";"):
                    key, requirement_id = row["identity_key"], row["id"]
                    if key in self.entries or requirement_id in self.ids:
                        raise SystemExit(f"IDs/âncoras duplicados no registro {path}: {requirement_id}")
                    self.entries[key] = row
                    self.ids[requirement_id] = key

    @staticmethod
    def identity(prefix: str, page_ref: str, summary: str, occurrence: int = 1) -> tuple[str, str]:
        anchor = f"{prefix}|{page_ref}|{canonical(summary)}|occurrence:{occurrence}"
        key = hashlib.sha256(anchor.encode("utf-8")).hexdigest()
        return key, anchor

    def assign(self, prefix: str, page_ref: str, summary: str, occurrence: int = 1) -> str:
        key, anchor = self.identity(prefix, page_ref, summary, occurrence)
        if key in self.entries:
            return self.entries[key]["id"]

        numeric = [
            int(match.group(1))
            for requirement_id in self.ids
            if (match := re.fullmatch(rf"{re.escape(prefix)}-(\d+)", requirement_id))
        ]
        sequence = max(numeric, default=0) + 1
        requirement_id = f"{prefix}-{sequence:04d}"
        while requirement_id in self.ids:
            sequence += 1
            requirement_id = f"{prefix}-{sequence:04d}"
        row = {"id": requirement_id, "identity_key": key, "source_anchor": anchor}
        self.entries[key] = row
        self.ids[requirement_id] = key
        return requirement_id

    def save(self) -> None:
        ordered = sorted(self.entries.values(), key=lambda row: row["id"])
        with self.path.open("w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.DictWriter(
                stream,
                fieldnames=["id", "identity_key", "source_anchor"],
                delimiter=";",
                lineterminator="\n",
            )
            writer.writeheader()
            writer.writerows(ordered)
