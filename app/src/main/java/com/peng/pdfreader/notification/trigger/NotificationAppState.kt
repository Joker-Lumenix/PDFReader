package com.peng.pdfreader.notification.trigger

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.peng.pdfreader.core.util.Logger

/**
 * Whether the app is in the foreground.
 *
 * The state comes from the process lifecycle: it is on as long as any activity
 * of the app is visible. The rules of the notifications need it to know whether
 * a notification would be in the way of the user.
 */
object NotificationAppState {

    private const val TAG = "NotificationAppState"

    /**
     * Whether the user left the app with the home button.
     *
     * The app is still in the foreground when the home button is pressed, so
     * the scene of the home button is only known here - the notification
     * itself is sent when the app went to the background.
     */
    @Volatile
    private var homeButtonUsed = false

    /**
     * Whether an ad was clicked.
     *
     * The ad opens its own page, the app goes to the background - that is the
     * moment the scene of the ad may send its notification.
     */
    @Volatile
    private var adClicked = false

    /** Remembers that the user left the app with the home button. */
    fun markHomeButtonUsed() {
        homeButtonUsed = true
    }

    /**
     * Whether the user left the app with the home button.
     *
     * The flag is consumed, so the next time the app goes to the background
     * it is the scene "app background" again.
     */
    fun consumeHomeButtonUsed(): Boolean {
        val used = homeButtonUsed
        homeButtonUsed = false
        return used
    }

    /** Remembers that an ad was clicked. */
    fun markAdClicked() {
        adClicked = true
    }

    /**
     * Whether an ad was clicked before the app went to the background.
     *
     * The flag is consumed, so the next time the app goes to the background
     * it is the scene "app background" again.
     */
    fun consumeAdClicked(): Boolean {
        val clicked = adClicked
        adClicked = false
        return clicked
    }

    /** Whether the app is in the foreground at the moment. */
    fun isForeground(context: Context): Boolean {
        val lifecycle = runCatching { ProcessLifecycleOwner.get().lifecycle }
            .getOrNull() ?: return false
        return lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    /**
     * Watches the app state.
     *
     * The listener is called when the app comes to the foreground and when it
     * leaves it - the background scene of the notifications uses it.
     */
    fun observe(owner: Lifecycle, listener: Listener) {
        owner.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_START -> listener.onForeground()
                        Lifecycle.Event.ON_STOP -> listener.onBackground()
                        else -> Unit
                    }
                }
            },
        )
    }

    /** The listener of the app state. */
    interface Listener {
        /** The app came to the foreground. */
        fun onForeground()

        /** The app went to the background. */
        fun onBackground()
    }

    /** Logs the state of the app, it helps to find a scene that misbehaves. */
    fun log(state: String) {
        Logger.i(TAG, state)
    }
}
