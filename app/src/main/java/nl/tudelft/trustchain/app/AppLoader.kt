package nl.tudelft.trustchain.app

import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import nl.tudelft.trustchain.app.ui.dashboard.DashboardItem
import nl.tudelft.trustchain.common.R
import nl.tudelft.trustchain.common.freedomOfComputing.InstalledApps

class AppLoader(
    private val dataStore: DataStore<Preferences>,
    private val firstRun: Boolean = false
) {
    val preferredApps: List<DashboardItem>
        get() = apps.filter { it.isPreferred }
    var apps: Set<DashboardItem>

    @DrawableRes
    val icon = R.drawable.ic_bug_report_black_24dp

    @ColorRes
    val color = R.color.dark_gray

    init {
        runBlocking {
            if (firstRun) {
                apps = AppDefinition.BaseAppDefinitions.entries.map { DashboardItem(it.appDefinition) }.toSet()
                setPreferredAppList(DEFAULT_APPS)
            } else {
                val pApps = getPreferredAppList()
                val tempApps =
                    AppDefinition.BaseAppDefinitions.entries.map { app ->
                        DashboardItem(
                            app.appDefinition,
                            isPreferred = pApps.contains(app.appDefinition.appName)
                        )
                    }.toMutableSet()
                Log.i("foc installer", "installing")
                Log.i("foc installer", "$color")
                for (i in 0..<InstalledApps.appNames.size) {
                    val name = InstalledApps.appNames[i]
                    tempApps.add(
                        DashboardItem(
                            FOCAppDefinition(
                                icon,
                                name,
                                color,
                                name
                            ),
//                                AppDefinition.create(name, javaClass),
                            isPreferred = true
                        )
                    )
                }
                apps = tempApps.toSet()
            }
        }
    }

    fun update() =
        runBlocking {
            if (firstRun) {
                apps = AppDefinition.BaseAppDefinitions.entries.map { DashboardItem(it.appDefinition) }.toSet()
                setPreferredAppList(DEFAULT_APPS)
            } else {
                val pApps = getPreferredAppList()
                val tempApps =
                    AppDefinition.BaseAppDefinitions.entries.map { app ->
                        DashboardItem(
                            app.appDefinition,
                            isPreferred = pApps.contains(app.appDefinition.appName)
                        )
                    }.toMutableSet()
                // TODO add FOC apps
                Log.i("foc installer", "installing")
                for (i in 0..<InstalledApps.appNames.size) {
                    val name = InstalledApps.appNames[i]
                    tempApps.add(
                        DashboardItem(
                            FOCAppDefinition(
                                icon,
                                name,
                                color,
                                name + ".apk"
                            ),
//                                AppDefinition.create(name, javaClass),
                            isPreferred = true
                        )
                    )
                }
                apps = tempApps.toSet()
            }
        }

    suspend fun setPreferredApp(app: String) {
        val newApps = preferredApps.map { it.app.appName }.toMutableSet()
        newApps.add(app)
        return setPreferredAppList(newApps)
    }

    suspend fun removePreferredApp(app: String) {
        val newApps = preferredApps.map { it.app.appName }.toMutableSet()
        newApps.remove(app)
        return setPreferredAppList(newApps)
    }

    private suspend fun getPreferredAppList(): Set<String> {
        val preferredApps: Flow<Set<String>> =
            dataStore.data
                .map { preferences ->
                    preferences[PREFERRED_APPS] ?: emptySet()
                }
        preferredApps.first().let {
            return it
        }
    }

    private suspend fun setPreferredAppList(newPreferences: Set<String>) {
        dataStore.edit { settings ->
            settings[PREFERRED_APPS] = newPreferences
        }
        this.apps.forEach {
            it.isPreferred = newPreferences.contains(it.app.appName)
        }
    }

    companion object {
        val PREFERRED_APPS = stringSetPreferencesKey("preferred_apps")
        val DEFAULT_APPS =
            setOf(
                AppDefinition.BaseAppDefinitions.CURRENCY_II.appDefinition.appName,
                AppDefinition.BaseAppDefinitions.VALUETRANSFER.appDefinition.appName,
                AppDefinition.BaseAppDefinitions.MUSIC_DAO.appDefinition.appName,
                AppDefinition.BaseAppDefinitions.EUROTOKEN.appDefinition.appName,
                AppDefinition.BaseAppDefinitions.FREEDOM_OF_COMPUTING.appDefinition.appName
            )
    }
}
