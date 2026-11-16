package com.peng.pdfreader.feature.tools.progress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state of the job that is shown by the progress page.
 *
 * The tool publishes its progress here, the progress page reads it. Neither of
 * them knows the other - that keeps the business logic of the tool out of the
 * page and the page out of the tool.
 */
object ToolProgressBus {

    private val _state = MutableStateFlow(ToolProgressState())
    val state: StateFlow<ToolProgressState> = _state.asStateFlow()

    /** The tool starts its job. */
    fun start(label: String) {
        _state.value = ToolProgressState(running = true, label = label)
    }

    /** The tool reports how far its job is. */
    fun report(percent: Int) {
        val current = _state.value
        if (!current.running) return
        _state.value = current.copy(percent = percent.coerceIn(0, 100))
    }

    /** The job is done, the progress page opens the result page with it. */
    fun finish(result: ToolJobResult) {
        _state.value = _state.value.copy(running = false, result = result, error = null)
    }

    /** The job failed, the progress page closes and the tool shows the error. */
    fun fail(message: String) {
        _state.value = _state.value.copy(running = false, result = null, error = message)
    }

    /** Clears the state, it is called when a tool leaves its flow. */
    fun reset() {
        _state.value = ToolProgressState()
    }
}

/**
 * The state of the progress page.
 *
 * @param running whether the job is still running
 * @param percent how far the job is
 * @param label the text of the job, e.g. "正在合并…"
 * @param result what the job produced
 * @param error the message of a failed job
 */
data class ToolProgressState(
    val running: Boolean = false,
    val percent: Int = 0,
    val label: String? = null,
    val result: ToolJobResult? = null,
    val error: String? = null,
)

/**
 * What a job of a tool produced.
 *
 * The result page shows it - the same page for every tool, only the state of
 * the hero differs.
 *
 * @param documentId the document the tool has produced
 * @param mode the state of the hero, see [com.peng.pdfreader.feature.tools.activity.ResultActivity]
 * @param title the success title of the hero
 * @param sizeBefore the size of the document before the tool ran
 * @param sizeAfter the size of the document after the tool ran
 */
data class ToolJobResult(
    val documentId: String,
    val mode: String,
    val title: String? = null,
    val sizeBefore: Long = 0L,
    val sizeAfter: Long = 0L,
)
