#!/usr/bin/env python3
from html.parser import HTMLParser
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import json
import csv

URL = "https://wiki.openstreetmap.org/w/index.php?title=TMC/Event_Code_List&oldid=981793"

class Tables(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.rows = []
        self.row = None
        self.cell = None
    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        elif tag in ("td", "th") and self.row is not None:
            self.cell = []
    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)
    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.cell is not None:
            self.row.append(" ".join("".join(self.cell).split()))
            self.cell = None
        elif tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None

parser = Tables()
headers = {"User-Agent": "droidRDS/1.0 (https://github.com/interrupt21h/droidRDS; scott@21h.io)"}
try:
    with urlopen(Request(URL, headers=headers), timeout=60) as response:
        parser.feed(response.read().decode("utf-8"))
except HTTPError as exc:
    if exc.code != 403:
        raise
    api = "https://wiki.openstreetmap.org/w/api.php?action=parse&oldid=981793&prop=text&format=json&formatversion=2"
    with urlopen(Request(api, headers=headers), timeout=60) as response:
        parser.feed(json.load(response)["parse"]["text"])
events = {}
active = False
for row in parser.rows:
    if row == ["Code", "Description", "N", "Q", "T", "D", "U", "C", "R"]:
        active = True
        continue
    if active and len(row) == 9 and row[0].isdigit():
        code, name, nature, quantifier, duration, direction, urgency, update, reference = row
        code = int(code)
        if code in events or not 0 <= code <= 2047 or not name:
            raise ValueError(f"Invalid or duplicate event {code}")
        events[code] = [code, name, quantifier, nature, urgency, duration, direction, update, reference]
assert len(events) >= 1400, f"Incomplete catalogue: {len(events)}"
for code in (101, 201, 701, 738, 739):
    assert code in events
assert "roadworks" in events[701][1].lower()
out = Path("app/src/main/assets")
out.mkdir(parents=True, exist_ok=True)
with (out / "alertc-bundled.tsv").open("w", encoding="utf-8", newline="") as f:
    f.write("# OpenStreetMap Wiki contributors; CC BY-SA 2.0; revision 981793\n")
    writer = csv.writer(f, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_NONE)
    writer.writerow(["code", "description", "quantifier", "nature", "urgency", "duration", "directionality", "updateClass", "reference"])
    writer.writerows(events[k] for k in sorted(events))
(out / "alertc-attribution.txt").write_text("OpenStreetMap Wiki contributors — TMC/Event Code List\n" + URL + "\nhttps://creativecommons.org/licenses/by-sa/2.0/\nConverted from HTML to TSV; original code is separately MIT licensed.\n", encoding="utf-8")
print(f"Generated {len(events)} event definitions")
