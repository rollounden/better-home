package app.betterhome

import android.app.Application
import app.betterhome.data.Home

class BetterHomeApp : Application() {
    val home: Home by lazy { Home(this) }
}
