package com.kinnect.i230577_i230839

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real Kinnect PHP backend at BuildConfig.API_BASE (server.properties).
 * Needs one test account with a finished profile and one friend, passed as instrumentation
 * arguments testEmail, testPassword and testFriendUid (the friend's numeric user id).
 */
@RunWith(AndroidJUnit4::class)
class BackendFlowTest {

    private val args = InstrumentationRegistry.getArguments()
    private val email = args.getString("testEmail").orEmpty()
    private val password = args.getString("testPassword").orEmpty()
    private val friendUid = args.getString("testFriendUid").orEmpty()

    @Before
    fun signedOut() {
        // Grant runtime permissions up front so no system dialog pauses the app mid-test
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { permission ->
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(pkg, permission) }
        }
        signOutLocally()
    }

    /** Log in through auth/login.php → Home → Menu → Log out returns to Log in with an empty back stack. */
    @Test
    fun logInThenLogOutClearsBackStack() {
        assumeTrue("Pass testEmail and testPassword to run this test", email.isNotEmpty() && password.isNotEmpty())
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.input_email)).perform(scrollTo(), clearText(), replaceText(email))
            onView(withId(R.id.input_password)).perform(scrollTo(), clearText(), replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.btn_login)).perform(scrollTo(), click())

            // The web service answers asynchronously, so wait for Home to appear
            waitFor(withId(R.id.home_root))
            assertTrue("Logging in must store a session token", Session.isLoggedIn)
            assertEquals(email.lowercase(), Session.user?.email)

            onView(withId(R.id.tab_menu)).perform(click())
            waitFor(withId(R.id.menu_root))
            onView(withId(R.id.btn_logout)).perform(scrollTo(), click())

            waitFor(withId(R.id.login_root))
            assertFalse("Log out must clear the session token", Session.isLoggedIn)

            pressBackUnconditionally()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var resumed = 0
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).size
            }
            assertEquals("Back stack should be empty after logging out", 0, resumed)
        }
    }

    /** Signed in, open a chat, send a message: it shows in the thread and is saved on the server. */
    @Test
    fun sentMessageAppearsInChatAndIsSaved() {
        assumeTrue("Pass testEmail, testPassword and testFriendUid to run this test",
            email.isNotEmpty() && password.isNotEmpty() && friendUid.isNotEmpty())
        // Log in straight through the web service (the UI login is covered by the test above)
        val login = Api.request("POST", "auth/login.php", mapOf("email" to email, "password" to password), token = null)
        assertTrue("Log in failed: ${login.message}", login.ok)
        val me = login.json!!.getJSONObject("user").toUser()
        Session.save(login.json!!.getString("token"), me)
        val text = "Espresso check ${System.currentTimeMillis()}"

        val intent = Intent(ApplicationProvider.getApplicationContext(), ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_UID, friendUid)
        ActivityScenario.launch<ChatActivity>(intent).use {
            waitFor(withId(R.id.chat_root))
            onView(withId(R.id.input_message)).perform(replaceText(text), closeSoftKeyboard())
            onView(withId(R.id.btn_send)).perform(click())

            // Shown at once (sent offline-first), then confirmed by the server
            waitFor(withText(text))
            val saved = waitForSavedMessage(text)
            assertNotNull("The message never reached the server", saved)
            assertEquals(me.uid, saved!!.optString("senderId"))
            assertEquals(friendUid, saved.optString("receiverId"))
        }
        signOutLocally()
    }

    /** Asks messages/sync.php until the conversation contains [text], or gives up after [timeoutMs]. */
    private fun waitForSavedMessage(text: String, timeoutMs: Long = 20_000): org.json.JSONObject? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val result = Api.request("GET", "messages/sync.php", mapOf("with" to friendUid, "since" to 0))
            val messages = result.json?.optJSONArray("messages")
            if (result.ok && messages != null) {
                (0 until messages.length()).map { messages.getJSONObject(it) }
                    .lastOrNull { it.optString("text") == text }
                    ?.let { return it }
            }
            Thread.sleep(500)
        }
        return null
    }

    /** Starts each test signed out, without needing the server. */
    private fun signOutLocally() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Session.clear()
            LocalDb.clearAll()
        }
    }

    /** Polls until a view matching [matcher] is on screen (network-backed screens load asynchronously). */
    private fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 20_000) {
        val end = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < end) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (e: NoMatchingViewException) {
                lastError = e
            } catch (e: AssertionError) {
                lastError = e
            }
            Thread.sleep(300)
        }
        throw AssertionError("Timed out waiting for view", lastError)
    }
}
