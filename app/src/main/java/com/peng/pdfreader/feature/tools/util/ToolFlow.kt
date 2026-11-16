package com.peng.pdfreader.feature.tools.util

import android.app.Activity
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.ui.activities.ResultActivity
import com.peng.pdfreader.ui.activities.ToolProgressActivity
import com.peng.pdfreader.feature.tools.progress.ToolJobResult
import com.peng.pdfreader.feature.tools.progress.ToolProgressBus

/**
 * The shared flow of the tools.
 *
 * Every tool of the app follows the same way: it opens the progress page,
 * builds its document and hands it over to the result page. The tools only
 * bring their own job - the pages around them are the same for all of them.
 */
object ToolFlow {

    private const val TAG = "ToolFlow"

    /**
     * Runs the job of a tool behind the progress page.
     *
     * The progress page shows the progress of the job and opens the result
     * page as soon as the job is done.
     *
     * @param activity the tool that runs the job
     * @param label the text of the job, e.g. "正在合并…"
     * @param block the job itself, it reports its progress
     */
    suspend fun run(
        activity: Activity,
        label: String,
        block: suspend (report: (Int) -> Unit) -> ToolJobResult,
    ) {
        ToolProgressBus.start(label)
        ToolProgressActivity.start(activity)
        try {
            val result = block { percent -> ToolProgressBus.report(percent) }
            ToolProgressBus.finish(result)
        } catch (error: Exception) {
            Logger.e(TAG, "the job of the tool failed", error)
            ToolProgressBus.fail(error.message.orEmpty())
        }
    }

    /**
     * The result of a tool that only knows the document it has produced.
     *
     * @param documentId the document the tool has produced
     * @param title the success title of the hero of the result page
     */
    fun success(documentId: String, title: String): ToolJobResult = ToolJobResult(
        documentId = documentId,
        mode = ResultActivity.MODE_SUCCESS,
        title = title,
    )

    /**
     * The result of a tool that compares the size of the document before and
     * after it ran.
     */
    fun sizes(documentId: String, before: Long, after: Long): ToolJobResult = ToolJobResult(
        documentId = documentId,
        mode = ResultActivity.MODE_SIZES,
        sizeBefore = before,
        sizeAfter = after,
    )
}
