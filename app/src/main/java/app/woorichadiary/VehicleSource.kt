package app.woorichadiary

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import app.woorichadiary.core.CarEvent
import app.woorichadiary.core.Seat
import java.lang.reflect.Proxy

/** Translates vehicle signals into [CarEvent]s. All SDK value quirks are handled in this file. */
class VehicleSource(
    private val context: Context,
    private val onEvent: (CarEvent) -> Unit,
) {
    private val keepAlive = mutableListOf<Any>()
    private val seatAreas = mapOf(
        Seat.DRIVER to SeatValue.ROW_1_LEFT,
        Seat.PASSENGER to SeatValue.ROW_1_RIGHT,
        Seat.REAR_LEFT to SeatValue.ROW_2_LEFT,
        Seat.REAR_RIGHT to SeatValue.ROW_2_RIGHT,
    )

    /** Signals that could not be subscribed; the UI offers a manual fallback for these. */
    val unavailable = mutableSetOf<String>()

    fun start() {
        val vehicle = Vehicle(context.applicationContext)
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle SDK unavailable", e)
            unavailable += "vehicle"
            return
        }
        keepAlive += vehicle

        guarded("seat") {
            val seat = vehicle.getSeat()
            seatAreas.forEach { (name, value) ->
                val area = SeatArea(value)
                val occupancy = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(isOccupied: Boolean?) {
                        isOccupied?.let { onEvent(CarEvent.SeatChanged(name, interpretOccupancy(it))) }
                    }
                    override fun onFailed(e: Exception) = logFailure("occupancy $name", e)
                }
                val belt = object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(isBuckled: Boolean?) {
                        isBuckled?.let { onEvent(CarEvent.BeltChanged(name, it)) }
                    }
                    override fun onFailed(e: Exception) = logFailure("belt $name", e)
                }
                keepAlive += occupancy
                keepAlive += belt
                seat.registerSeatOccupancy(area, occupancy)
                seat.registerSeatBeltBuckledState(area, belt)
            }
        }
        guarded("wiper") {
            val wiper = vehicle.getWiper()
            subscribe(wiper, "registerWiperState", WindowArea(WindowValue.FRONT_WINDSHIELD)) { value ->
                onEvent(CarEvent.WiperChanged(value.toString() == "ON"))
            }
        }
        guarded("odometer") {
            subscribe(vehicle.getOdometer(), "registerDistanceDriven", null) { value ->
                (value as? Number)?.let { onEvent(CarEvent.OdometerChanged(it.toDouble())) }
            }
        }
        guarded("battery") {
            subscribe(vehicle.getEvBattery(), "registerEvBatteryStateOfCharge", null) { value ->
                (value as? Number)?.let { onEvent(CarEvent.BatteryChanged(it.toInt())) }
            }
        }
        guarded("driving") { watchDriving() }
    }

    /**
     * In Emulator 2.1.0 this value arrives opposite to the Virtual Console setting: choosing
     * "Occupied" delivers false. Verify on a real vehicle before removing or keeping this.
     */
    private fun interpretOccupancy(raw: Boolean) = !raw

    private fun watchDriving() {
        val car = Car.createCar(context)
        keepAlive += car
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        keepAlive += manager
        onEvent(CarEvent.DrivingChanged(manager.currentCarUxRestrictions.isRequiresDistractionOptimization))
        manager.registerListener { restrictions ->
            onEvent(CarEvent.DrivingChanged(restrictions.isRequiresDistractionOptimization))
        }
    }

    /** Subscribes where the SDK's listener type differs by domain, reporting the first value. */
    private fun subscribe(domain: Any, method: String, area: Any?, onValue: (Any?) -> Unit) {
        val target = domain.javaClass.methods.first { it.name == method }
        val listenerType = target.parameterTypes.last()
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, m, args ->
            when {
                m.name == "equals" -> proxy === args?.firstOrNull()
                m.name == "hashCode" -> System.identityHashCode(proxy)
                m.name == "toString" -> method
                m.name == "onFailed" -> logFailure(method, args?.firstOrNull() as? Exception)
                else -> onValue(args?.firstOrNull())
            }
        }
        keepAlive += listener
        if (area == null) target.invoke(domain, listener) else target.invoke(domain, area, listener)
    }

    private inline fun guarded(signal: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$signal unavailable", e)
            unavailable += signal
        }
    }

    private fun logFailure(what: String, e: Exception?) {
        Log.w(TAG, "$what failed: ${e?.message}")
    }

    private companion object {
        const val TAG = "VehicleSource"
    }
}
