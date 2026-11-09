package app.woorichadiary

import android.content.Context
import app.woorichadiary.core.DayLog
import app.woorichadiary.core.Family
import app.woorichadiary.core.Seat
import app.woorichadiary.core.TimeWindow
import app.woorichadiary.core.WindowKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything stays on the vehicle. Children's names and logs are never sent anywhere. */
class DiaryStore(context: Context) {
    private val file = File(context.filesDir, "diary.json")
    private val photosRoot = File(context.filesDir, "photos")

    private fun root(): JSONObject =
        runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

    fun loadFamily(): Family? {
        val json = root().optJSONObject("family") ?: return null
        val riders = json.getJSONObject("riders").let { r ->
            r.keys().asSequence().associate { Seat.valueOf(it) to r.getString(it) }
        }
        return Family(json.getString("carName"), riders, json.getBoolean("consent"))
    }

    fun saveFamily(family: Family) {
        val riders = JSONObject().apply { family.riders.forEach { (seat, name) -> put(seat.name, name) } }
        write(root().put("family", JSONObject()
            .put("carName", family.carName)
            .put("riders", riders)
            .put("consent", family.guardianConsent)))
    }

    /** Latest log whose date is before [date]. */
    fun lastLogBefore(date: String): DayLog? {
        val logs = root().optJSONObject("logs") ?: return null
        return logs.keys().asSequence().filter { it < date }.maxOrNull()
            ?.let { logs.getJSONObject(it).toDayLog(it) }
    }

    fun logOn(date: String): DayLog? =
        root().optJSONObject("logs")?.optJSONObject(date)?.toDayLog(date)

    fun saveLog(log: DayLog) {
        val r = root()
        val logs = r.optJSONObject("logs") ?: JSONObject()
        logs.put(log.date, JSONObject()
            .put("distanceKm", log.distanceKm)
            .put("rain", log.rain)
            .put("riders", JSONArray(log.riders.toList()))
            .put("charged", log.charged)
            .put("waitedMinutes", log.waitedMinutes)
            .put("outing", log.outing ?: JSONObject.NULL)
            .put("windows", JSONArray(log.windows.map { w ->
                JSONObject().put("kind", w.kind.name).put("start", w.start).put("end", w.end)
            })))
        write(r.put("logs", logs))
    }

    fun allLogs(): List<DayLog> {
        val logs = root().optJSONObject("logs") ?: return emptyList()
        return logs.keys().asSequence().toList().sorted().map { logs.getJSONObject(it).toDayLog(it) }
    }

    /** The most recently approved photos. */
    fun approvedPhotos(): List<File> =
        photoDir(root().optString("photosOuting")).listFiles { f -> f.extension == "jpg" }
            ?.sortedBy { it.name }.orEmpty()

    fun photosWaiting(): Boolean = root().optBoolean("photosWaiting")

    fun markPhotosApproved(outingId: String) {
        write(root().put("photosOuting", outingId).put("photosWaiting", true))
    }

    fun markPhotosShown() {
        write(root().put("photosWaiting", false))
    }

    fun photoDir(outingId: String) = File(photosRoot, outingId.filter(Char::isDigit))

    /** The guardian can erase all stored data at any time. */
    fun eraseAll() {
        file.delete()
        photosRoot.deleteRecursively()
    }

    private fun JSONObject.toDayLog(date: String) = DayLog(
        date = date,
        distanceKm = getDouble("distanceKm"),
        rain = getBoolean("rain"),
        riders = getJSONArray("riders").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
        charged = getBoolean("charged"),
        waitedMinutes = getLong("waitedMinutes"),
        outing = if (isNull("outing")) null else getString("outing"),
        windows = optJSONArray("windows")?.let { a ->
            (0 until a.length()).map { i ->
                val w = a.getJSONObject(i)
                TimeWindow(WindowKind.valueOf(w.getString("kind")), w.getLong("start"), w.getLong("end"))
            }
        }.orEmpty(),
    )

    private fun write(json: JSONObject) {
        val temp = File(file.parentFile, "diary.json.tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }
}
