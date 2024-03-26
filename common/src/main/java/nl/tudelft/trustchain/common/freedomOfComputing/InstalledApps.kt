package nl.tudelft.trustchain.common.freedomOfComputing

import android.app.Activity

object InstalledApps {
    var appNames: ArrayList<String> = ArrayList()
    var appClasses: ArrayList<Class<out Activity>> = ArrayList()

    fun init() {
    }

    @Suppress("UNCHECKED_CAST")
    fun addApp(
        activeApp: String,
        fragmentClass: Class<*>
    ) {
        val fc = fragmentClass as Class<out Activity>
        appNames.add(activeApp)
        appClasses.add(fc)
    }
}
