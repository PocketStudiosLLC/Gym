package com.gymapp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT }

enum class ThemeColor(val label: String) {
    BLUE("Blue"),
    TEAL("Teal"),
    GREEN("Green"),
    ORANGE("Orange"),
    RED("Red"),
    PINK("Pink"),
    PURPLE("Purple"),
    AMBER("Amber"),
}

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

/** Personal details for the calorie goal and BMR. Any field may be unset. */
data class Profile(
    val calorieGoal: Int? = null,
    val heightFt: Int? = null,
    val heightIn: Double? = null,
    val weightLb: Double? = null,
    val age: Int? = null,
    val sex: Sex? = null,
) {
    val heightInches: Double? get() = if (heightFt == null && heightIn == null) null else (heightFt ?: 0) * 12 + (heightIn ?: 0.0)

    /**
     * Basal metabolic rate in kcal/day using the Mifflin-St Jeor equation, or null until
     * height, weight, age and sex are all set.
     */
    val bmr: Int?
        get() {
            val inches = heightInches?.takeIf { it > 0 } ?: return null
            val lb = weightLb?.takeIf { it > 0 } ?: return null
            val years = age?.takeIf { it > 0 } ?: return null
            val s = sex ?: return null
            val kg = lb * 0.45359237
            val cm = inches * 2.54
            val base = 10 * kg + 6.25 * cm - 5 * years
            return Math.round(base + if (s == Sex.MALE) 5 else -161).toInt()
        }
}

data class AppSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    val color: ThemeColor = ThemeColor.BLUE,
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val modeKey = stringPreferencesKey("theme_mode")
    private val colorKey = stringPreferencesKey("theme_color")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            mode = p[modeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            color = p[colorKey]?.let { runCatching { ThemeColor.valueOf(it) }.getOrNull() } ?: ThemeColor.BLUE,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private val calorieGoalKey = intPreferencesKey("calorie_goal")
    private val heightFtKey = intPreferencesKey("height_ft")
    private val heightInKey = doublePreferencesKey("height_in")
    private val weightKey = doublePreferencesKey("profile_weight_lb")
    private val ageKey = intPreferencesKey("age")
    private val sexKey = stringPreferencesKey("sex")

    val profile: Flow<Profile> = context.settingsStore.data.map { p ->
        Profile(
            calorieGoal = p[calorieGoalKey],
            heightFt = p[heightFtKey],
            heightIn = p[heightInKey],
            weightLb = p[weightKey],
            age = p[ageKey],
            sex = p[sexKey]?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun currentProfile(): Profile = profile.first()

    private suspend fun <T> put(key: Preferences.Key<T>, value: T?) = context.settingsStore.edit {
        if (value == null) it.remove(key) else it[key] = value
    }

    suspend fun setCalorieGoal(v: Int?) = put(calorieGoalKey, v)
    suspend fun setHeight(ft: Int?, inches: Double?) {
        put(heightFtKey, ft)
        put(heightInKey, inches)
    }
    suspend fun setProfileWeight(v: Double?) = put(weightKey, v)
    suspend fun setAge(v: Int?) = put(ageKey, v)
    suspend fun setSex(v: Sex?) = put(sexKey, v?.name)
    suspend fun setProfile(p: Profile) {
        setCalorieGoal(p.calorieGoal)
        setHeight(p.heightFt, p.heightIn)
        setProfileWeight(p.weightLb)
        setAge(p.age)
        setSex(p.sex)
    }

    suspend fun setMode(mode: ThemeMode) = context.settingsStore.edit { it[modeKey] = mode.name }
    suspend fun setColor(color: ThemeColor) = context.settingsStore.edit { it[colorKey] = color.name }
}
