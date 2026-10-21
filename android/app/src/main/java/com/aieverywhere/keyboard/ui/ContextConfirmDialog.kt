package com.aieverywhere.keyboard.ui

import android.content.Context
import android.app.AlertDialog

object ContextConfirmDialog {
    fun showConsent(context: Context, onConfirmed: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Allow Context Analysis?")
            .setMessage("AI anyWhere will analyze your selected text snippet strictly to generate summaries or smart replies. Your text is never stored on our servers.")
            .setPositiveButton("Allow") { dialog, _ ->
                dialog.dismiss()
                onConfirmed()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
