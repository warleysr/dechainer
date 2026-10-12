package io.github.warleysr.dechainer.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.warleysr.dechainer.screens.challenges.ChallengeScaffold
import io.github.warleysr.dechainer.screens.challenges.MathChallenge
import io.github.warleysr.dechainer.screens.challenges.ReadingChallenge
import io.github.warleysr.dechainer.screens.challenges.TetrisChallenge
import io.github.warleysr.dechainer.screens.challenges.WordChallenge
import io.github.warleysr.dechainer.security.SecurityManager

/** Runs the configured access challenges one after another; [onDone] fires after the last one. */
@Composable
fun ChallengeRunner(challenges: List<SecurityManager.ChallengeType>, onDone: () -> Unit, onGiveUp: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    val next: () -> Unit = { if (index + 1 >= challenges.size) onDone() else index++ }

    ChallengeScaffold(step = index + 1, total = challenges.size, onGiveUp = onGiveUp) {
        key(index) {
            when (challenges[index]) {
                SecurityManager.ChallengeType.MATH -> MathChallenge(onSuccess = next)
                SecurityManager.ChallengeType.WORDS -> WordChallenge(onSuccess = next)
                SecurityManager.ChallengeType.READING -> ReadingChallenge(onSuccess = next)
                SecurityManager.ChallengeType.TETRIS -> TetrisChallenge(
                    minutes = SecurityManager.getTetrisMinutes(context),
                    onSuccess = next
                )
            }
        }
    }
}
