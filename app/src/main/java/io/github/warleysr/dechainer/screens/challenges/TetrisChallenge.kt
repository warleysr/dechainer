package io.github.warleysr.dechainer.screens.challenges

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.security.SecurityManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

private const val COLS = 8
private const val ROWS = 20
private const val EMPTY = -1

private const val IDLE_TIMEOUT_MS = 8_000L

private const val MAX_FRAME_DELTA_MS = 100L

private const val LINES_PER_LEVEL = 10
private const val LOCK_DELAY_MS = 500L
private const val MAX_LOCK_RESETS = 15

private const val CLEAR_ANIMATION_MS = 320L
private const val TOP_OUT_ANIMATION_MS = 520L
private const val LOCK_FLASH_MS = 180L
private const val DROP_TRAIL_MS = 220L
private const val LEVEL_BANNER_MS = 1_300L

private const val REPEAT_DELAY_MS = 170L
private const val REPEAT_INTERVAL_MS = 50L
private const val SOFT_DROP_INTERVAL_MS = 35L

private val GREEN = Color(0xFF4CAF50)
private val YELLOW = Color(0xFFFFC107)
private val BLUE = Color(0xFF2196F3)
private val RED = Color(0xFFF44336)

private enum class Tetromino(val boxSize: Int, val cells: List<Pair<Int, Int>>, val color: Color) {
    I(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), BLUE),
    O(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), YELLOW),
    T(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), RED),
    S(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), GREEN),
    Z(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), RED),
    J(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), BLUE),
    L(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), YELLOW);

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

private sealed interface GameEvent {
    data object Move : GameEvent
    data object Rotate : GameEvent
    data object Lock : GameEvent
    data object HardDrop : GameEvent
    data class LinesCleared(val count: Int) : GameEvent
    data class LevelUp(val level: Int) : GameEvent
    data object TopOut : GameEvent
}

private data class DropTrail(val x: Int, val fromY: Int, val toY: Int, val color: Color)

private class TetrisGame(private val onEvent: (GameEvent) -> Unit) {
    val board = IntArray(COLS * ROWS) { EMPTY }
    private val bag = mutableListOf<Tetromino>()

    var current: ActivePiece? = null
        private set
    var next: Tetromino = nextFromBag()
        private set
    var lines by mutableIntStateOf(0)
        private set
    val level: Int
        get() = lines / LINES_PER_LEVEL + 1

    var clock by mutableLongStateOf(0L)
        private set
    var revision by mutableIntStateOf(0)
        private set

    var clearingRows: List<Int> = emptyList()
        private set
    var clearStartedAt = 0L
        private set
    var topOutStartedAt: Long? = null
        private set
    var lockFlashCells: List<Pair<Int, Int>> = emptyList()
        private set
    var lockFlashAt = Long.MIN_VALUE / 2
        private set
    var dropTrails: List<DropTrail> = emptyList()
        private set
    var dropTrailAt = Long.MIN_VALUE / 2
        private set
    var levelUpAt = Long.MIN_VALUE / 2
        private set

    private var sinceGravity = 0L
    private var lockElapsed = 0L
    private var lockResets = 0

    private val gravityIntervalMs: Long
        get() = max(200L, 1_000L - (level - 1) * 50L)

    init {
        spawnNext()
    }

