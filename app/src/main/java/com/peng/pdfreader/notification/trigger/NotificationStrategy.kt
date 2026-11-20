package com.peng.pdfreader.notification.trigger

import android.content.Context
import com.peng.pdfreader.core.util.Logger
import org.json.JSONObject
import java.io.IOException

/**
 * The strategy of the notifications.
 *
 * The file `assets/notification_strategy.json` holds the rules: the order of
 * the functions whose notifications are pushed, the distance between two of
 * them and how many times the loop runs. The owner changes that file to tune
 * the strategy - the app reads it every time a scene triggers.
 *
 * ```
 * {
 *   "order": ["MERGE_PDF", "SPLIT_PDF", "LOCK", "UNLOCK",
 *             "COMPRESS", "PRINT", "SCAN", "IMAGE_TO_PDF"],
 *   "stepIntervalMs": 1000,
 *   "repeatCount": 20
 * }
 * ```
 */
data class NotificationStrategy(
    /** The functions whose notifications are pushed, in that order. */
    val order: List<NotificationTarget> = DEFAULT_ORDER,
    /** The distance between two notifications, in milliseconds. */
    val stepIntervalMs: Long = DEFAULT_STEP_INTERVAL_MS,
    /** How many times the loop runs. */
    val repeatCount: Int = DEFAULT_REPEAT_COUNT,
) {

    companion object {
        private const val TAG = "NotificationStrategy"

        /** The file of the strategy. */
        const val FILE_NAME = "notification_strategy.json"

        /** The order of the functions of the design. */
        val DEFAULT_ORDER: List<NotificationTarget> = listOf(
            NotificationTarget.MERGE_PDF,
            NotificationTarget.SPLIT_PDF,
            NotificationTarget.LOCK,
            NotificationTarget.UNLOCK,
            NotificationTarget.COMPRESS,
            NotificationTarget.PRINT,
            NotificationTarget.SCAN,
            NotificationTarget.IMAGE_TO_PDF,
        )

        /** The distance between two notifications, in milliseconds. */
        const val DEFAULT_STEP_INTERVAL_MS = 1000L

        /** How many times the loop runs. */
        const val DEFAULT_REPEAT_COUNT = 20

        /**
         * Reads the strategy of the app.
         *
         * A missing or broken file does not stop the app, the defaults are used
         * then.
         */
        fun load(context: Context): NotificationStrategy {
            val text = runCatching {
                context.applicationContext.assets.open(FILE_NAME).use { stream ->
                    stream.bufferedReader().readText()
                }
            }.getOrElse {
                Logger.w(TAG, "the strategy file could not be read: ${it.message}")
                return NotificationStrategy()
            }
            return parse(text)
        }

        /** Reads the strategy out of the text of the file. */
        fun parse(text: String): NotificationStrategy {
            val json = runCatching { JSONObject(text) }.getOrElse {
                Logger.w(TAG, "the strategy file is broken: ${it.message}")
                return NotificationStrategy()
            }
            val order = json.optJSONArray("order")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    NotificationTarget.of(array.optString(index))
                }
            }.orEmpty().ifEmpty { DEFAULT_ORDER }
            return NotificationStrategy(
                order = order,
                stepIntervalMs = json.optLong("stepIntervalMs", DEFAULT_STEP_INTERVAL_MS),
                repeatCount = json.optInt("repeatCount", DEFAULT_REPEAT_COUNT),
            )
        }
    }
}
