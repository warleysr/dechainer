package io.github.warleysr.dechainer.tiles

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.DeviceOwnerRepository
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.utils.LocaleUtils
import java.util.Locale
import kotlin.math.ceil

/** Quick Settings tile to trigger the configured Impulse Lock (panic button) in one tap. */
class ImpulseLockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!SecurityManager.isRecoveryCodeSet(this) || !DeviceOwnerRepository.isDeviceOwner()) {
            updateTile()
            return
        }
        // Active impulse blocks cannot be cancelled early; just refresh the remaining time.
        if (SecurityManager.getImpulseBlockRemainingTime(this) > 0L) {
            updateTile()
            return
        }
        runUnlocked {
            SecurityManager.startImpulseBlock(this)
            updateTile()
        }
    }

    internal fun updateTile() {
        val tile = qsTile ?: return
        val ctx = localizedContext(this)
        tile.label = ctx.getString(R.string.impulse_lock)

        if (!SecurityManager.isRecoveryCodeSet(this) || !DeviceOwnerRepository.isDeviceOwner()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = null
            tile.updateTile()
            return
        }

        val remaining = SecurityManager.getImpulseBlockRemainingTime(this)
        if (remaining > 0L) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = formatRemainingMillis(ctx, remaining)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = formatMinutes(ctx, SecurityManager.getImpulseDurationMinutes(this))
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestTileUpdate(context, ImpulseLockTileService::class.java)
        }
    }
}

/** Quick Settings tile to start, pause (keeping apps suspended), resume, or skip breaks in Focus Mode. */
class FocusModeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        FocusMode.syncIfDue(this)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!SecurityManager.isRecoveryCodeSet(this)) {
            updateTile()
            return
        }
        runUnlocked {
            FocusMode.syncIfDue(this)
            val status = FocusMode.getStatus(this)
            when {
                status == null || status.phase == FocusMode.Phase.WAITING -> FocusMode.startFocus(this)
                status.paused -> FocusMode.resume(this)
                status.phase == FocusMode.Phase.SHORT_BREAK ||
                    status.phase == FocusMode.Phase.LONG_BREAK -> FocusMode.skipBreak(this)
                // Plain pause keeps distracting apps suspended, so it doesn't require the recovery code.
                status.phase == FocusMode.Phase.FOCUS -> FocusMode.pause(this, releaseApps = false)
            }
            updateTile()
        }
    }

    internal fun updateTile() {
        val tile = qsTile ?: return
        val ctx = localizedContext(this)
        tile.label = ctx.getString(R.string.focus_mode)

        if (!SecurityManager.isRecoveryCodeSet(this)) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = null
            tile.updateTile()
            return
        }

        val status = FocusMode.getStatus(this)
        when {
            status == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = formatMinutes(ctx, FocusMode.getFocusMinutes(this))
            }
            status.phase == FocusMode.Phase.WAITING -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = ctx.getString(R.string.focus_phase_waiting)
            }
            status.paused -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = ctx.getString(R.string.tile_focus_paused)
            }
            status.phase == FocusMode.Phase.FOCUS -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "${ctx.getString(R.string.tile_focus_phase_focus)} · ${formatRemainingMillis(ctx, status.remainingMillis)}"
            }
            else -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "${ctx.getString(R.string.tile_focus_phase_break)} · ${formatRemainingMillis(ctx, status.remainingMillis)}"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestTileUpdate(context, FocusModeTileService::class.java)
        }
    }
}

private fun TileService.runUnlocked(action: () -> Unit) {
    if (isLocked) unlockAndRun(action) else action()
}

private fun requestTileUpdate(context: Context, serviceClass: Class<out TileService>) {
    runCatching {
        TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context.applicationContext, serviceClass)
        )
    }
}

private fun localizedContext(context: Context): Context {
    val locale = Locale.forLanguageTag(LocaleUtils.getLocale(context))
    val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
    return context.createConfigurationContext(config)
}

private fun formatRemainingMillis(context: Context, millis: Long): String {
    val totalMinutes = ceil(millis / 60_000.0).toInt().coerceAtLeast(1)
    return formatMinutes(context, totalMinutes)
}

private fun formatMinutes(context: Context, totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        context.getString(R.string.time_hours_minutes, hours, minutes)
    } else {
        context.getString(R.string.time_minutes, minutes)
    }
}
