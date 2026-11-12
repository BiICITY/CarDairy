package app.woorichadiary.core

/**
 * The car's day as a state machine. Pure Kotlin so every scene is testable without a vehicle.
 *
 * Rule that outranks everything else: nothing is spoken while driving. Lines that come up on
 * the road are held and released once the car is parked.
 */
class JourneyMachine(
    private val family: Family,
    private val today: () -> String,
    private val nowMillis: () -> Long,
    private var yesterday: DayLog? = null,
    initialLog: DayLog? = null,
    private var photosWaiting: Boolean = false,
) {
    enum class Phase { IDLE, BOARDED, DRIVING, PARKED, WAITING }

    var phase = Phase.IDLE
        private set
    var log = initialLog ?: DayLog(date = today())
        private set

    private val occupied = mutableSetOf<Seat>()
    private val hugged = mutableSetOf<Seat>()
    private val held = mutableListOf<CarAction>()
    private var driving = false
    private var odometerAtStart: Double? = null
    private var lastOdometer: Double? = null
    private var waitStartedAt: Long? = null
    private var boardedAt: Long? = null
    private var drivingSince: Long? = null
    private var lowBatteryWarned = false
    private var lastBattery: Int? = null

    fun onEvent(event: CarEvent): List<CarAction> {
        rollDayIfNeeded()
        val actions = when (event) {
            is CarEvent.SeatChanged -> onSeat(event)
            is CarEvent.BeltChanged -> onBelt(event)
            is CarEvent.DrivingChanged -> onDriving(event.driving)
            is CarEvent.WiperChanged -> onWiper(event.on)
            is CarEvent.OdometerChanged -> onOdometer(event.km)
            is CarEvent.BatteryChanged -> onBattery(event.percent)
        }
        if (driving) {
            held += actions.filterNot { it is CarAction.Silence }
            return actions.filterIsInstance<CarAction.Silence>()
        }
        return actions
    }

    /** Someone was already seated when the app started. That is a fact, not a boarding. */
    fun syncSeat(seat: Seat, isOccupied: Boolean) {
        if (isOccupied) {
            occupied += seat
            if (boardedAt == null) boardedAt = nowMillis()
            if (phase == Phase.IDLE) phase = Phase.BOARDED
        }
    }

    /** The parent approved photos from an outing; the car reacts at the next boarding. */
    fun onPhotosApproved() {
        photosWaiting = true
    }

    /** A child answered the "what did you do?" question. */
    fun onAnswer(answer: String): List<CarAction> {
        if (answer == SECRET) return listOf(CarAction.Say("비밀이구나! 궁금하지만 참을게.", CarAction.Kind.DIARY))
        log = log.copy(outing = answer)
        return listOf(CarAction.Say("${answer}! 일기에 꼭 적어 둘게.", CarAction.Kind.DIARY))
    }

    private fun onSeat(event: CarEvent.SeatChanged): List<CarAction> {
        if (!event.occupied) {
            occupied -= event.seat
            hugged -= event.seat
            if (occupied.isEmpty() && !driving) {
                phase = Phase.WAITING
                waitStartedAt = nowMillis()
                boardedAt?.let { addWindow(WindowKind.IN_CAR, it, nowMillis()) }
                boardedAt = null
            }
            return emptyList()
        }

        val firstAboard = occupied.isEmpty()
        occupied += event.seat
        val rider = family.riders[event.seat]
        rider?.let { log = log.copy(riders = log.riders + it) }
        if (driving) return emptyList()

        if (boardedAt == null) boardedAt = nowMillis()
        val waited = waitStartedAt
        val actions = when {
            firstAboard && waited != null -> {
                waitStartedAt = null
                phase = Phase.BOARDED
                addWindow(WindowKind.WAITING, waited, nowMillis())
                val minutes = (nowMillis() - waited) / 60_000
                log = log.copy(waitedMinutes = log.waitedMinutes + minutes)
                listOf(CarAction.Ask(welcomeBack(rider, minutes), OUTING_CHOICES))
            }
            rider != null && phase != Phase.BOARDED -> {
                phase = Phase.BOARDED
                listOf(greeting(rider))
            }
            rider != null -> listOf(CarAction.Say("${Korean.call(rider)}, 어서 와!"))
            else -> {
                if (phase == Phase.IDLE) phase = Phase.BOARDED
                emptyList()
            }
        }
        // The driver often gets in first, so a child boarding also counts as "the family is back".
        return if (firstAboard || rider != null) withPhotoReaction(actions) else actions
    }

    private fun withPhotoReaction(actions: List<CarAction>): List<CarAction> {
        if (!photosWaiting) return actions
        photosWaiting = false
        val reaction = "사진 봤어! 정말 멋지다."
        val spoken = when (val first = actions.firstOrNull()) {
            is CarAction.Say -> listOf(first.copy(text = "$reaction ${first.text}")) + actions.drop(1)
            is CarAction.Ask -> listOf(first.copy(text = "$reaction ${first.text}")) + actions.drop(1)
            else -> listOf(CarAction.Say(reaction)) + actions
        }
        return listOf(CarAction.ShowPhotos) + spoken
    }

    private fun onBelt(event: CarEvent.BeltChanged): List<CarAction> {
        if (!event.buckled) {
            hugged -= event.seat
            return emptyList()
        }
        val rider = family.riders[event.seat] ?: return emptyList()
        if (!hugged.add(event.seat)) return emptyList()
        return listOf(
            CarAction.Say("${Korean.subject(family.carName)} ${Korean.objectOf(rider)} 꼭 안아 줄게!", CarAction.Kind.HUG),
        )
    }

    private fun onDriving(nowDriving: Boolean): List<CarAction> {
        if (nowDriving == driving) return emptyList()
        driving = nowDriving
        if (nowDriving) {
            phase = Phase.DRIVING
            odometerAtStart = lastOdometer
            drivingSince = nowMillis()
            if (boardedAt == null) boardedAt = nowMillis()
            return listOf(CarAction.Silence)
        }
        phase = Phase.PARKED
        addTripDistance()
        drivingSince?.let { addWindow(WindowKind.DRIVING, it, nowMillis()) }
        drivingSince = null
        return held.toList().also { held.clear() }
    }

    private fun addWindow(kind: WindowKind, start: Long, end: Long) {
        log = log.copy(windows = log.windows + TimeWindow(kind, start, end))
    }

    private fun onWiper(on: Boolean): List<CarAction> {
        if (on) log = log.copy(rain = true)
        return emptyList()
    }

    private fun onOdometer(km: Double): List<CarAction> {
        lastOdometer = km
        if (driving && odometerAtStart == null) odometerAtStart = km
        return emptyList()
    }

    private fun onBattery(percent: Int): List<CarAction> {
        val previous = lastBattery
        lastBattery = percent
        if (percent <= LOW_BATTERY && !lowBatteryWarned) {
            lowBatteryWarned = true
            return listOf(
                CarAction.Say("배고파서 힘이 없어. 밥 좀 주세요!", CarAction.Kind.CARE),
                CarAction.ParentNotice("배터리 잔량 $percent%. 충전이 필요합니다."),
            )
        }
        if (lowBatteryWarned && previous != null && percent >= previous + CHARGE_STEP) {
            lowBatteryWarned = false
            log = log.copy(charged = true)
            return listOf(CarAction.Say("밥 먹었더니 힘이 나! 고마워!", CarAction.Kind.HUG), CarAction.ClearNotice)
        }
        if (previous != null && percent >= previous + CHARGE_STEP) log = log.copy(charged = true)
        return emptyList()
    }

    private fun greeting(rider: String): CarAction {
        val diary = yesterday?.let { DiaryEngine.read(it) }
        val hello = "${Korean.call(rider)}, 안녕!"
        return if (diary == null) {
            CarAction.Say("$hello 나는 ${family.carName}${copula(family.carName)}. 오늘부터 일기를 쓸 거야.")
        } else {
            CarAction.Say("$hello 어제 일기 들려줄게. $diary", CarAction.Kind.DIARY)
        }
    }

    private fun welcomeBack(rider: String?, minutes: Long): String {
        val who = rider?.let { "${Korean.call(it)}, " }.orEmpty()
        return "${who}나 ${DiaryEngine.describeWait(minutes)} 기다렸어. 뭐 하고 왔어?"
    }

    private fun copula(name: String) = if (Korean.call(name).endsWith("아")) "이야" else "야"

    private fun addTripDistance() {
        val start = odometerAtStart
        val end = lastOdometer
        if (start != null && end != null && end > start) {
            log = log.copy(distanceKm = log.distanceKm + (end - start))
        }
        odometerAtStart = null
    }

    private fun rollDayIfNeeded() {
        val date = today()
        if (date != log.date) {
            yesterday = log
            log = DayLog(date = date)
        }
    }

    companion object {
        const val LOW_BATTERY = 20
        const val CHARGE_STEP = 5
        const val SECRET = "비밀이야"
        val OUTING_CHOICES = listOf("바다", "놀이터", "할머니 집", SECRET)
    }
}
