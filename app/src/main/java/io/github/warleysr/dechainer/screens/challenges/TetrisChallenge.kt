package io.github.warleysr.dechainer.screens.challenges

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import kotlin.math.abs
import kotlin.math.max

private const val COLS = 10
private const val ROWS = 20
private const val EMPTY = -1

private const val IDLE_TIMEOUT_MS = 8_000L

private const val MAX_FRAME_DELTA_MS = 100L

private enum class Tetromino(val boxSize: Int, val cells: List<Pair<Int, Int>>, val color: Color) {
    I(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), Color(0xFF00BCD4)),
    O(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), Color(0xFFFFC107)),
    T(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), Color(0xFF9C27B0)),
    S(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), Color(0xFF4CAF50)),
    Z(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), Color(0xFFF44336)),
    J(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), Color(0xFF2196F3)),
    L(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), Color(0xFFFF9800));

    fun rotatedCells(rotation: Int): List<Pair<Int, Int>> {
        var result = cells
        repeat(rotation.mod(4)) {
            result = result.map { (x, y) -> (boxSize - 1 - y) to x }
        }
        return result
    }
}

private data class ActivePiece(val type: Tetromino, val rotation: Int, val x: Int, val y: Int) {
    fun cells(): List<Pair<Int, Int>> = type.rotatedCells(rotation).map { (cx, cy) -> (x + cx) to (y + cy) }
}

private class TetrisGame {
    val board = IntArray(COLS * ROWS) { EMPTY }
    private val bag = mutableListOf<Tetromino>()

    var current: ActivePiece = spawn(nextFromBag())
        private set
    var next: Tetromino = nextFromBag()
        private set
    var lines by mutableIntStateOf(0)
        private set

    var revision by mutableIntStateOf(0)
        private set

    val gravityIntervalMs: Long
        get() = max(120L, 700L - (lines / 10) * 60L)

    private fun nextFromBag(): Tetromino {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled())
        return bag.removeAt(0)
    }

    private fun spawn(type: Tetromino) = ActivePiece(type, 0, (COLS - type.boxSize) / 2, 0)

    private fun fits(piece: ActivePiece): Boolean = piece.cells().all { (x, y) ->
        x in 0 until COLS && y in 0 until ROWS && board[y * COLS + x] == EMPTY
    }

    private fun tryMove(piece: ActivePiece): Boolean {
        if (!fits(piece)) return false
        current = piece
        revision++
        return true
    }

    fun moveLeft() = tryMove(current.copy(x = current.x - 1))

    fun moveRight() = tryMove(current.copy(x = current.x + 1))

    fun rotate() {
        val rotated = current.copy(rotation = (current.rotation + 1) % 4)
        for (kick in listOf(0, -1, 1, -2, 2)) {
            if (tryMove(rotated.copy(x = rotated.x + kick))) return
        }
    }

    fun step() {
        if (!tryMove(current.copy(y = current.y + 1))) lock()
    }

    fun hardDrop() {
        current = ghost()
        lock()
    }

    fun ghost(): ActivePiece {
        var piece = current
        while (fits(piece.copy(y = piece.y + 1))) piece = piece.copy(y = piece.y + 1)
        return piece
    }

    private fun lock() {
        current.cells().forEach { (x, y) -> board[y * COLS + x] = current.type.ordinal }
        clearLines()

        val spawned = spawn(next)
        next = nextFromBag()
        if (fits(spawned)) {
            current = spawned
        } else {
            board.fill(EMPTY)
            current = spawn(next)
            next = nextFromBag()
        }
        revision++
    }

    private fun clearLines() {
        var writeRow = ROWS - 1
        for (row in ROWS - 1 downTo 0) {
            val full = (0 until COLS).all { board[row * COLS + it] != EMPTY }
            if (full) {
                lines++
                continue
            }
            if (writeRow != row) {
                for (col in 0 until COLS) board[writeRow * COLS + col] = board[row * COLS + col]
            }
            writeRow--
        }
        for (row in writeRow downTo 0) {
            for (col in 0 until COLS) board[row * COLS + col] = EMPTY
        }
    }
}

