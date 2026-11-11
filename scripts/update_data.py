"""공개 데이터를 받아 data/generated/*.json 으로 저장한다 (GitHub Actions에서 실행).

- price_level.json : 세계은행 PA.NUS.PPPC.RF (PPP 환율 ÷ 시장 환율 = 미국 대비 물가 수준), CC BY 4.0
- bigmac.json      : The Economist 빅맥 지수 원자료 (나라별 최신 빅맥 현지 가격)

로컬 테스트: python3 scripts/update_data.py --bigmac-csv 파일경로 [--skip-worldbank]
"""
import argparse
import csv
import io
import json
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "data/generated"
UA = {"User-Agent": "ExchangeCalc data updater (github.com/goodonya77/JinTest)"}

WB_URL = ("https://api.worldbank.org/v2/country/all/indicator/PA.NUS.PPPC.RF"
          "?format=json&per_page=1000&mrnev=1")  # 나라별 가장 최근의 값이 있는 해
BIGMAC_URL = ("https://raw.githubusercontent.com/TheEconomist/big-mac-data/"
              "master/output-data/big-mac-full-index.csv")


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read().decode("utf-8")


def our_countries():
    regions = json.loads((ROOT / "data/regions.json").read_text(encoding="utf-8"))
    return {c["iso3"]: c for k in regions["continents"] for r in k["regions"] for c in r["countries"]}


def world_bank(countries):
    body = json.loads(get(WB_URL))
    if len(body) < 2 or body[1] is None:
        raise SystemExit(f"세계은행 API 오류: {body}")
    meta, rows = body
    latest = {}
    for row in rows:
        iso3, value = row.get("countryiso3code"), row.get("value")
        if iso3 in countries and value is not None:
            year = int(row["date"])
            if iso3 not in latest or year > latest[iso3]["year"]:
                latest[iso3] = {"value": round(value, 4), "year": year}
    return {
        "source": "World Bank, Price level ratio of PPP conversion factor (GDP) to market exchange rate (PA.NUS.PPPC.RF)",
        "license": "CC BY 4.0",
        "url": "https://data.worldbank.org/indicator/PA.NUS.PPPC.RF",
        "note": "미국 = 1. 한국 대비로 보려면 나라 값 ÷ 한국 값 × 100",
        "countries": dict(sorted(latest.items())),
    }


def big_mac(text, countries):
    latest = {}
    for row in csv.DictReader(io.StringIO(text)):
        iso3 = row["iso_a3"]
        if iso3 not in latest or row["date"] > latest[iso3]["date"]:
            latest[iso3] = {
                "date": row["date"],
                "currency": row["currency_code"],
                "local_price": float(row["local_price"]),
                "usd_price": round(float(row["dollar_price"]), 3),
            }
    # 유로존은 EUZ 한 줄로 발표되는 경우가 있어, 따로 값이 없는 유로 국가는 유로존 평균을 쓴다
    euz = latest.get("EUZ")
    result = {}
    for iso3, c in countries.items():
        if iso3 in latest:
            result[iso3] = latest[iso3]
        elif euz and c["currency"] == "EUR":
            result[iso3] = dict(euz, euro_area_average=True)
    return {
        "source": "The Economist, The Big Mac index (github.com/TheEconomist/big-mac-data)",
        "license": "저장소에 별도 라이선스 표기 없음 — 상업 이용 전 확인 필요",
        "countries": dict(sorted(result.items())),
    }


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{name}: {len(data['countries'])}개 나라")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bigmac-csv")
    ap.add_argument("--skip-worldbank", action="store_true")
    args = ap.parse_args()
    countries = our_countries()
    if not args.skip_worldbank:
        write("price_level.json", world_bank(countries))
    text = pathlib.Path(args.bigmac_csv).read_text(encoding="utf-8") if args.bigmac_csv else get(BIGMAC_URL)
    write("bigmac.json", big_mac(text, countries))


if __name__ == "__main__":
    main()
