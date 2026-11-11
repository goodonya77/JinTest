"""regions_src.txt → data/regions.json (대륙 > 지역 > 나라)."""
import json
import pathlib

root = pathlib.Path(__file__).resolve().parent.parent
continents = []
for line in (root / "scripts/regions_src.txt").read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("# 대륙"):
        continue
    if line.startswith("@"):
        name, icon = line[1:].split()
        continents.append({"name": name, "icon": icon, "regions": []})
    elif line.startswith("#"):
        continents[-1]["regions"].append({"name": line[1:].strip(), "countries": []})
    else:
        iso2, iso3, name, currency = line.split()
        continents[-1]["regions"][-1]["countries"].append(
            {"iso2": iso2, "iso3": iso3, "name": name, "currency": currency})

out = {"version": 1, "continents": continents}
(root / "data/regions.json").write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
codes = [c["iso2"] for k in continents for r in k["regions"] for c in r["countries"]]
assert len(codes) == len(set(codes)), "중복 국가"
print(len(continents), "대륙", sum(len(k["regions"]) for k in continents), "지역", len(codes), "나라")