@Composable
fun TetrisChallenge(minutes: Int, onSuccess: () -> Unit) {
    val game = remember { TetrisGame() }
    val requiredMillis = minutes * 60_000L
    var playedMillis by remember { mutableLongStateOf(0L) }
    var lastInputAt by remember { mutableLongStateOf(-IDLE_TIMEOUT_MS) }
    var isPlaying by remember { mutableStateOf(false) }
    val currentOnSuccess by rememberUpdatedState(onSuccess)

    fun input(action: () -> Unit) {
        lastInputAt = SystemClock.uptimeMillis()
        action()
    }

    LaunchedEffect(Unit) {
        var lastFrame = withFrameMillis { it }
        var sinceGravity = 0L
        while (true) {
            val frame = withFrameMillis { it }
            val delta = (frame - lastFrame).coerceIn(0L, MAX_FRAME_DELTA_MS)
            lastFrame = frame

            isPlaying = SystemClock.uptimeMillis() - lastInputAt < IDLE_TIMEOUT_MS
            if (!isPlaying) continue

            playedMillis += delta
            if (playedMillis >= requiredMillis) {
                currentOnSuccess()
                break
            }

            sinceGravity += delta
            if (sinceGravity >= game.gravityIntervalMs) {
                sinceGravity = 0L
                game.step()
            }
        }
    }

    val remainingSeconds = ((requiredMillis - playedMillis).coerceAtLeast(0L) + 999) / 1000
    val boardColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ghostColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.tetris_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            progress = { (playedMillis.toFloat() / requiredMillis).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            if (isPlaying) stringResource(R.string.tetris_lines, game.lines)
            else stringResource(R.string.tetris_paused),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPlaying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(COLS.toFloat() / ROWS, matchHeightConstraintsFirst = true)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { input { game.rotate() } })
                    }
                    .pointerInput(Unit) {
                        var dragX = 0f
                        var dragY = 0f
                        detectDragGestures(
                            onDragStart = {
                                dragX = 0f
                                dragY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val cell = size.width / COLS.toFloat()
                                dragX += amount.x
                                dragY += amount.y
                                while (abs(dragX) >= cell) {
                                    if (dragX > 0) input { game.moveRight() } else input { game.moveLeft() }
                                    dragX -= if (dragX > 0) cell else -cell
                                }
                                while (dragY >= cell) {
                                    input { game.step() }
                                    dragY -= cell
                                }
                            }
                        )
                    }
            ) {
                game.revision
                val cell = size.width / COLS
                drawRoundRect(boardColor, cornerRadius = CornerRadius(8.dp.toPx()))
                for (col in 1 until COLS) {
                    drawLine(gridColor, Offset(col * cell, 0f), Offset(col * cell, size.height), 1f)
                }
                for (row in 1 until ROWS) {
                    drawLine(gridColor, Offset(0f, row * cell), Offset(size.width, row * cell), 1f)
                }
                for (row in 0 until ROWS) {
                    for (col in 0 until COLS) {
                        val value = game.board[row * COLS + col]
                        if (value != EMPTY) drawCell(col, row, cell, Tetromino.entries[value].color)
                    }
                }
                game.ghost().cells().forEach { (x, y) -> drawCell(x, y, cell, ghostColor, outline = true) }
                game.current.cells().forEach { (x, y) -> drawCell(x, y, cell, game.current.type.color) }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.tetris_next), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Canvas(modifier = Modifier.size(56.dp)) {
                    game.revision
                    val next = game.next
                    val cell = size.width / 4
                    val offset = (4 - next.boxSize) / 2f
                    next.cells.forEach { (x, y) ->
                        drawRoundRect(
                            next.color,
                            topLeft = Offset((x + offset) * cell + 1f, (y + offset) * cell + 1f),
                            size = Size(cell - 2f, cell - 2f),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, R.string.tetris_move_left) {
                input { game.moveLeft() }
            }
            ControlButton(Icons.AutoMirrored.Filled.RotateRight, R.string.tetris_rotate) {
                input { game.rotate() }
            }
            ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, R.string.tetris_move_right) {
                input { game.moveRight() }
            }
            ControlButton(Icons.Filled.KeyboardArrowDown, R.string.tetris_soft_drop) {
                input { game.step() }
            }
            ControlButton(Icons.Filled.KeyboardDoubleArrowDown, R.string.tetris_hard_drop) {
                input { game.hardDrop() }
            }
        }
    }
}

private fun DrawScope.drawCell(col: Int, row: Int, cell: Float, color: Color, outline: Boolean = false) {
    val inset = 1.5f
    val topLeft = Offset(col * cell + inset, row * cell + inset)
    val size = Size(cell - inset * 2, cell - inset * 2)
    val radius = CornerRadius(3.dp.toPx())
    if (outline) {
        drawRoundRect(color.copy(alpha = 0.5f), topLeft, size, radius, style = Stroke(2.dp.toPx()))
    } else {
        drawRoundRect(color, topLeft, size, radius)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, description: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(32.dp))
    }
}
