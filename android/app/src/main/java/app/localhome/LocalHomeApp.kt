package app.localhome

import android.app.Application
import app.localhome.data.Home

class LocalHomeApp : Application() {
    val home: Home by lazy { Home(this) }
}
