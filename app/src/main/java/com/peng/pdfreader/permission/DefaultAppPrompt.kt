package com.peng.pdfreader.permission

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocType

/**
 * The prompt that asks the user to make the app the default reader.
 *
 * The prompt only appears when the app is not the default handler yet and the
 * user has not pushed it away too often: it shows up after the first
 * effective read, then it waits two days or two more effective reads, and it
 * stays away for good after the third refusal.
 */
object DefaultAppPrompt {

    /** The tag of the sheet of the prompt. */
    const val TAG = "default_app_prompt"

    /**
     * Records an effective read and shows the prompt when it is due.
     *
     * @param context the context of the app
     * @param manager the manager that carries the sheet of the prompt
     * @param type the type of the document the user was reading
     */
    fun onEffectiveRead(context: Context, manager: FragmentManager, type: DocType) {
        val prefs = AppPrefs.get(context)
        // The app is the default handler already - the prompt never shows.
        if (DefaultReader.isDefaultHandler(context)) return
        // The user pushed the prompt away too often, it stays away.
        if (prefs.defaultPromptRefusals >= MAX_REFUSALS) return

        prefs.effectiveReadCount = prefs.effectiveReadCount + 1
        if (!isDue(prefs)) return
        show(context, manager, type)
    }

    /**
     * Whether the prompt is due.
     *
     * The first effective read shows it. After that it waits two days or two
     * more effective reads, whichever comes first.
     */
    private fun isDue(prefs: AppPrefs): Boolean = isDue(
        effectiveReads = prefs.effectiveReadCount,
        readsAtLastPrompt = prefs.defaultPromptReadCount,
        shownAt = prefs.defaultPromptShownAt,
        now = System.currentTimeMillis(),
    )

    /**
     * Whether the prompt is due, with the counters of the app.
     *
     * @param effectiveReads the effective reads of the user so far
     * @param readsAtLastPrompt the effective reads when the prompt was shown
     * @param shownAt when the prompt was shown the last time, zero when it was
     * never shown
     * @param now the current time
     */
    internal fun isDue(
        effectiveReads: Int,
        readsAtLastPrompt: Int,
        shownAt: Long,
        now: Long,
    ): Boolean {
        if (shownAt == 0L) return true
        val cooledDown = now - shownAt >= COOLDOWN_MS
        val reads = effectiveReads - readsAtLastPrompt
        return cooledDown || reads >= READS_BEFORE_NEXT
    }

    /** Shows the prompt of the default reader. */
    private fun show(context: Context, manager: FragmentManager, type: DocType) {
        val prefs = AppPrefs.get(context)
        prefs.defaultPromptShownAt = System.currentTimeMillis()
        prefs.defaultPromptReadCount = prefs.effectiveReadCount
        val sheet = SetDefaultReaderSheet()
        sheet.documentLabel = context.getString(type.labelRes)
        sheet.onLater = {
            prefs.defaultPromptRefusals = prefs.defaultPromptRefusals + 1
        }
        sheet.show(manager, TAG)
    }

    /** How often the user may push the prompt away. */
    private const val MAX_REFUSALS = 3

    /** The time the prompt waits after a refusal. */
    private const val COOLDOWN_MS = 2L * 24L * 60L * 60L * 1000L

    /** The effective reads the prompt waits for after a refusal. */
    private const val READS_BEFORE_NEXT = 2
}