    private fun nextFromBag(): Tetromino {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled())
        return bag.removeAt(0)
    }

    private fun fits(piece: ActivePiece): Boolean = piece.cells().all { (x, y) ->
        x in 0 until COLS && y in 0 until ROWS && board[y * COLS + x] == EMPTY
    }

    private fun tryMove(piece: ActivePiece): Boolean {
        if (!fits(piece)) return false
        current = piece
        if (lockElapsed > 0 && lockResets < MAX_LOCK_RESETS) {
            lockElapsed = 0
            lockResets++
        }
        revision++
        return true
    }

    fun update(delta: Long) {
        clock += delta
        if (clearingRows.isNotEmpty()) {
            if (clock - clearStartedAt >= CLEAR_ANIMATION_MS) finishClear()
            return
        }
        topOutStartedAt?.let { startedAt ->
            if (clock - startedAt >= TOP_OUT_ANIMATION_MS) finishTopOut()
            return
        }
        val piece = current ?: return
        val below = piece.copy(y = piece.y + 1)
        if (fits(below)) {
            lockElapsed = 0
            sinceGravity += delta
            if (sinceGravity >= gravityIntervalMs) {
                sinceGravity = 0
                tryMove(below)
            }
        } else {
            lockElapsed += delta
            if (lockElapsed >= LOCK_DELAY_MS) lock(hardDrop = false)
        }
    }

    fun moveLeft() = shift(-1)

    fun moveRight() = shift(1)

    private fun shift(dx: Int) {
        val piece = current ?: return
        if (tryMove(piece.copy(x = piece.x + dx))) onEvent(GameEvent.Move)
    }

    fun rotate() {
        val piece = current ?: return
        val rotated = piece.copy(rotation = (piece.rotation + 1) % 4)
        for (kick in listOf(0, -1, 1, -2, 2)) {
            if (tryMove(rotated.copy(x = rotated.x + kick))) {
                onEvent(GameEvent.Rotate)
                return
            }
        }
    }

    fun softDrop() {
        val piece = current ?: return
        if (tryMove(piece.copy(y = piece.y + 1))) sinceGravity = 0
    }

    fun hardDrop() {
        val piece = current ?: return
        val landing = ghost() ?: return
        dropTrails = piece.cells().groupBy({ it.first }, { it.second }).map { (x, ys) ->
            DropTrail(x, ys.min(), landing.cells().filter { it.first == x }.minOf { it.second }, piece.type.color)
        }.filter { it.toY > it.fromY }
        dropTrailAt = clock
        current = landing
        lock(hardDrop = true)
    }

    fun ghost(): ActivePiece? {
        var piece = current ?: return null
        while (fits(piece.copy(y = piece.y + 1))) piece = piece.copy(y = piece.y + 1)
        return piece
    }

    private fun lock(hardDrop: Boolean) {
        val piece = current ?: return
        piece.cells().forEach { (x, y) -> board[y * COLS + x] = piece.type.ordinal }
        lockFlashCells = piece.cells()
        lockFlashAt = clock
        current = null

        val full = (0 until ROWS).filter { row -> (0 until COLS).all { board[row * COLS + it] != EMPTY } }
        if (full.isNotEmpty()) {
            clearingRows = full
            clearStartedAt = clock
            onEvent(GameEvent.LinesCleared(full.size))
        } else {
            onEvent(if (hardDrop) GameEvent.HardDrop else GameEvent.Lock)
            spawnNext()
        }
        revision++
    }

    private fun finishClear() {
        val levelBefore = level
        var writeRow = ROWS - 1
        for (row in ROWS - 1 downTo 0) {
            if (row in clearingRows) continue
            if (writeRow != row) {
                for (col in 0 until COLS) board[writeRow * COLS + col] = board[row * COLS + col]
            }
            writeRow--
        }
        for (row in writeRow downTo 0) {
            for (col in 0 until COLS) board[row * COLS + col] = EMPTY
        }
        lines += clearingRows.size
        clearingRows = emptyList()
        if (level > levelBefore) {
            levelUpAt = clock
            onEvent(GameEvent.LevelUp(level))
        }
        spawnNext()
    }

    private fun finishTopOut() {
        board.fill(EMPTY)
        topOutStartedAt = null
        spawnNext()
    }

    private fun spawnNext() {
        val type = next
        next = nextFromBag()
        val spawned = ActivePiece(type, 0, (COLS - type.boxSize) / 2, 0)
        sinceGravity = 0
        lockElapsed = 0
        lockResets = 0
        if (fits(spawned)) {
            current = spawned
        } else {
            topOutStartedAt = clock
            onEvent(GameEvent.TopOut)
        }
        revision++
    }
}

