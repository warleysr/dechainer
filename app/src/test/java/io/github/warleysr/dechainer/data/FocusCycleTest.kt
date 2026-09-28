package io.github.warleysr.dechainer.data

import io.github.warleysr.dechainer.data.FocusMode.Phase
import io.kotest.matchers.shouldBe
import org.junit.Test

class FocusCycleTest {

    private fun next(finished: Phase, completed: Int, cycles: Int = 4, autoStart: Boolean = false) =
        FocusCycle.next(finished, completed, cycles, autoStart)

    @Test
    fun `focus is followed by a short break until the cycle is complete`() {
        next(Phase.FOCUS, completed = 0) shouldBe (Phase.SHORT_BREAK to 1)
        next(Phase.FOCUS, completed = 2) shouldBe (Phase.SHORT_BREAK to 3)
    }

    @Test
    fun `the last focus period of a cycle earns a long break`() {
        next(Phase.FOCUS, completed = 3) shouldBe (Phase.LONG_BREAK to 4)
    }

    @Test
    fun `lowering the cycle count mid-session still leads to a long break`() {
        next(Phase.FOCUS, completed = 5, cycles = 2) shouldBe (Phase.LONG_BREAK to 6)
    }

    @Test
    fun `a break waits for the user unless auto-start is on`() {
        next(Phase.SHORT_BREAK, completed = 1) shouldBe (Phase.WAITING to 1)
        next(Phase.SHORT_BREAK, completed = 1, autoStart = true) shouldBe (Phase.FOCUS to 1)
    }

    @Test
    fun `a long break closes the cycle`() {
        next(Phase.LONG_BREAK, completed = 4) shouldBe (Phase.WAITING to 0)
        next(Phase.LONG_BREAK, completed = 4, autoStart = true) shouldBe (Phase.FOCUS to 0)
    }

    @Test
    fun `a full cycle with auto-start alternates focus and short breaks before the long one`() {
        var phase = Phase.FOCUS
        var completed = 0
        val seen = mutableListOf(phase)
        repeat(8) {
            val (p, c) = next(phase, completed, cycles = 4, autoStart = true)
            phase = p
            completed = c
            seen += phase
        }
        seen shouldBe listOf(
            Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS,
            Phase.SHORT_BREAK, Phase.FOCUS, Phase.LONG_BREAK, Phase.FOCUS
        )
        completed shouldBe 0
    }
}
