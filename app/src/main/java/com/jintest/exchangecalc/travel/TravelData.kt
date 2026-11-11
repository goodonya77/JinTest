package com.jintest.exchangecalc.travel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Country(
    val iso2: String,
    val iso3: String,
    val name: String,
    val currency: String,
    val continent: String,
    val region: String,
)

data class Region(val name: String, val countries: List<Country>)
data class Continent(val name: String, val icon: String, val regions: List<Region>)

enum class CityType { MAJOR, TOURIST }

/** season: 1~12월 12글자 (P=성수기, M=보통, O=비수기) */
data class City(val name: String, val type: CityType, val lat: Double, val lon: Double, val season: String)

data class PriceEntry(val value: Double?, val asOf: String, val source: String, val note: String)

data class CountryPrices(
    val currency: String,
    val national: Map<String, PriceEntry>,
    val cities: Map<String, Map<String, PriceEntry>>,
)

data class BigMac(val localPrice: Double, val currency: String, val date: String, val euroAverage: Boolean)

/** 미국 = 1 기준 물가 수준 */
data class PriceLevel(val value: Double, val year: Int, val source: String)

class TravelData(
    val continents: List<Continent>,
    val priceLevel: Map<String, PriceLevel>,  // iso3
    val bigMac: Map<String, BigMac>,          // iso3
    val cities: Map<String, List<City>>,      // iso2
    val prices: Map<String, CountryPrices>,   // iso2
) {
    val countries: List<Country> = continents.flatMap { c -> c.regions.flatMap { it.countries } }
    private val byIso2 = countries.associateBy { it.iso2 }
    fun country(iso2: String): Country? = byIso2[iso2]
}

/**
 * 여행 물가 데이터. 앱에 들어 있는 assets 사본을 바로 쓰고,
 * GitHub에 올라간 최신 data/ 파일을 받아 두었다가 다음 실행부터 쓴다 (앱 업데이트 없이 데이터 갱신).
 */
object TravelRepository {
    // 저장소 기본 브랜치가 바뀌면 이 주소도 함께 바꾼다
    private const val REMOTE = "https://raw.githubusercontent.com/goodonya77/JinTest/ccr-033aa4f8-gl6pst/data/"
    private val FILES = listOf(
        "regions.json", "cities.json", "prices.json",
        "generated/price_level.json", "generated/bigmac.json",
    )

    private fun cacheFile(context: Context, name: String) = File(context.filesDir, "travel/$name")

    private fun read(context: Context, name: String): String {
        val cached = cacheFile(context, name)
        if (cached.exists()) runCatching { return cached.readText() }
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }

    suspend fun load(context: Context): TravelData = withContext(Dispatchers.IO) {
        try {
            parse(context)
        } catch (e: Exception) {
            // 받아 둔 파일이 깨졌으면 지우고 앱에 들어 있는 사본으로
            File(context.filesDir, "travel").deleteRecursively()
            parse(context)
        }
    }

    /** 최신 데이터를 받아 둔다. 하나라도 실패하면 기존 파일을 그대로 둔다. */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val downloaded = FILES.associateWith { name ->
            runCatching { download(REMOTE + name) }.getOrNull() ?: return@withContext false
        }
        downloaded.forEach { (name, text) ->
            JSONObject(text) // 형식 확인
            cacheFile(context, name).apply { parentFile?.mkdirs() }.writeText(text)
        }
        true
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            check(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(context: Context): TravelData {
        val continents = JSONObject(read(context, "regions.json")).getJSONArray("continents").objects().map { c ->
            Continent(
                name = c.getString("name"),
                icon = c.getString("icon"),
                regions = c.getJSONArray("regions").objects().map { r ->
                    Region(r.getString("name"), r.getJSONArray("countries").objects().map { x ->
                        Country(
                            x.getString("iso2"), x.getString("iso3"), x.getString("name"), x.getString("currency"),
                            c.getString("name"), r.getString("name"),
                        )
                    })
                },
            )
        }

        val levelJson = JSONObject(read(context, "generated/price_level.json"))
        val levels = levelJson.getJSONObject("countries").entries().associate { (iso3, v) ->
            iso3 to PriceLevel(v.getDouble("value"), v.getInt("year"), v.optString("source", "World Bank"))
        }

        val bigMac = JSONObject(read(context, "generated/bigmac.json")).getJSONObject("countries").entries()
            .associate { (iso3, v) ->
                iso3 to BigMac(v.getDouble("local_price"), v.getString("currency"), v.getString("date"),
                    v.optBoolean("euro_area_average"))
            }

        val citiesJson = JSONObject(read(context, "cities.json")).getJSONObject("countries")
        val cities = citiesJson.keys().asSequence().associateWith { iso2 ->
            citiesJson.getJSONArray(iso2).objects().map {
                City(
                    it.getString("name"),
                    if (it.getString("type") == "tourist") CityType.TOURIST else CityType.MAJOR,
                    it.getDouble("lat"), it.getDouble("lon"), it.getString("season"),
                )
            }
        }

        val prices = JSONObject(read(context, "prices.json")).getJSONObject("countries").entries()
            .associate { (iso2, v) ->
                iso2 to CountryPrices(
                    currency = v.getString("currency"),
                    national = v.getJSONObject("national").entries().associate { (k, e) -> k to e.toEntry() },
                    cities = v.getJSONObject("cities").entries().associate { (city, items) ->
                        city to items.entries().associate { (k, e) -> k to e.toEntry() }
                    },
                )
            }

        return TravelData(continents, levels, bigMac, cities, prices)
    }

    private fun JSONObject.toEntry() = PriceEntry(
        value = if (isNull("value")) null else optDouble("value").takeIf { !it.isNaN() },
        asOf = optString("as_of"),
        source = optString("source"),
        note = optString("note"),
    )

    private fun org.json.JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.entries(): List<Pair<String, JSONObject>> =
        keys().asSequence().mapNotNull { k -> optJSONObject(k)?.let { k to it } }.toList()
}
