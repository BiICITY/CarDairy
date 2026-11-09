package app.woorichadiary.core

/** Korean particles depend on whether the last syllable has a final consonant. */
object Korean {
    private fun hasFinalConsonant(word: String): Boolean {
        val last = word.trim().lastOrNull() ?: return false
        if (last !in '가'..'힣') return false
        return (last - '가') % 28 != 0
    }

    /** 하준 -> 하준아, 콩이 -> 콩이야 */
    fun call(name: String) = name + if (hasFinalConsonant(name)) "아" else "야"

    /** 하준 -> 하준이를, 콩이 -> 콩이를 */
    fun objectOf(name: String) = name + if (hasFinalConsonant(name)) "이를" else "를"

    /** 하준 -> 하준이가, 콩이 -> 콩이가 */
    fun subject(name: String) = name + if (hasFinalConsonant(name)) "이가" else "가"
}