@Composable
fun TetrisChallenge(minutes: Int, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sounds = remember { TetrisSounds(context.applicationContext) }
    var soundOn by remember { mutableStateOf(SecurityManager.isTetrisSoundEnabled(context)) }
    val currentSoundOn by rememberUpdatedState(soundOn)

    DisposableEffect(sounds) {
        onDispose { sounds.release() }
    }
    LaunchedEffect(sounds) {
        withContext(Dispatchers.IO) { sounds.load() }
    }

    val game = remember {
        TetrisGame { event ->
            val (sound, haptic) = when (event) {
                GameEvent.Move -> TetrisSound.MOVE to null
                GameEvent.Rotate -> TetrisSound.ROTATE to null
                GameEvent.Lock -> TetrisSound.LOCK to HapticFeedbackType.SegmentTick
                GameEvent.HardDrop -> TetrisSound.HARD_DROP to HapticFeedbackType.VirtualKey
                is GameEvent.LinesCleared ->
                    if (event.count >= 3) TetrisSound.CLEAR_BIG to HapticFeedbackType.LongPress
                    else TetrisSound.CLEAR to HapticFeedbackType.Confirm
                is GameEvent.LevelUp -> TetrisSound.LEVEL_UP to HapticFeedbackType.Confirm
                GameEvent.TopOut -> TetrisSound.TOP_OUT to HapticFeedbackType.Reject
            }
            if (currentSoundOn) sounds.play(sound)
            haptic?.let { haptics.performHapticFeedback(it) }
        }
    }
    val requiredMillis = minutes * 60_000L
    var playedMillis by remember { mutableLongStateOf(0L) }
    var lastInputAt by remember { mutableLongStateOf(-IDLE_TIMEOUT_MS) }
    var isPlaying by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val currentOnSuccess by rememberUpdatedState(onSuccess)

    fun input(action: () -> Unit) {
        lastInputAt = SystemClock.uptimeMillis()
        action()
    }

    LaunchedEffect(Unit) {
        var lastFrame = withFrameMillis { it }
        while (true) {
            val frame = withFrameMillis { it }
            val delta = (frame - lastFrame).coerceIn(0L, MAX_FRAME_DELTA_MS)
            lastFrame = frame

            isPlaying = SystemClock.uptimeMillis() - lastInputAt < IDLE_TIMEOUT_MS
            if (!isPlaying) continue

            if (!finished) {
                playedMillis += delta
                if (playedMillis >= requiredMillis) {
                    finished = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            }
            game.update(delta)
        }
    }

    val remainingSeconds = ((requiredMillis - playedMillis).coerceAtLeast(0L) + 999) / 1000
    val colors = BoardColors(
        board = MaterialTheme.colorScheme.surfaceVariant,
        grid = MaterialTheme.colorScheme.outlineVariant,
        ghost = MaterialTheme.colorScheme.onSurfaceVariant,
        flash = Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxHeight()
                    .aspectRatio(COLS.toFloat() / ROWS, matchHeightConstraintsFirst = true)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
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
                                        input { game.softDrop() }
                                        dragY -= cell
                                    }
                                }
                            )
                        }
                ) {
                    game.revision
                    drawBoard(game, colors)
                }
                LevelBanner(game)
                if (!isPlaying) PausedOverlay()
            }

            Column(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (finished) {
                    Button(
                        onClick = { currentOnSuccess() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.tetris_continue), maxLines = 1)
                    }
                } else {
                    Text(
                        "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { (playedMillis.toFloat() / requiredMillis).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.tetris_lines, game.lines), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.tetris_level, game.level), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        soundOn = !soundOn
                        SecurityManager.setTetrisSoundEnabled(context, soundOn)
                    }
                ) {
                    Icon(
                        if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = stringResource(if (soundOn) R.string.tetris_sound_on else R.string.tetris_sound_off)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val buttonModifier = Modifier.weight(1f).height(56.dp)
            ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, R.string.tetris_move_left, buttonModifier, REPEAT_INTERVAL_MS) {
                input { game.moveLeft() }
            }
            ControlButton(Icons.AutoMirrored.Filled.RotateRight, R.string.tetris_rotate, buttonModifier) {
                input { game.rotate() }
            }
            ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, R.string.tetris_move_right, buttonModifier, REPEAT_INTERVAL_MS) {
                input { game.moveRight() }
            }
            ControlButton(Icons.Filled.KeyboardArrowDown, R.string.tetris_soft_drop, buttonModifier, SOFT_DROP_INTERVAL_MS) {
                input { game.softDrop() }
            }
            ControlButton(Icons.Filled.KeyboardDoubleArrowDown, R.string.tetris_hard_drop, buttonModifier) {
                input { game.hardDrop() }
            }
        }
    }
}

private data class BoardColors(
    val board: Color,
    val grid: Color,
    val ghost: Color,
    val flash: Color
)

