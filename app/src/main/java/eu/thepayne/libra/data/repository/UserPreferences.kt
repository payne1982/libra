package eu.thepayne.libra.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("libra_prefs")

data class AppPrefs(
    val weightMinKg: Float = 40f,
    val weightMaxKg: Float = 120f,
    val useKg: Boolean = true,
    // Profilo utente per il protocollo BLE della bilancia
    val initials: String = "AAA",
    val heightCm: Int = 170,
    val genderMale: Boolean = true,
    val birthYear: Int = 1990,
    val birthMonth: Int = 1,
    val birthDay: Int = 1,
    val activityLevel: Int = 3,
    val scaleUserId: Long = 1234L,
    val languageCode: String = "",
)

class UserPreferences(private val context: Context) {

    private object Keys {
        val WEIGHT_MIN = floatPreferencesKey("weight_min_kg")
        val WEIGHT_MAX = floatPreferencesKey("weight_max_kg")
        val USE_KG = booleanPreferencesKey("use_kg")
        val INITIALS = stringPreferencesKey("initials")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val GENDER_MALE = booleanPreferencesKey("gender_male")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val BIRTH_MONTH = intPreferencesKey("birth_month")
        val BIRTH_DAY = intPreferencesKey("birth_day")
        val ACTIVITY_LEVEL = intPreferencesKey("activity_level")
        val SCALE_USER_ID = longPreferencesKey("scale_user_id")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            weightMinKg = p[Keys.WEIGHT_MIN] ?: 40f,
            weightMaxKg = p[Keys.WEIGHT_MAX] ?: 120f,
            useKg = p[Keys.USE_KG] ?: true,
            initials = p[Keys.INITIALS] ?: "AAA",
            heightCm = p[Keys.HEIGHT_CM] ?: 170,
            genderMale = p[Keys.GENDER_MALE] ?: true,
            birthYear = p[Keys.BIRTH_YEAR] ?: 1990,
            birthMonth = p[Keys.BIRTH_MONTH] ?: 1,
            birthDay = p[Keys.BIRTH_DAY] ?: 1,
            activityLevel = p[Keys.ACTIVITY_LEVEL] ?: 3,
            scaleUserId = p[Keys.SCALE_USER_ID] ?: 1234L,
            languageCode = p[Keys.LANGUAGE_CODE] ?: "",
        )
    }

    suspend fun setWeightRange(minKg: Float, maxKg: Float) {
        context.dataStore.edit {
            it[Keys.WEIGHT_MIN] = minKg
            it[Keys.WEIGHT_MAX] = maxKg
        }
    }

    suspend fun setUseKg(useKg: Boolean) {
        context.dataStore.edit { it[Keys.USE_KG] = useKg }
    }

    suspend fun setScaleUserId(id: Long) {
        context.dataStore.edit { it[Keys.SCALE_USER_ID] = id }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
        context.getSharedPreferences(eu.thepayne.libra.LANG_PREFS, 0)
            .edit().putString(eu.thepayne.libra.LANG_KEY, code).apply()
    }

    suspend fun setUserProfile(
        initials: String,
        heightCm: Int,
        genderMale: Boolean,
        birthYear: Int,
        birthMonth: Int,
        birthDay: Int,
        activityLevel: Int,
    ) {
        context.dataStore.edit {
            it[Keys.INITIALS] = initials.uppercase().take(3).padEnd(3, 'A')
            it[Keys.HEIGHT_CM] = heightCm
            it[Keys.GENDER_MALE] = genderMale
            it[Keys.BIRTH_YEAR] = birthYear
            it[Keys.BIRTH_MONTH] = birthMonth
            it[Keys.BIRTH_DAY] = birthDay
            it[Keys.ACTIVITY_LEVEL] = activityLevel
        }
    }
}
