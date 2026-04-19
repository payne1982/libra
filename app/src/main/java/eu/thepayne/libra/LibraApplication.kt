package eu.thepayne.libra

import android.app.Application
import android.content.Context
import eu.thepayne.libra.data.db.AppDatabase
import eu.thepayne.libra.data.repository.MeasurementRepository
import eu.thepayne.libra.data.repository.UserPreferences

class LibraApplication : Application() {
    val userPreferences by lazy { UserPreferences(this) }
    val measurementRepository by lazy { MeasurementRepository(AppDatabase.getInstance(this)) }

    override fun attachBaseContext(base: Context) {
        val lang = base.getSharedPreferences(LANG_PREFS, 0).getString(LANG_KEY, "") ?: ""
        super.attachBaseContext(applyLocale(base, lang))
    }
}
