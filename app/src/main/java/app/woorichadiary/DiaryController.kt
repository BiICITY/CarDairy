package app.woorichadiary

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.woorichadiary.core.CarAction
import app.woorichadiary.core.CarEvent
import app.woorichadiary.core.DayLog
import app.woorichadiary.core.Family
import app.woorichadiary.core.JourneyMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate

data class UiState(
    val family: Family? = null,
    val line: String? = null,
    val kind: CarAction.Kind = CarAction.Kind.TALK,
    val choices: List<String> = emptyList(),
    val parentNotice: String? = null,
    val driving: Boolean = false,
    val today: DayLog = DayLog(LocalDate.now().toString()),
    val unavailableSignals: Set<String> = emptySet(),
    val photoPageUrl: String? = null,
    val photos: List<File> = emptyList(),
)

/** Owns the journey machine and is the only place where events become speech and UI. */
class DiaryController(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val store = DiaryStore(appContext)
    private val voice by lazy { CarVoice(appContext) }
    private val source = VehicleSource(appContext, ::postVehicleEvent)
    private val photoServer = PhotoServer(appContext, store, ::onPhotosUploaded)
    private val lastEvents = mutableMapOf<String, CarEvent>()
    private var machine: JourneyMachine? = null
    private var started = false

    private val _state = MutableStateFlow(UiState(family = store.loadFamily()))
    val state: StateFlow<UiState> = _state

    init {
        _state.value.family?.let { createMachine(it) }
    }

    fun startVehicle() {
        if (started) return
        started = true
        source.start()
        _state.update { it.copy(unavailableSignals = source.unavailable.toSet()) }
    }

    fun completeSetup(family: Family) {
        store.saveFamily(family)
        createMachine(family)
        _state.update { it.copy(family = family) }
    }

    /** The first report for each seat is its state at startup, so it only sets the baseline. */
    private fun postVehicleEvent(event: CarEvent) {
        if (event is CarEvent.SeatChanged) {
            main.post {
                val key = event::class.simpleName + keySuffix(event)
                if (key !in lastEvents) {
                    lastEvents[key] = event
                    machine?.syncSeat(event.seat, event.occupied)
                } else {
                    postEvent(event)
                }
            }
        } else {
            postEvent(event)
        }
    }

    /** Vehicle callbacks arrive on SDK threads and repeat values, so normalize both here. */
    private fun postEvent(event: CarEvent) {
        main.post {
            val key = event::class.simpleName + keySuffix(event)
            if (lastEvents[key] == event) return@post
            val isFirstFalse = lastEvents[key] == null && isIdleDefault(event)
            lastEvents[key] = event
            if (!isFirstFalse) handle(event)
        }
    }

    /** Demo mode goes through the same path as the vehicle. */
    fun inject(event: CarEvent) = postEvent(event)

    fun answer(choice: String) {
        val m = machine ?: return
        _state.update { it.copy(choices = emptyList()) }
        apply(m.onAnswer(choice))
        persist(m)
    }

    /** Opens the phone page. The server runs only while this is showing and the car is parked. */
    fun openPhotoPage() {
        if (_state.value.driving) return
        val url = photoServer.start()
        _state.update { it.copy(photoPageUrl = url) }
    }

    fun closePhotoPage() {
        photoServer.stop()
        _state.update { it.copy(photoPageUrl = null) }
    }

    private fun onPhotosUploaded(outingId: String) {
        main.post {
            store.markPhotosApproved(outingId)
            machine?.onPhotosApproved()
            // The phone is still waiting for this request's reply, so close only after it is sent.
            main.postDelayed(::closePhotoPage, PAGE_CLOSE_DELAY_MILLIS)
        }
    }

    fun dismissNotice() = _state.update { it.copy(parentNotice = null) }

    fun eraseAll() {
        photoServer.stop()
        store.eraseAll()
        machine = null
        lastEvents.clear()
        voice.stop()
        _state.value = UiState(unavailableSignals = source.unavailable.toSet())
    }

    private fun handle(event: CarEvent) {
        if (event is CarEvent.DrivingChanged) {
            _state.update { it.copy(driving = event.driving) }
            if (event.driving) closePhotoPage()
        }
        val m = machine ?: return
        apply(m.onEvent(event))
        persist(m)
    }

    private fun apply(actions: List<CarAction>) {
        actions.forEach { action ->
            when (action) {
                is CarAction.Say -> {
                    _state.update { it.copy(line = action.text, kind = action.kind, choices = emptyList()) }
                    voice.say(action.text)
                }
                is CarAction.Ask -> {
                    _state.update { it.copy(line = action.text, kind = CarAction.Kind.TALK, choices = action.choices) }
                    voice.say(action.text)
                }
                is CarAction.ParentNotice -> _state.update { it.copy(parentNotice = action.text) }
                CarAction.ShowPhotos -> {
                    store.markPhotosShown()
                    _state.update { it.copy(photos = store.approvedPhotos()) }
                }
                CarAction.ClearNotice -> _state.update { it.copy(parentNotice = null) }
                CarAction.Silence -> {
                    voice.stop()
                    _state.update { it.copy(line = null, choices = emptyList(), photos = emptyList()) }
                }
            }
        }
    }

    private fun persist(m: JourneyMachine) {
        store.saveLog(m.log)
        _state.update { it.copy(today = m.log) }
    }

    private fun createMachine(family: Family) {
        val date = LocalDate.now().toString()
        machine = JourneyMachine(
            family = family,
            today = { LocalDate.now().toString() },
            nowMillis = System::currentTimeMillis,
            yesterday = store.lastLogBefore(date),
            initialLog = store.logOn(date),
            photosWaiting = store.photosWaiting(),
        )
    }

    private companion object {
        const val PAGE_CLOSE_DELAY_MILLIS = 1_500L
    }

    private fun keySuffix(event: CarEvent) = when (event) {
        is CarEvent.SeatChanged -> event.seat.name
        is CarEvent.BeltChanged -> event.seat.name
        else -> ""
    }

    private fun isIdleDefault(event: CarEvent) = when (event) {
        is CarEvent.SeatChanged -> !event.occupied
        is CarEvent.BeltChanged -> !event.buckled
        is CarEvent.WiperChanged -> !event.on
        is CarEvent.DrivingChanged -> !event.driving
        else -> false
    }
}
