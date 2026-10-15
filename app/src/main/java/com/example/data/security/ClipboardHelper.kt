package com.example.data.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipboardHelper(private val context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main)
    private var clearJob: Job? = null

    /**
     * Copies sensitive text (username, password, etc.) to clipboard with the sensitive flag,
     * and schedules an automatic clear after [clearDelayMillis] (default 30 seconds).
     */
    fun copySensitiveText(label: String, text: String, clearDelayMillis: Long = 30_000L) {
        val clip = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)

        // Cancel previous clear job if any
        clearJob?.cancel()

        // Schedule auto-clear after 30 seconds
        clearJob = scope.launch {
            delay(clearDelayMillis)
            try {
                if (clipboard.hasPrimaryClip()) {
                    val currentClip = clipboard.primaryClip
                    if (currentClip != null && currentClip.itemCount > 0) {
                        val currentText = currentClip.getItemAt(0).text?.toString()
                        if (currentText == text) {
                            // Clear clipboard safely
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                clipboard.clearPrimaryClip()
                            } else {
                                val emptyClip = ClipData.newPlainText("", "")
                                clipboard.setPrimaryClip(emptyClip)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore clipboard access errors on background
            }
        }
    }
}
