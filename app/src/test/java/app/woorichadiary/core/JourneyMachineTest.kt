package app.woorichadiary.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMachineTest {
    private var now = 0L
    private var date = "2026-11-07"
    private val family = Family("콩이", mapOf(Seat.REAR_LEFT to "하준"), guardianConsent = true)

    private fun machine(yesterday: DayLog? = null) =
        JourneyMachine(family, { date }, { now }, yesterday)

    private fun List<CarAction>.spoken() = filterIsInstance<CarAction.Say>().map { it.text }

    @Test fun greetsRiderWithYesterdaysDiary() {
        val m = machine(DayLog("2026-11-06", distanceKm = 12.0, rain = true))
        val text = m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true)).spoken().single()
        assertTrue(text, text.startsWith("하준아, 안녕!"))
        assertTrue(text, "비를 많이 맞았어" in text)
        assertTrue(text, "12km" in text)
    }

    @Test fun firstEverRideIntroducesTheCar() {
        val text = machine().onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true)).spoken().single()
        assertTrue(text, "나는 콩이야" in text)
    }

    @Test fun beltHugsOncePerBuckle() {
        val m = machine()
        m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true))
        val first = m.onEvent(CarEvent.BeltChanged(Seat.REAR_LEFT, true))
        assertEquals("콩이가 하준이를 꼭 안아 줄게!", first.spoken().single())
        assertTrue(m.onEvent(CarEvent.BeltChanged(Seat.REAR_LEFT, true)).isEmpty())
        m.onEvent(CarEvent.BeltChanged(Seat.REAR_LEFT, false))
        assertEquals(1, m.onEvent(CarEvent.BeltChanged(Seat.REAR_LEFT, true)).size)
    }

    @Test fun neverSpeaksWhileDrivingAndReleasesAfterParking() {
        val m = machine()
        m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true))
        assertEquals(listOf<CarAction>(CarAction.Silence), m.onEvent(CarEvent.DrivingChanged(true)))
        assertTrue(m.onEvent(CarEvent.BatteryChanged(15)).isEmpty())
        assertTrue(m.onEvent(CarEvent.BeltChanged(Seat.REAR_LEFT, true)).isEmpty())
        val released = m.onEvent(CarEvent.DrivingChanged(false))
        assertTrue(released.spoken().any { "배고파서" in it })
        assertTrue(released.spoken().any { "안아 줄게" in it })
    }

    @Test fun recordsTripDistanceAndRainWhileDriving() {
        val m = machine()
        m.onEvent(CarEvent.OdometerChanged(100.0))
        m.onEvent(CarEvent.DrivingChanged(true))
        m.onEvent(CarEvent.WiperChanged(true))
        m.onEvent(CarEvent.OdometerChanged(112.0))
        m.onEvent(CarEvent.DrivingChanged(false))
        assertEquals(12.0, m.log.distanceKm, 0.001)
        assertTrue(m.log.rain)
    }

    @Test fun asksAboutTheOutingAfterWaiting() {
        val m = machine()
        m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true))
        m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, false))
        now = 240 * 60_000L
        val ask = m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true)).single() as CarAction.Ask
        assertTrue(ask.text, "4시간 기다렸어" in ask.text)
        assertEquals(240L, m.log.waitedMinutes)
        m.onAnswer("바다")
        assertEquals("바다", m.log.outing)
    }

    @Test fun aSecretIsNotWrittenInTheDiary() {
        val m = machine()
        m.onAnswer(JourneyMachine.SECRET)
        assertEquals(null, m.log.outing)
    }

    @Test fun batteryAlertThenThanksAfterCharging() {
        val m = machine()
        m.onEvent(CarEvent.BatteryChanged(50))
        val low = m.onEvent(CarEvent.BatteryChanged(18))
        assertTrue(low.spoken().single().contains("배고파서"))
        assertTrue(low.any { it is CarAction.ParentNotice && "18%" in it.text })
        assertTrue(m.onEvent(CarEvent.BatteryChanged(17)).isEmpty())
        val fed = m.onEvent(CarEvent.BatteryChanged(40))
        assertTrue(fed.spoken().single().contains("고마워"))
        assertTrue(CarAction.ClearNotice in fed)
        assertTrue(m.log.charged)
    }

    @Test fun newDayStartsAFreshLogAndKeepsYesterdayForTheDiary() {
        val m = machine()
        m.onEvent(CarEvent.WiperChanged(true))
        date = "2026-11-08"
        val text = m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true)).spoken().single()
        assertTrue(text, "비를 많이 맞았어" in text)
        assertEquals("2026-11-08", m.log.date)
    }

    @Test fun particlesFollowFinalConsonant() {
        assertEquals("하준아", Korean.call("하준"))
        assertEquals("콩이야", Korean.call("콩이"))
        assertEquals("하준이가", Korean.subject("하준"))
        assertEquals("콩이가", Korean.subject("콩이"))
    }

    @Test fun diaryOnlySaysWhatHappened() {
        assertEquals(null, DiaryEngine.read(DayLog("2026-11-07")))
        assertEquals(3, DiaryEngine.lines(
            DayLog("d", 5.0, true, setOf("하준"), true, 60, "바다"),
        ).size)
    }
}
