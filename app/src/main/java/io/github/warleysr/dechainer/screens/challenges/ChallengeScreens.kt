package io.github.warleysr.dechainer.screens.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import kotlin.random.Random

@Composable
fun MathChallenge(onSuccess: () -> Unit) {
    var problemIndex by remember { mutableIntStateOf(0) }
    var num1 by remember { mutableIntStateOf(Random.nextInt(10, 99)) }
    var num2 by remember { mutableIntStateOf(Random.nextInt(10, 99)) }
    var answer by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val totalProblems = 5

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.math_challenge_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${problemIndex + 1} / $totalProblems", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("$num1 + $num2 =", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = answer,
            onValueChange = { 
                answer = it
                isError = false
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = isError,
            supportingText = {
                if (isError) Text(stringResource(R.string.challenge_error))
            }
        )
        
        Button(
            onClick = {
                val result = num1 + num2
                if (answer.toIntOrNull() == result) {
                    if (problemIndex + 1 >= totalProblems) {
                        onSuccess()
                    } else {
                        problemIndex++
                        num1 = Random.nextInt(10, 99)
                        num2 = Random.nextInt(10, 99)
                        answer = ""
                    }
                } else {
                    isError = true
                    problemIndex = 0
                    num1 = Random.nextInt(10, 99)
                    num2 = Random.nextInt(10, 99)
                    answer = ""
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

private val COMMON_WORDS = listOf(
    "about", "after", "agent", "ahead", "album", "alike", "allow", "along", "among", "anger",
    "angry", "apple", "arena", "arise", "armor", "asked", "avoid", "aware", "baker", "basis",
    "began", "being", "bench", "black", "blame", "blast", "block", "board", "booth", "brain",
    "brave", "break", "brief", "broad", "brown", "built", "buyer", "candy", "catch", "chain",
    "chart", "cheap", "chest", "child", "chose", "claim", "clean", "clerk", "cliff", "clock",
    "cloud", "coast", "court", "craft", "crazy", "crime", "crowd", "crude", "cycle", "dance",
    "death", "delay", "doubt", "draft", "dream", "drill", "drive", "eager", "earth", "elite",
    "enemy", "enter", "equal", "event", "exact", "extra", "false", "fiber", "fifth", "fight",
    "first", "flash", "floor", "focus", "forth", "forum", "frame", "fraud", "front", "fully",
    "giant", "glass", "grace", "grand", "grass", "great", "gross", "guard", "guest", "happy",
    "heart", "horse", "house", "image", "inner", "japan", "judge", "known", "labor", "laser",
    "laugh", "learn", "leave", "level", "limit", "lives", "logic", "lower", "lunch", "major",
    "march", "maybe", "meant", "metal", "minor", "mixed", "money", "moral", "mount", "mouth",
    "music", "nerve", "newly", "north", "novel", "occur", "offer", "order", "ought", "panel",
    "party", "phase", "photo", "pilot", "place", "plane", "plate", "pound", "press", "pride",
    "print", "prize", "proud", "queen", "quick", "quite", "radio", "range", "ratio", "ready",
    "rebel", "relax", "right", "river", "rough", "route", "rural", "scene", "score", "serve",
    "shade", "shall", "share", "sheet", "shell", "shine", "shock", "shore", "shown", "since",
    "skill", "slide", "smart", "smoke", "solve", "sound", "space", "speak", "spend", "split",
    "staff", "stake", "stark", "state", "steel", "stick", "stock", "store", "story", "strip"
)

@Composable
fun WordChallenge(onSuccess: () -> Unit) {
    val selectedWords = remember { List(48) { COMMON_WORDS[Random.nextInt(COMMON_WORDS.size)] } }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.word_challenge_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${currentIndex + 1} / 32", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        Text(selectedWords[currentIndex], style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = currentInput,
            onValueChange = {
                currentInput = it.lowercase()
                isError = false
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            isError = isError,
            supportingText = {
                if (isError) Text(stringResource(R.string.challenge_error))
            }
        )

        Button(
            onClick = {
                if (currentInput.trim() == selectedWords[currentIndex]) {
                    if (currentIndex + 1 >= 32) {
                        onSuccess()
                    } else {
                        currentIndex++
                        currentInput = ""
                    }
                } else {
                    isError = true
                    currentIndex = 0
                    currentInput = ""
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}
