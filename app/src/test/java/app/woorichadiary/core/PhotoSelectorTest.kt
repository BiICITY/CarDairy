package app.woorichadiary.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSelectorTest {
    private val min = 60_000L
    private val before = TimeWindow(WindowKind.IN_CAR, 0, 60 * min)
    private val drive = TimeWindow(WindowKind.DRIVING, 10 * min, 60 * min)
    private val wait = TimeWindow(WindowKind.WAITING, 60 * min, 300 * min)
    private val after = TimeWindow(WindowKind.IN_CAR, 300 * min, 360 * min)
    private val outing = PhotoSelector.outings(listOf(before, drive, wait, after)).single()

    private fun photo(id: Int, at: Long, driver: Boolean = false) = PhotoMeta("p$id", at, driver)

    @Test fun oneHundredPhotosBecomeSevenAcrossTheOuting() {
        val hundred = List(100) { photo(it, 60 * min + it * 2 * min) }
        val chosen = PhotoSelector.select(outing, hundred).chosen
        assertEquals(7, chosen.size)
        assertTrue(chosen.first().takenAt < 100 * min)
        assertTrue(chosen.last().takenAt > 220 * min)
    }

    @Test fun mixesFiveAwayAndTwoInCar() {
        val away = List(20) { photo(it, 70 * min + it * 5 * min) }
        val car = List(5) { photo(100 + it, 310 * min + it * min) }
        val chosen = PhotoSelector.select(outing, away + car).chosen
        assertEquals(5, chosen.count { it.takenAt in wait })
        assertEquals(2, chosen.count { it.takenAt in after })
    }

    @Test fun fillsWithAwayPhotosWhenNoneInCar() {
        val away = List(20) { photo(it, 70 * min + it * 5 * min) }
        assertEquals(7, PhotoSelector.select(outing, away).chosen.size)
    }

    @Test fun driverPhoneWhileDrivingIsNeverSuggested() {
        val risky = photo(1, 30 * min, driver = true)
        val passenger = photo(2, 31 * min, driver = false)
        val parkedDriver = photo(3, 5 * min, driver = true)
        val result = PhotoSelector.select(outing, listOf(risky, passenger, parkedDriver))
        assertFalse(risky in result.chosen)
        assertTrue(passenger in result.chosen)
        assertTrue(parkedDriver in result.chosen)
        assertEquals(1, result.excludedForSafety)
    }

    @Test fun ignoresPhotosOutsideTheOuting() {
        val result = PhotoSelector.select(outing, listOf(photo(1, 500 * min)))
        assertTrue(result.chosen.isEmpty())
    }

    @Test fun burstShotsCountOnce() {
        val burst = List(10) { photo(it, 100 * min + it * 1000L) }
        val other = photo(50, 200 * min)
        val chosen = PhotoSelector.select(outing, burst + other).chosen
        assertEquals(7, chosen.size)
        assertTrue(other in chosen)
        val two = PhotoSelector.select(
            outing.copy(inCar = emptyList()),
            burst.take(2) + List(5) { photo(60 + it, 120 * min + it * min) } + List(5) { photo(70 + it, 180 * min + it * min) },
        ).chosen
        assertEquals(1, two.count { it in burst.take(2) })
    }

    @Test fun overnightParkingIsNotAnOuting() {
        val night = TimeWindow(WindowKind.WAITING, 0, 13 * 60 * min)
        assertTrue(PhotoSelector.outings(listOf(night)).isEmpty())
    }

    @Test fun photosAppearWhenTheChildBoardsAfterTheDriver() {
        val family = Family("콩이", mapOf(Seat.REAR_LEFT to "하준"), true)
        val m = JourneyMachine(family, { "d" }, { 0L }, photosWaiting = true)
        val driverFirst = m.onEvent(CarEvent.SeatChanged(Seat.DRIVER, true))
        assertEquals(CarAction.ShowPhotos, driverFirst.first())
        val m2 = JourneyMachine(family, { "d" }, { 0L })
        m2.onEvent(CarEvent.SeatChanged(Seat.DRIVER, true))
        m2.onPhotosApproved()
        assertEquals(CarAction.ShowPhotos, m2.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true)).first())
        assertTrue(m2.onEvent(CarEvent.SeatChanged(Seat.PASSENGER, true)).none { it is CarAction.ShowPhotos })
    }

    @Test fun seatAlreadyOccupiedAtStartupDoesNotUsePhotosOrGreet() {
        val family = Family("콩이", mapOf(Seat.REAR_LEFT to "하준"), true)
        val m = JourneyMachine(family, { "d" }, { 0L }, photosWaiting = true)
        m.syncSeat(Seat.DRIVER, true)
        val actions = m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true))
        assertEquals(CarAction.ShowPhotos, actions.first())
    }

    @Test fun machineRecordsWindowsAndReactsToPhotosAtNextBoarding() {
        var now = 0L
        val family = Family("콩이", mapOf(Seat.REAR_LEFT to "하준"), true)
        val m = JourneyMachine(family, { "d" }, { now })
        m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true))
        now = 10 * min; m.onEvent(CarEvent.DrivingChanged(true))
        now = 60 * min; m.onEvent(CarEvent.DrivingChanged(false))
        m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, false))
        now = 300 * min
        m.onPhotosApproved()
        val actions = m.onEvent(CarEvent.SeatChanged(Seat.REAR_LEFT, true))

        assertEquals(CarAction.ShowPhotos, actions.first())
        assertTrue((actions[1] as CarAction.Ask).text.startsWith("사진 봤어!"))
        assertEquals(
            listOf(WindowKind.DRIVING, WindowKind.IN_CAR, WindowKind.WAITING),
            m.log.windows.map { it.kind },
        )
        assertEquals(wait.start, PhotoSelector.outings(m.log.windows).single().waiting.start)
    }
}
