"""cities_src.txt → data/cities.json (나라별 대표 도시·관광지, 좌표, 월별 성수기)."""
import json
import pathlib

root = pathlib.Path(__file__).resolve().parent.parent
known = {c["iso2"] for k in json.loads((root / "data/regions.json").read_text(encoding="utf-8"))["continents"]
         for r in k["regions"] for c in r["countries"]}
countries, cur = {}, None
for line in (root / "scripts/cities_src.txt").read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    if line.startswith("@"):
        cur = line[1:].strip()
        assert cur in known, cur
        countries[cur] = []
        continue
    kind, name, lat, lon, season = [x.strip() for x in line.split("|")]
    assert kind in ("major", "tourist") and len(season) == 12 and set(season) <= set("PMO"), line
    countries[cur].append({"name": name, "type": kind, "lat": float(lat), "lon": float(lon), "season": season})

out = {
    "version": 1,
    "season_legend": {"P": "성수기", "M": "보통", "O": "비수기"},
    "season_note": "기후(우기·건기·더위)와 관광 시즌 일반 정보로 정리한 값으로, 실제와 다를 수 있음",
    "countries": countries,
}
(root / "data/cities.json").write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(len(countries), "나라", sum(len(v) for v in countries.values()), "도시")
