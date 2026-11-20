package com.peng.pdfreader.notification.trigger

import android.content.Context

/**
 * The rules a scene has to pass before its notification is sent.
 *
 * The order of the checks is the one of the owner:
 *
 * 1. the global check: the app must not be in the foreground and the last
 *    notification of any scene must be at least 15 seconds ago
 * 2. the check of the scene: the app has to be installed for a while, the
 *    same scene has to be quiet for a while and the daily limit of the scene
 *    must not be reached yet
 */
object NotificationRules {

    /**
     * The global check.
     *
     * @param foreground whether the app is in the foreground at the moment
     * @return null when the scene may go on, the reason otherwise
     */
    fun globalBlocker(
        context: Context,
        state: NotificationState,
        scene: NotificationScene,
        now: Long,
        config: NotificationConfig,
        foreground: Boolean = NotificationAppState.isForeground(context),
    ): String? {
        // The app is in the foreground: the user is already using it, a
        // notification would only be in the way.
        if (foreground) return "the app is in the foreground"

        // Two notifications of any scene must be at least 15 seconds apart.
        val last = state.lastAnyTriggerAt()
        if (last > 0L && now - last < config.minSecondsBetweenScenes * 1000L) {
            return "the last notification was less than ${config.minSecondsBetweenScenes}s ago"
        }
        return null
    }

    /**
     * The check of the scene.
     *
     * @return null when the scene may send its notification, the reason
     *   otherwise
     */
    fun sceneBlocker(
        state: NotificationState,
        scene: NotificationScene,
        now: Long,
        config: NotificationConfig,
    ): String? {
        // The app has to be installed for a while.
        val installedAt = state.installedAt
        if (installedAt > 0L &&
            now - installedAt < config.minMinutesSinceInstall * 60_000L
        ) {
            val minutes = (now - installedAt) / 60_000L
            return "the app is installed for $minutes min only"
        }

        // The same scene has to be quiet for a while.
        val last = state.lastTriggerAt(scene)
        if (last > 0L && now - last < config.minMinutesBetweenScenes * 60_000L) {
            val minutes = (now - last) / 60_000L
            return "the scene was $minutes min ago"
        }

        // The daily limit of the scene must not be reached yet.
        val count = state.dailyCount(scene)
        if (count >= config.dailyLimit) {
            return "the daily limit of $count is reached"
        }
        return null
    }
}
