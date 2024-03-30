package nl.tudelft.trustchain.common.freedomOfComputing

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import java.util.Collections

object InstalledApps {
    private val appNames: MutableSet<String> = HashSet()
    private val preferredApps: MutableSet<String> = HashSet()
    private var dataStore: DataStore<Preferences>? = null
    private var preferredAppsKey: Preferences.Key<Set<String>>? = null
    private var installedappskey: Preferences.Key<Set<String>>? = null

    fun injectDataStore(
        dataStore: DataStore<Preferences>,
        preferredAppsKey: Preferences.Key<Set<String>>,
        installedAppsKey: Preferences.Key<Set<String>>
    ) {
        this.preferredAppsKey = preferredAppsKey
        this.installedappskey = installedAppsKey
        this.dataStore = dataStore
        if (appNames.size > 0) {
            runBlocking {
                install(appNames, preferredApps)
                appNames.clear()
                preferredApps.clear()
                appNames.clear()
            }
        }
    }

    fun addApp(
        appName: String,
        preferred: Boolean = true
    ) {
        if (dataStore == null) {
            appNames.add(appName)
            if (preferred) {
                preferredApps.add(appName)
            }
        } else {
            runBlocking {
                install(appName, preferred)
            }
        }
    }

    private suspend fun install(
        appNames: Collection<String>,
        preferredApps: Collection<String>
    ) {
        Log.i("app-installer", "Installing apps: $appNames, $preferredApps")
        dataStore!!.edit { settings ->
            val set = settings[installedappskey!!].orEmpty().toMutableSet()
            set.addAll(appNames)
            settings[installedappskey!!] = set

            if (!preferredApps.isEmpty()) {
                val oldPreferred = settings[preferredAppsKey!!].orEmpty().toMutableSet()
                oldPreferred.addAll(preferredApps)
                settings[preferredAppsKey!!] = oldPreferred
            }
        }
    }

    private suspend fun install(
        appName: String,
        preferred: Boolean
    ) {
        val collection = Collections.singleton(appName)
        if (preferred) {
            install(collection, collection)
        } else {
            install(collection, Collections.emptySet())
        }
    }
}
