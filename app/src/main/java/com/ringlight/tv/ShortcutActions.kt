package com.ringlight.tv

import android.content.Context
import android.content.Intent

/** Only these public commands may enter through a button mapper. */
object ShortcutActions {
    data class Command(val action: String, val label: Int, val icon: Int)

    val commands = listOf(
        Command(RingLightService.ACTION_TOGGLE, R.string.shortcut_toggle_long, R.drawable.ic_ring),
        Command(RingLightService.ACTION_CYCLE_COLOR, R.string.shortcut_color_long, R.drawable.ic_color),
        Command(RingLightService.ACTION_CYCLE_INTENSITY, R.string.shortcut_intensity_long, R.drawable.ic_intensity),
        Command(RingLightService.ACTION_TURN_OFF, R.string.shortcut_off_long, R.drawable.ic_ring),
    )

    fun find(action: String?): Command? = commands.firstOrNull { it.action == action }

    fun intent(context: Context, command: Command): Intent =
        Intent(context, TrampolineActivity::class.java)
            .setAction(command.action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
