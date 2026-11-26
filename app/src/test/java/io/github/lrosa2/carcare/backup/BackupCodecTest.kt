package io.github.lrosa2.carcare.backup

import io.github.lrosa2.carcare.domain.StatusEngine
import io.github.lrosa2.carcare.domain.StatusSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.time.LocalDate
import java.time.MonthDay

class BackupCodecTest {

    private val sample = BackupFile(
        formatVersion = 1,
        exportedAt = "2026-11-23",
        vehicle = VehicleJson("Yaris Hybrid"),
        items = listOf(
            ItemJson("engine_oil", "Engine oil", intervalKm = 5_000, intervalMonths = 12, toyotaRef = "15,000 km / 1 year", myNote = "cheaper oil"),
            ItemJson("brake_fluid", "Brake fluid", intervalMonths = 24, pinnedKm = 95_000, pinSetOn = "2026-11-23"),
            ItemJson("winter_tyres", "Winter tyres on", seasonal = "--10-15"),
        ),
        readings = listOf(ReadingJson("2026-11-23", 83_000)),
        visits = listOf(
            VisitJson("2026-05-10", 77_000, "Garage", listOf(VisitItemJson("brake_fluid"))),
            VisitJson("2026-08-01", 79_500, items = listOf(VisitItemJson("engine_oil"), VisitItemJson("brake_fluid", partial = true))),
        ),
        cards = listOf(CardJson("Torque", "Wheel nuts 103 Nm")),
        settings = SettingsJson(reminderDay = 1, reminderTime = "19:00"),
    )

    @Test
    fun `encode then decode gives the same file`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun `a file from a newer app version is refused`() {
        val text = BackupCodec.encode(sample.copy(formatVersion = 2))
        val e = assertThrows<BackupException> { BackupCodec.decode(text) }
        assertTrue(e.message!!.contains("newer"), e.message)
    }

    @Test
    fun `a visit that ticks an unknown item is refused`() {
        val bad = sample.copy(visits = listOf(VisitJson("2026-05-10", 77_000, items = listOf(VisitItemJson("nope")))))
        val e = assertThrows<BackupException> { BackupCodec.decode(BackupCodec.encode(bad)) }
        assertTrue(e.message!!.contains("nope"), e.message)
    }

    @Test
    fun `a bad date is refused with the place it was found`() {
        val bad = sample.copy(readings = listOf(ReadingJson("01.02.2026", 74_000)))
        val e = assertThrows<BackupException> { BackupCodec.decode(BackupCodec.encode(bad)) }
        assertTrue(e.message!!.contains("01.02.2026"), e.message)
    }

    @Test
    fun `duplicate item ids are refused`() {
        val bad = sample.copy(items = sample.items + ItemJson("engine_oil", "Again"))
        assertThrows<BackupException> { BackupCodec.decode(BackupCodec.encode(bad)) }
    }

    @Test
    fun `not json at all is refused with a readable message`() {
        val e = assertThrows<BackupException> { BackupCodec.decode("hello") }
        assertTrue(e.message!!.isNotBlank())
    }

    @Test
    fun `mapping to the domain keeps rules, pins and partial ticks`() {
        val specs = sample.toItemSpecs().associateBy { it.id }
        assertEquals(95_000, specs.getValue("brake_fluid").rule.pinnedKm)
        assertEquals(LocalDate.of(2026, 11, 23), specs.getValue("brake_fluid").pinSetOn)
        assertEquals(MonthDay.of(10, 15), specs.getValue("winter_tyres").rule.seasonal)
        val visits = sample.toVisitRecords()
        assertEquals(mapOf("engine_oil" to false, "brake_fluid" to true), visits[1].items)
        assertEquals(LocalDate.of(2026, 11, 23), sample.toReadings().single().date)
    }

    /** The owner's import file (git-ignored, spec D10) decodes and gives a status for every item. */
    @Test
    fun `the private import file decodes and every item gets a status`() {
        val file = File("../private/carcare-import.json")
        assumeTrue(file.exists(), "private import file not present")
        val backup = BackupCodec.decode(file.readText())
        val o = StatusEngine.overview(
            backup.toItemSpecs(), backup.toVisitRecords(), backup.toReadings(),
            LocalDate.now(), StatusSettings(),
        )
        assertTrue(o.items.isNotEmpty())
        assertEquals(backup.items.map { it.id }.toSet(), o.items.map { it.spec.id }.toSet())
    }
}
