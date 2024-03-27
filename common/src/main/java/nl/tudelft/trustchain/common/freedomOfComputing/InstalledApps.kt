package nl.tudelft.trustchain.common.freedomOfComputing

object InstalledApps {
    var appNames: ArrayList<String> = ArrayList()

    fun init() {
    }

    fun addApp(activeApp: String,) {
        appNames.add(activeApp)
    }
}
