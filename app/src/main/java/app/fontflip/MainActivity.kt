package app.fontflip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.fontflip.ui.CrashScreen
import app.fontflip.ui.FontFlipApp
import app.fontflip.ui.FontFlipTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashFile = File(filesDir, "crash.txt")
        setContent {
            FontFlipTheme {
                var crash by remember { mutableStateOf(if (crashFile.exists()) crashFile.readText() else null) }
                val log = crash
                if (log != null) {
                    CrashScreen(log) {
                        crashFile.delete()
                        crash = null
                    }
                } else {
                    FontFlipApp()
                }
            }
        }
    }
}
