package com.ringlight.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Supports the legacy shortcut picker used by button mappers, independent of the TV launcher. */
class ShortcutActivity : AppCompatActivity() {
    companion object {
        const val ACTION_TEST_SHORTCUT = "com.ringlight.tv.TEST_SHORTCUT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val testMode = intent.action == ACTION_TEST_SHORTCUT
        if (!testMode && intent.action != Intent.ACTION_CREATE_SHORTCUT) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (testMode) R.string.test_actions else R.string.shortcut_picker_title)
            .setItems(ShortcutActions.commands.map { getString(it.label) }.toTypedArray()) { _, index ->
                val command = ShortcutActions.commands[index]
                val target = ShortcutActions.intent(this, command)
                if (testMode) {
                    startActivity(target)
                } else {
                    @Suppress("DEPRECATION")
                    val result = Intent()
                        .putExtra(Intent.EXTRA_SHORTCUT_INTENT, target)
                        .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(command.label))
                        .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource.fromContext(this, command.icon))
                    setResult(Activity.RESULT_OK, result)
                }
                finish()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
