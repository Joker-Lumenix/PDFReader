package com.peng.pdfreader.notification.trigger

import android.content.Context
import com.peng.pdfreader.data.AppPrefs

/**
 * The configuration of the notifications.
 *
 * The values are the ones the owner wants for the first release; they live in
 * the preferences of the app, so they can be changed later without a new
 * release - a remote configuration can write the same values.
 *
 * @param delays how long a scene waits before its notification is sent
 * @param minMinutesSinceInstall how long the app has to be installed before
 *   the first notification is sent
 * @param minMinutesBetweenScenes how long the app has to wait between two
 *   notifications of the same scene
 * @param minSecondsBetweenScenes how long the app has to wait between two
 *   notifications of any scene
 * @param dailyLimit how many notifications one scene may send per day
 * @param allowedWithoutPermission whether a notification may be shown when the
 *   user did not grant the notification permission
 */
data class NotificationConfig(
    val delays: Map<NotificationScene, Long> = DEFAULT_DELAYS,
    val minMinutesSinceInstall: Int = 10,
    val minMinutesBetweenScenes: Int = 60,
    val minSecondsBetweenScenes: Int = 15,
    val dailyLimit: Int = 3,
    val allowedWithoutPermission: Boolean = false,
    /**
     * The campaign of every scene.
     *
     * The campaign decides the icon, the picture and the page the
     * notification leads to. A message of firebase overwrites it.
     */
    val campaigns: Map<NotificationScene, NotificationTarget> = DEFAULT_CAMPAIGNS,
) {

    /** How long the scene waits before its notification is sent. */
    fun delayOf(scene: NotificationScene): Long = delays[scene] ?: 0L

    /** The campaign of the scene, the one of the design by default. */
    fun campaignOf(scene: NotificationScene): NotificationTarget =
        campaigns[scene] ?: NotificationTarget.IMAGE_TO_PDF

    companion object {
        /** The delay of the scenes, in milliseconds. */
        val DEFAULT_DELAYS: Map<NotificationScene, Long> = mapOf(
            // The timer and the events of the system are sent right away.
            NotificationScene.TIMER to 0L,
            NotificationScene.DEVICE_UNLOCK to 0L,
            NotificationScene.HOME_KEY to 0L,
            NotificationScene.ALARM to 0L,
            NotificationScene.FCM to 0L,
            // The app that went to the background and the ad that was clicked
            // wait a while - the user has to be settled first.
            NotificationScene.APP_BACKGROUND to 30 * 60 * 1000L,
            NotificationScene.AD_CLICK to 10 * 60 * 1000L,
        )

        /**
         * The campaign of the scenes.
         *
         * Every scene promotes the picture to PDF by default - the owner can
         * change the campaign of a scene in the preferences.
         */
        val DEFAULT_CAMPAIGNS: Map<NotificationScene, NotificationTarget> = mapOf(
            NotificationScene.TIMER to NotificationTarget.IMAGE_TO_PDF,
            NotificationScene.DEVICE_UNLOCK to NotificationTarget.IMAGE_TO_PDF,
            NotificationScene.HOME_KEY to NotificationTarget.IMAGE_TO_PDF,
            NotificationScene.APP_BACKGROUND to NotificationTarget.IMAGE_TO_PDF,
            NotificationScene.AD_CLICK to NotificationTarget.IMAGE_TO_PDF,
            NotificationScene.ALARM to NotificationTarget.IMAGE_TO_PDF,
            NotificationScene.FCM to NotificationTarget.IMAGE_TO_PDF,
        )

        /**
         * The configuration of the app.
         *
         * The delays and the daily limit are taken from the preferences, so
         * they can be changed without a new release; the rest has its default.
         */
        fun of(context: Context): NotificationConfig {
            val prefs = AppPrefs.get(context)
            val delays = DEFAULT_DELAYS.mapValues { (scene, fallback) ->
                prefs.notificationDelay(scene) ?: fallback
            }
            val campaigns = DEFAULT_CAMPAIGNS.mapValues { (scene, fallback) ->
                prefs.notificationCampaign(scene) ?: fallback
            }
            return NotificationConfig(
                delays = delays,
                minMinutesSinceInstall = prefs.notificationMinMinutesSinceInstall,
                minMinutesBetweenScenes = prefs.notificationMinMinutesBetweenScenes,
                minSecondsBetweenScenes = prefs.notificationMinSecondsBetweenScenes,
                dailyLimit = prefs.notificationDailyLimit,
                allowedWithoutPermission = prefs.notificationAllowedWithoutPermission,
                campaigns = campaigns,
            )
        }
    }
}
