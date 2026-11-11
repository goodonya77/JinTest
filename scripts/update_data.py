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

WB_API = "https://api.worldbank.org/v2/country/all/indicator/{}?format=json&per_page=20000&date=2012:2025"
WB_RATIO = "PA.NUS.PPPC.RF"  # PPP 환율 ÷ 시장 환율 (보관 처리되어 없을 수 있음)
WB_PPP = "PA.NUS.PPP"        # PPP 환율 (현지 통화 / 국제 달러)
WB_FX = "PA.NUS.FCRF"        # 시장 환율 (현지 통화 / 미국 달러)
BIGMAC_URL = ("https://raw.githubusercontent.com/TheEconomist/big-mac-data/"
              "master/output-data/big-mac-full-index.csv")


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read().decode("utf-8")


def our_countries():
    regions = json.loads((ROOT / "data/regions.json").read_text(encoding="utf-8"))
    return {c["iso3"]: c for k in regions["continents"] for r in k["regions"] for c in r["countries"]}


def wb_series(code, countries):
    """나라별 {연도: 값}. 지표가 없으면 None."""
    body = json.loads(get(WB_API.format(code)))
    if len(body) < 2 or body[1] is None:
        print(f"세계은행 {code}: {body}")
        return None
    series = {}
    for row in body[1]:
        iso3, value = row.get("countryiso3code"), row.get("value")
        if iso3 in countries and value is not None:
            series.setdefault(iso3, {})[int(row["date"])] = value
    return series


def world_bank(countries):
    """미국 = 1 기준 물가 수준. 비율 지표가 없으면 PPP 환율 ÷ 시장 환율로 직접 계산한다."""
    latest = {}
    ratio = wb_series(WB_RATIO, countries)
    if ratio:
        method = WB_RATIO
        for iso3, years in ratio.items():
            y = max(years)
            latest[iso3] = {"value": round(years[y], 4), "year": y}
    else:
        method = f"{WB_PPP} ÷ {WB_FX}"
        ppp, fx = wb_series(WB_PPP, countries), wb_series(WB_FX, countries)
        if not ppp or not fx:
            raise SystemExit("세계은행 PPP·환율 지표를 받지 못했어요")
        for iso3 in ppp:
            both = [y for y in ppp[iso3] if y in fx.get(iso3, {}) and fx[iso3][y]]
            if both:
                y = max(both)
                latest[iso3] = {"value": round(ppp[iso3][y] / fx[iso3][y], 4), "year": y}
    return {
        "source": f"World Bank World Development Indicators ({method})",
        "license": "CC BY 4.0",
        "url": "https://data.worldbank.org/indicator/PA.NUS.PPP",
        "note": "미국 = 1인 물가 수준. 한국 대비로 보려면 나라 값 ÷ 한국 값 × 100",
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
