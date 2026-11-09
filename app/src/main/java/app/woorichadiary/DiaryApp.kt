package app.woorichadiary

import android.app.Application

class DiaryApp : Application() {
    val controller by lazy { DiaryController(this) }
}
