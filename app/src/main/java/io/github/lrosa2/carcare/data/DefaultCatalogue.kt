package io.github.lrosa2.carcare.data

import io.github.lrosa2.carcare.backup.BackupCodec
import io.github.lrosa2.carcare.backup.BackupFile
import io.github.lrosa2.carcare.backup.CardJson
import io.github.lrosa2.carcare.backup.ItemJson
import io.github.lrosa2.carcare.backup.VehicleJson

/**
 * What the APK ships for a start without the import file (spec D20): item names, intervals and
 * Toyota reference lines for a Yaris Hybrid (XP13). No VIN, pins, history or other personal data.
 */
object DefaultCatalogue {

    private const val NL = "Toyota NL schedule"

    val items = listOf(
        ItemJson("engine_oil", "Engine oil", intervalKm = 15_000, intervalMonths = 12, toyotaRef = "$NL: every 15,000 km or 1 year."),
        ItemJson("oil_filter", "Oil filter", intervalKm = 15_000, intervalMonths = 12, toyotaRef = "$NL: with every oil change, every 15,000 km or 1 year."),
        ItemJson("engine_coolant", "Engine coolant", intervalKm = 90_000, toyotaRef = "$NL: first at 150,000 km, then every 90,000 km."),
        ItemJson("inverter_coolant", "Inverter coolant", intervalKm = 90_000, toyotaRef = "$NL: first at 240,000 km, then every 90,000 km."),
        ItemJson("transaxle_fluid", "Hybrid transmission fluid (ATF WS)", intervalKm = 60_000, intervalMonths = 48, toyotaRef = "$NL: inspect every 60,000 km or 4 years, no replacement interval."),
        ItemJson("spark_plugs", "Spark plugs", intervalKm = 90_000, toyotaRef = "$NL: every 90,000 km."),
        ItemJson("brake_fluid", "Brake fluid", intervalKm = 30_000, intervalMonths = 24, toyotaRef = "$NL: every 30,000 km or 2 years."),
        ItemJson("hv_fan_filter", "Hybrid battery fan filter", intervalKm = 15_000, intervalMonths = 12, toyotaRef = "No Toyota interval found; clean the mesh under the rear seat when dusty."),
        ItemJson("cabin_filter", "Pollen filter", intervalKm = 30_000, intervalMonths = 24, toyotaRef = "$NL: every 30,000 km or 2 years."),
        ItemJson("air_filter", "Air filter", intervalKm = 60_000, intervalMonths = 48, toyotaRef = "$NL: every 60,000 km or 4 years."),
        ItemJson("winter_tyres", "Winter tyres on", seasonal = "--10-15", toyotaRef = "Not a Toyota item. Swiss rule of thumb: October to Easter."),
        ItemJson("summer_tyres", "Summer tyres on", seasonal = "--04-15", toyotaRef = "Not a Toyota item. Swiss rule of thumb: October to Easter."),
        ItemJson("brakes_check", "Brakes check (pads / discs)", intervalKm = 15_000, intervalMonths = 12, toyotaRef = "$NL: inspected at every service."),
        ItemJson("battery_12v", "12V battery check", intervalMonths = 12, toyotaRef = "Toyota UK: inspected at every service."),
        ItemJson("ac_service", "A/C service", intervalMonths = 24, toyotaRef = "Not in the Toyota schedule."),
        ItemJson("wipers", "Wipers", intervalMonths = 12, toyotaRef = "$NL: inspected at every service."),
    )

    private val cardTitles = listOf("Vehicle", "Fluids", "Torque", "Parts", "Procedures", "Tyres & TPMS", "Links & garages", "To buy")

    fun file(): BackupFile = BackupFile(
        formatVersion = BackupCodec.CURRENT_VERSION,
        vehicle = VehicleJson("My car"),
        items = items,
        cards = cardTitles.map { CardJson(it, "") },
    )
}
