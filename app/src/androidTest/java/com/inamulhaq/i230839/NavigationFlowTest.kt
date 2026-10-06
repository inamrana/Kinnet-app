package com.inamulhaq.i230839

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Espresso tests for the two critical multi-step user workflows. */
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    /** Home → Comments → Back returns to Home. */
    @Test
    fun homeToCommentsAndBackToHome() {
        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.home_root)).check(matches(isDisplayed()))

            onView(withId(R.id.action_comment)).perform(scrollTo(), click())
            onView(withId(R.id.comments_root)).check(matches(isDisplayed()))

            pressBack()
            onView(withId(R.id.home_root)).check(matches(isDisplayed()))
        }
    }

    /** Log in → Menu → Log out lands on Log in, and the back stack is empty afterwards. */
    @Test
    fun loginThenLogoutClearsBackStack() {
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.btn_login)).perform(scrollTo(), click())
            onView(withId(R.id.home_root)).check(matches(isDisplayed()))

            onView(withId(R.id.tab_menu)).perform(click())
            onView(withId(R.id.menu_root)).check(matches(isDisplayed()))

            onView(withId(R.id.btn_logout)).perform(scrollTo(), click())
            onView(withId(R.id.login_root)).check(matches(isDisplayed()))

            // Back from Log in must leave the app: nothing (Home, Menu…) is left underneath.
            pressBackUnconditionally()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var resumed = 0
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).size
            }
            assertTrue("Back stack should be empty after logging out", resumed == 0)
        }
    }
}