private fun DrawScope.drawBoard(game: TetrisGame, colors: BoardColors) {
    val clock = game.clock
    val cell = size.width / COLS
    drawRoundRect(colors.board, cornerRadius = CornerRadius(8.dp.toPx()))
    for (col in 1 until COLS) {
        drawLine(colors.grid, Offset(col * cell, 0f), Offset(col * cell, size.height), 1f)
    }
    for (row in 1 until ROWS) {
        drawLine(colors.grid, Offset(0f, row * cell), Offset(size.width, row * cell), 1f)
    }

    val topOutProgress = game.topOutStartedAt?.let { ((clock - it).toFloat() / TOP_OUT_ANIMATION_MS).coerceIn(0f, 1f) }
    val clearProgress = ((clock - game.clearStartedAt).toFloat() / CLEAR_ANIMATION_MS).coerceIn(0f, 1f)

    for (row in 0 until ROWS) {
        val clearing = row in game.clearingRows
        for (col in 0 until COLS) {
            val value = game.board[row * COLS + col]
            if (value == EMPTY) continue
            val color = Tetromino.entries[value].color
            when {
                topOutProgress != null -> {
                    val rowDelay = (ROWS - 1 - row) / ROWS.toFloat() * 0.5f
                    val local = ((topOutProgress - rowDelay) / 0.5f).coerceIn(0f, 1f)
                    drawCell(col, row, cell, color.copy(alpha = 1f - local), scale = 1f - local * 0.6f)
                }
                clearing && clearProgress < 0.4f -> {
                    drawCell(col, row, cell, color)
                    val pulse = if ((clearProgress / 0.1f).toInt() % 2 == 0) 0.85f else 0.35f
                    drawCell(col, row, cell, colors.flash.copy(alpha = pulse))
                }
                clearing -> {
                    val shrink = (clearProgress - 0.4f) / 0.6f
                    drawCell(col, row, cell, colors.flash.copy(alpha = 1f - shrink), scale = 1f - shrink)
                }
                else -> drawCell(col, row, cell, color)
            }
        }
    }

    val trailProgress = (clock - game.dropTrailAt).toFloat() / DROP_TRAIL_MS
    if (trailProgress in 0f..1f) {
        game.dropTrails.forEach { trail ->
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, trail.color.copy(alpha = 0.45f * (1f - trailProgress))),
                    startY = trail.fromY * cell,
                    endY = trail.toY * cell
                ),
                topLeft = Offset(trail.x * cell + cell * 0.15f, trail.fromY * cell),
                size = Size(cell * 0.7f, (trail.toY - trail.fromY) * cell)
            )
        }
    }

    val flashProgress = (clock - game.lockFlashAt).toFloat() / LOCK_FLASH_MS
    if (flashProgress in 0f..1f && game.clearingRows.isEmpty()) {
        game.lockFlashCells.forEach { (x, y) ->
            drawCell(x, y, cell, colors.flash.copy(alpha = 0.55f * (1f - flashProgress)))
        }
    }

    game.ghost()?.cells()?.forEach { (x, y) -> drawCell(x, y, cell, colors.ghost, outline = true) }
    game.current?.let { piece -> piece.cells().forEach { (x, y) -> drawCell(x, y, cell, piece.type.color) } }
}

@Composable
private fun PausedOverlay() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.tetris_paused),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LevelBanner(game: TetrisGame) {
    val progress = (game.clock - game.levelUpAt).toFloat() / LEVEL_BANNER_MS
    if (progress !in 0f..1f) return
    val pop = (progress / 0.2f).coerceAtMost(1f)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.graphicsLayer {
            val scale = 1.4f - 0.4f * pop
            scaleX = scale
            scaleY = scale
            alpha = if (progress < 0.75f) pop else (1f - progress) / 0.25f
        }
    ) {
        Text(
            stringResource(R.string.tetris_level, game.level),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

private fun DrawScope.drawCell(
    col: Int,
    row: Int,
    cell: Float,
    color: Color,
    outline: Boolean = false,
    scale: Float = 1f
) {
    val inset = 1.5f + (cell / 2f - 1.5f) * (1f - scale)
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
private fun ControlButton(
    icon: ImageVector,
    description: Int,
    modifier: Modifier,
    repeatIntervalMs: Long? = null,
    onPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnPress by rememberUpdatedState(onPress)
    var handledByPress by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource, repeatIntervalMs) {
        var job: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    handledByPress = true
                    job?.cancel()
                    job = launch(start = CoroutineStart.UNDISPATCHED) {
                        currentOnPress()
                        if (repeatIntervalMs == null) return@launch
                        delay(REPEAT_DELAY_MS)
                        while (isActive) {
                            currentOnPress()
                            delay(repeatIntervalMs)
                        }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> job?.cancel()
            }
        }
    }

    FilledIconButton(
        onClick = { if (handledByPress) handledByPress = false else currentOnPress() },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(32.dp))
    }
}
