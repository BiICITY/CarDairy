package app.woorichadiary.core

/** What the phone tells the car about a photo. The picture itself stays on the phone. */
data class PhotoMeta(val id: String, val takenAt: Long, val driverPhone: Boolean)

/** One time the family left the car: the wait, plus the in-car time right around it. */
data class Outing(
    val waiting: TimeWindow,
    val inCar: List<TimeWindow>,
    val driving: List<TimeWindow>,
) {
    val id: String get() = waiting.start.toString()
}

data class Selection(val chosen: List<PhotoMeta>, val matched: Int, val excludedForSafety: Int)

/**
 * Picks photos purely from timestamps, so no location is needed.
 *
 * A photo taken on the driver's phone during driving is never suggested, whoever took it.
 * Recommending those would reward taking pictures at the wheel.
 */
object PhotoSelector {
    const val MAX_PHOTOS = 7
    private const val AWAY_SHARE = 5
    private const val BURST_MILLIS = 20_000L
    private const val LONGEST_OUTING_MILLIS = 12 * 60 * 60 * 1000L

    /** Waits longer than half a day are overnight parking, not outings. */
    fun outings(windows: List<TimeWindow>): List<Outing> {
        val inCar = windows.filter { it.kind == WindowKind.IN_CAR }
        val driving = windows.filter { it.kind == WindowKind.DRIVING }
        return windows
            .filter { it.kind == WindowKind.WAITING && it.end - it.start <= LONGEST_OUTING_MILLIS }
            .sortedByDescending { it.start }
            .map { wait ->
                val around = inCar.filter { it.end == wait.start || it.start == wait.end }
                Outing(wait, around, driving.filter { d -> around.any { d.start in it || d.end in it } })
            }
    }

    fun select(outing: Outing, photos: List<PhotoMeta>): Selection {
        val away = photos.filter { it.takenAt in outing.waiting }
        val inCar = photos.filter { p -> outing.inCar.any { p.takenAt in it } }
        val safeInCar = inCar.filterNot { p -> p.driverPhone && outing.driving.any { p.takenAt in it } }

        val carTarget = minOf(MAX_PHOTOS - AWAY_SHARE, safeInCar.size)
        val awayCount = minOf(MAX_PHOTOS - carTarget, away.size)
        val carCount = minOf(MAX_PHOTOS - awayCount, safeInCar.size)

        return Selection(
            chosen = (spread(away, awayCount) + spread(safeInCar, carCount)).sortedBy { it.takenAt },
            matched = away.size + safeInCar.size,
            excludedForSafety = inCar.size - safeInCar.size,
        )
    }

    /** Covers the whole stretch of time evenly, skipping shots from the same burst first. */
    private fun spread(photos: List<PhotoMeta>, count: Int): List<PhotoMeta> {
        if (count <= 0) return emptyList()
        val sorted = photos.sortedBy { it.takenAt }
        val distinct = sorted.fold(mutableListOf<PhotoMeta>()) { kept, photo ->
            if (kept.isEmpty() || photo.takenAt - kept.last().takenAt >= BURST_MILLIS) kept += photo
            kept
        }
        if (distinct.size >= count) {
            return List(count) { i -> distinct[(i * 2 + 1) * distinct.size / (count * 2)] }
        }
        return distinct + (sorted - distinct.toSet()).take(count - distinct.size)
    }
}
