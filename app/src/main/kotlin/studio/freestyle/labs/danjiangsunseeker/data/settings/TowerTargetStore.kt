package studio.freestyle.labs.danjiangsunseeker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import studio.freestyle.labs.danjiangsunseeker.domain.model.TowerTarget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TowerTargetStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ds = context.towerTargetDataStore

    val target: Flow<TowerTarget> = ds.data.map { prefs ->
        prefs[KEY_TARGET]?.let { value ->
            runCatching { TowerTarget.valueOf(value) }.getOrNull()
        } ?: TowerTarget.UpperY
    }

    suspend fun setTarget(target: TowerTarget) {
        ds.edit { prefs -> prefs[KEY_TARGET] = target.name }
    }

    companion object {
        private val KEY_TARGET = stringPreferencesKey("tower_target")
    }
}

private val Context.towerTargetDataStore by preferencesDataStore("tower_target")
