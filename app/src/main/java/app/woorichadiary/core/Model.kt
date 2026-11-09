package app.woorichadiary.core

enum class Seat(val label: String) {
    DRIVER("운전석"),
    PASSENGER("동승석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_RIGHT("뒷좌석 오른쪽"),
}

/** Family setup chosen on first launch. */
data class Family(
    val carName: String,
    val riders: Map<Seat, String>,
    val guardianConsent: Boolean,
)

/** Everything the app reacts to, whether from the vehicle or from demo mode. */
sealed interface CarEvent {
    data class SeatChanged(val seat: Seat, val occupied: Boolean) : CarEvent
    data class BeltChanged(val seat: Seat, val buckled: Boolean) : CarEvent
    data class DrivingChanged(val driving: Boolean) : CarEvent
    data class WiperChanged(val on: Boolean) : CarEvent
    data class OdometerChanged(val km: Double) : CarEvent
    data class BatteryChanged(val percent: Int) : CarEvent
}

enum class WindowKind { WAITING, IN_CAR, DRIVING }

/** A stretch of time the car remembers. Only times are kept, never places. */
data class TimeWindow(val kind: WindowKind, val start: Long, val end: Long) {
    operator fun contains(time: Long) = time in start..end
}

/** The facts of one day, in the order the car would want to tell them. */
data class DayLog(
    val date: String,
    val distanceKm: Double = 0.0,
    val rain: Boolean = false,
    val riders: Set<String> = emptySet(),
    val charged: Boolean = false,
    val waitedMinutes: Long = 0,
    val outing: String? = null,
    val windows: List<TimeWindow> = emptyList(),
)

/** What the car does in response. */
sealed interface CarAction {
    /** Speak and show a line. Never emitted while driving. */
    data class Say(val text: String, val kind: Kind = Kind.TALK) : CarAction

    /** Ask a question that buttons on screen can answer. */
    data class Ask(val text: String, val choices: List<String>) : CarAction

    /** Show the exact state to the parent alongside the car's playful line. */
    data class ParentNotice(val text: String) : CarAction

    /** Show the photos the parent approved. Parked only, like everything else on screen. */
    data object ShowPhotos : CarAction

    /** The condition the parent was told about is resolved. */
    data object ClearNotice : CarAction

    /** Driving started: stop talking and clear the screen. */
    data object Silence : CarAction

    enum class Kind { TALK, DIARY, HUG, CARE }
}
