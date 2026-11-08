package cz.anglictina.kurz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.anglictina.kurz.data.Repository

@Composable
fun OnboardingScreen(repo: Repository, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp).padding(top = 64.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Vítej v kurzu angličtiny", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Roční kurz B1 → B1+. Každý týden přibude nová lekce a aplikace ti s ní pomůže každý den: zopakuje slovíčka a gramatiku a k tomu tě naučí kolik slov chceš navíc.")
            Text("Jak ti máme říkat?")
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Jméno") })
            Button(onClick = { repo.settings.userName = name.trim(); onDone() }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Začít")
            }
        }
    }
}
