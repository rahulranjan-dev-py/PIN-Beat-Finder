#!/usr/bin/env python3
"""Turn one CHANGELOG.md section into short GitHub release notes.

Usage: release_notes.py <tag> [changelog] > notes.md

Keeps the section title, the top-level bullets (each folded to one line and cut after its first
sentence), at most MAX_BULLETS of them, an install line, and a link to the full section.
"""
import re
import sys

MAX_BULLETS = 6
REPO = "rahulranjan-dev-py/PIN-Beat-Finder"


def section_for(text: str, tag: str) -> tuple[str, list[str]]:
    version = tag.lstrip("v")
    lines = text.splitlines()
    start = next((i for i, l in enumerate(lines) if re.match(rf"^## v?{re.escape(version)}\b", l)), None)
    if start is None:
        return "", []
    title = re.sub(r"^## v?[\d.]+\s*[—-]\s*", "", lines[start]).strip()
    body = []
    for l in lines[start + 1:]:
        if l.startswith("## "):
            break
        body.append(l)
    # top-level bullets: "- " at column 0; continuation lines are indented
    bullets, cur = [], None
    for l in body:
        if l.startswith("- "):
            if cur:
                bullets.append(cur)
            cur = l[2:].strip()
        elif cur is not None and l.strip() and (l.startswith("  ") or l.startswith("\t")):
            cur += " " + l.strip()
        elif cur is not None and not l.strip():
            bullets.append(cur)
            cur = None
    if cur:
        bullets.append(cur)
    return title, bullets


def first_sentence(bullet: str) -> str:
    # keep a bold lead ("**Fetch offices by PIN.**") plus the first sentence after it
    m = re.match(r"(\*\*[^*]+\*\*)\s*(.*)", bullet)
    lead, rest = (m.group(1), m.group(2)) if m else ("", bullet)
    sent = re.split(r"(?<=[.;!?])\s+", rest, maxsplit=1)[0].strip().rstrip(";")
    if len(sent) > 170:  # cut at a word boundary rather than mid-word
        sent = sent[:170].rsplit(" ", 1)[0].rstrip(",;:") + "…"
    if sent and sent[-1] not in ".!?…":
        sent += "."
    return f"{lead} {sent}".strip()


def main() -> None:
    tag = sys.argv[1]
    path = sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md"
    title, bullets = section_for(open(path, encoding="utf-8").read(), tag)
    out = []
    if title:
        out.append(f"**{title[:1].upper() + title[1:]}**")
        out.append("")
    for b in bullets[:MAX_BULLETS]:
        out.append(f"- {first_sentence(b)}")
    if len(bullets) > MAX_BULLETS:
        out.append(f"- …and {len(bullets) - MAX_BULLETS} more")
    out.append("")
    out.append("**Install:** download the `.apk` below and open it. Updates over the previous version in place; "
               "the app also offers this update itself.")
    anchor = re.sub(r"[^a-z0-9\s-]", "", (tag + " " + title).lower()).strip().replace(" ", "-")
    out.append(f"Full notes: [CHANGELOG.md](https://github.com/{REPO}/blob/main/CHANGELOG.md#{anchor})")
    print("\n".join(out))


if __name__ == "__main__":
    main()
