package cz.anglictina.kurz

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionKind
import cz.anglictina.kurz.ui.AppTheme
import cz.anglictina.kurz.ui.BankScreen
import cz.anglictina.kurz.ui.HomeScreen
import cz.anglictina.kurz.ui.LessonDetailScreen
import cz.anglictina.kurz.ui.LessonsScreen
import cz.anglictina.kurz.ui.OnboardingScreen
import cz.anglictina.kurz.ui.SessionScreen
import cz.anglictina.kurz.ui.SettingsScreen
import cz.anglictina.kurz.ui.StatsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var repo: Repository
    lateinit var speech: Speech

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = Repository(applicationContext).also { it.load() }
        speech = Speech(this)
        Reminder.ensureChannel(this)
        Reminder.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            AppTheme {
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()
                var version by remember { mutableIntStateOf(0) }     // změna = překreslit po synchronizaci / relaci
                var syncMsg by remember { mutableStateOf<String?>(null) }
                var syncing by remember { mutableStateOf(false) }

                fun sync(manual: Boolean) {
                    if (syncing) return
                    syncing = true
                    scope.launch {
                        val r = repo.sync()
                        syncing = false
                        syncMsg = r.fold(
                            onSuccess = { n -> if (n > 0) "Staženo nového obsahu: $n" else if (manual) "Vše je aktuální" else null },
                            onFailure = { if (manual) "Nepodařilo se připojit. Zkuste to později." else null }
                        )
                        version++
                    }
                }
                LaunchedEffect(Unit) { sync(false) }

                val start = if (repo.settings.userName.isBlank()) "onboarding" else "home"
                NavHost(nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(repo) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }
                    }
                    composable("home") {
                        HomeScreen(
                            repo, version, syncing, syncMsg,
                            onSync = { sync(true) },
                            onMsgShown = { syncMsg = null },
                            onStart = { kind, minutes -> nav.navigate("session/${kind.name}/$minutes/0") },
                            onBank = { nav.navigate("bank") },
                            onLessons = { nav.navigate("lessons") },
                            onStats = { nav.navigate("stats") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("session/{kind}/{minutes}/{extra}") { e ->
                        val kind = SessionKind.valueOf(e.arguments?.getString("kind") ?: "LESSONS")
                        val minutes = e.arguments?.getString("minutes")?.toIntOrNull() ?: 15
                        val extra = e.arguments?.getString("extra") == "1"
                        SessionScreen(repo, speech, kind, minutes, extra,
                            onDone = { version++; nav.popBackStack() },
                            onMore = { k, m, x -> version++; nav.navigate("session/${k.name}/$m/${if (x) 1 else 0}") { popUpTo("home") } })
                    }
                    composable("bank") { BankScreen(repo, version, onBack = { version++; nav.popBackStack() },
                        onStart = { m, x -> nav.navigate("session/BANK/$m/${if (x) 1 else 0}") }) }
                    composable("lessons") { LessonsScreen(repo, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("lesson/$it") }) }
                    composable("lesson/{id}") { e -> LessonDetailScreen(repo, speech, e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() }) }
                    composable("stats") { StatsScreen(repo, onBack = { nav.popBackStack() }) }
                    composable("settings") { SettingsScreen(repo, onBack = { Reminder.schedule(this@MainActivity); version++; nav.popBackStack() }, onSync = { sync(true) }) }
                }
            }
        }
    }

    override fun onPause() { super.onPause(); repo.progress.save() }
    override fun onDestroy() { speech.shutdown(); super.onDestroy() }
}
