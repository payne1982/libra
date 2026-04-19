package eu.thepayne.libra

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.thepayne.libra.ui.NavGraph
import eu.thepayne.libra.ui.theme.LibraTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences(LANG_PREFS, 0).getString(LANG_KEY, "") ?: ""
        super.attachBaseContext(applyLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibraTheme {
                NavGraph()
            }
        }
    }
}
