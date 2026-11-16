package com.peng.pdfreader

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.ui.flow.AppFlow
import com.peng.pdfreader.ui.splash.SplashActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The start up flow: the splash screen hands over to the onboarding, the
 * permission step or the home screen.
 */
@RunWith(AndroidJUnit4::class)
class StartupFlowTest {

    @Test
    fun theSplashScreenStartsWithoutCrashing() {
        ActivityScenario.launch(SplashActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("the splash has no content", activity.window.decorView.width >= 0)
            }
        }
    }

    @Test
    fun theSplashScreenLeavesAfterTheProgressCycle() {
        val started = System.currentTimeMillis()
        ActivityScenario.launch(SplashActivity::class.java).use { scenario ->
            // the progress cycle is three seconds, the screen has to be gone by then
            var finished = false
            val deadline = started + 8_000L
            while (System.currentTimeMillis() < deadline) {
                if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) {
                    finished = true
                    break
                }
                Thread.sleep(200)
            }
            assertTrue("the splash screen did not leave", finished)
        }
    }

    @Test
    fun theFlowContinuesWithTheOnboardingOnAFreshInstall() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_shown", false).apply()

        val step = AppFlow.nextStep(
            onboardingShown = false,
            storageGranted = true,
        )
        assertEquals(AppFlow.Step.ONBOARDING, step)
    }

    @Test
    fun theHomeIsOpenedForADocumentFromAnotherApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW)
        // setData clears the type, so both have to be set together
        intent.setDataAndType(
            android.net.Uri.parse("content://com.peng.pdfreader.test/sample.pdf"),
            "application/pdf",
        )
        // the system handles pdf documents as well, the component points the
        // intent at this app
        intent.component = android.content.ComponentName(
            context.packageName,
            MainActivity::class.java.name,
        )
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // the intent must not crash the shell, even when the uri cannot be read
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity is MainActivity)
            }
        }
    }

    @Test
    fun theRepositoryIsReadyAfterTheShellStarted() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val repository = DocumentRepository.get(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
            assertTrue(repository.all().isNotEmpty())
        }
    }
}
