package app.woorichadiary.core

import kotlin.math.roundToInt

/**
 * Turns recorded facts into diary lines with templates.
 * Lines come only from facts in [DayLog], so the car never invents its own condition.
 */
object DiaryEngine {
    private const val MAX_LINES = 3

    fun lines(log: DayLog): List<String> {
        val candidates = buildList {
            log.outing?.let { add("가족이 ${it}에 다녀왔대. 나도 궁금해!") }
            if (log.rain) add("비를 많이 맞았어. 와이퍼로 열심히 닦았어.")
            if (log.waitedMinutes >= 30) add("주차장에서 ${describeWait(log.waitedMinutes)} 기다렸어.")
            if (log.riders.isNotEmpty()) add("${joinNames(log.riders)} 태웠어.")
            if (log.charged) add("밥을 든든히 먹어서 힘이 나!")
            val km = log.distanceKm.roundToInt()
            if (km > 0) add("${km}km나 달렸어.")
        }
        return candidates.take(MAX_LINES)
    }

    /** Null when the day has nothing true to tell. */
    fun read(log: DayLog): String? = lines(log).takeIf { it.isNotEmpty() }?.joinToString(" ")

    fun describeWait(minutes: Long): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            minutes < 1 -> "잠깐"
            hours == 0L -> "${minutes}분"
            rest == 0L -> "${hours}시간"
            else -> "${hours}시간 ${rest}분"
        }
    }

    private fun joinNames(names: Set<String>): String {
        val sorted = names.sorted()
        return sorted.dropLast(1).joinToString("") { "$it, " } + Korean.objectOf(sorted.last())
    }
}
