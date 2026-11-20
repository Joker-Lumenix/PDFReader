package com.peng.pdfreader.notification.trigger

/**
 * The scenes that can trigger a notification.
 *
 * Every scene has its own limits (the interval between two of its triggers and
 * the number of notifications per day), the rules of the app apply to all of
 * them the same way.
 */
enum class NotificationScene(val key: String) {

    /** The periodic timer of the app, it runs every minute. */
    TIMER("timer"),

    /** The device was unlocked. */
    DEVICE_UNLOCK("device_unlock"),

    /** The user pressed the home button. */
    HOME_KEY("home_key"),

    /**
     * The app went to the background for another reason than the home button -
     * another app was put in front of it or the screen was switched off.
     */
    APP_BACKGROUND("app_background"),

    /** An ad was clicked. */
    AD_CLICK("ad_click"),

    /** A one shot alarm of the app fired. */
    ALARM("alarm"),

    /** A message of firebase arrived. */
    FCM("fcm"),

    ;

    companion object {
        /** The scene of the given key, null when the key is unknown. */
        fun of(key: String?): NotificationScene? = entries.firstOrNull { it.key == key }
    }
}
