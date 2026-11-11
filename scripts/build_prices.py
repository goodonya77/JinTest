"""조사 결과(나라별 JSON)를 data/prices.json 하나로 합친다.

사용: python3 scripts/build_prices.py 조사폴더
값이 null인 항목은 그대로 두고(앱에서 "없음"), 모든 값은 verified=false로 시작한다.
(공식 페이지를 직접 열어 확인하지 못하고 검색 결과 요약에서 가져온 값이기 때문)
"""
import json
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parent.parent
src = pathlib.Path(sys.argv[1])
KEYS_NATIONAL = ["starbucks_americano_tall", "coke_500ml", "water_500ml", "iphone_base", "netflix_standard",
                 "spotify_premium", "mobile_data_1gb_usd", "broadband_month_usd", "petrol_1l", "electricity_kwh",
                 "ikea_billy"]
KEYS_CITY = ["meal_cheap", "beer_restaurant", "taxi_base", "transit_single", "hotel_mid", "backpacker_daily_usd"]


def clean(entry):
    if not isinstance(entry, dict) or entry.get("value") is None:
        return {"value": None, "note": (entry or {}).get("note", "") if isinstance(entry, dict) else ""}
    return {"value": entry["value"], "as_of": entry.get("as_of", ""), "source": entry.get("source", ""),
            "note": entry.get("note", ""), "verified": False}


countries, summary = {}, []
# 2차 조사 패치: 기존에 비어 있던 값만 채운다
patches = [json.loads(f.read_text(encoding="utf-8")) for f in sorted(src.glob("patch_*.json"))]
# 조사 기준(2024년 이후)에 맞지 않는 것으로 확인된 값은 비운다
DROP = {("FR", "mobile_data_1gb_usd"): "Cable.co.uk 2023년 조사값으로 보여 기준(2024년 이후) 미달",
        ("IT", "mobile_data_1gb_usd"): "Cable.co.uk 2023년 조사값으로 보여 기준(2024년 이후) 미달"}


def apply_patches(d):
    iso2 = d["iso2"]
    for p in patches:
        part = p.get(iso2, {})
        for k, v in part.get("national", {}).items():
            if (d.setdefault("national", {}).get(k) or {}).get("value") is None:
                d["national"][k] = v
        for city, items in part.get("cities", {}).items():
            target = d.setdefault("cities", {}).setdefault(city, {})
            for k, v in items.items():
                if (target.get(k) or {}).get("value") is None:
                    target[k] = v
    for (c, k), why in DROP.items():
        if c == iso2:
            d["national"][k] = {"value": None, "note": why}
    return d


for f in sorted(p for p in src.glob("*.json") if not p.name.startswith("patch_")):
    d = apply_patches(json.loads(f.read_text(encoding="utf-8")))
    nat = {k: clean(d.get("national", {}).get(k)) for k in KEYS_NATIONAL}
    cities = {c: {k: clean(v.get(k)) for k in KEYS_CITY} for c, v in d.get("cities", {}).items()}
    countries[d["iso2"]] = {"currency": d["currency"], "national": nat, "cities": cities}
    filled = sum(v["value"] is not None for v in nat.values()) + \
        sum(v["value"] is not None for c in cities.values() for v in c.values())
    total = len(nat) + sum(len(c) for c in cities.values())
    summary.append(f'{d["iso2"]} {filled}/{total}')

out = {
    "version": 1,
    "spec": "data/prices_spec.md",
    "note": "검색 결과 요약 기반 1차 조사값. verified=true로 바뀐 값만 공식 페이지에서 직접 확인한 값",
    "countries": countries,
}
(root / "data/prices.json").write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(" · ".join(summary))
