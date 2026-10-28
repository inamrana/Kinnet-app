package com.inamulhaq.i230839

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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Espresso tests for two backend-connected workflows. They talk to the real Firebase project,
 * so they need a test account passed as instrumentation arguments:
 *
 *   testEmail, testPassword  – an existing Kinnect account with a finished profile
 *   testFriendUid            – the uid of one of that account's friends (for the chat test)
 *
 * Android Studio: Run > Edit Configurations > Instrumentation arguments, or
 *   gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.testEmail=...
 */
@RunWith(AndroidJUnit4::class)
class BackendFlowTest {

    private val args = InstrumentationRegistry.getArguments()
    private val email = args.getString("testEmail").orEmpty()
    private val password = args.getString("testPassword").orEmpty()
    private val friendUid = args.getString("testFriendUid").orEmpty()

    @Before
    fun signedOut() {
        FirebaseAuth.getInstance().signOut()
    }

    /** Log in with Firebase Auth → Home → Menu → Log out returns to Log in with an empty back stack. */
    @Test
    fun logInWithFirebaseThenLogOutClearsBackStack() {
        assumeTrue("Pass testEmail and testPassword to run this test", email.isNotEmpty() && password.isNotEmpty())
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.input_email)).perform(scrollTo(), clearText(), replaceText(email))
            onView(withId(R.id.input_password)).perform(scrollTo(), clearText(), replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.btn_login)).perform(scrollTo(), click())

            // Firebase answers asynchronously, so wait for Home to appear
            waitFor(withId(R.id.home_root))
            assertEquals(FirebaseAuth.getInstance().currentUser?.email, email)

            onView(withId(R.id.tab_menu)).perform(click())
            waitFor(withId(R.id.menu_root))
            onView(withId(R.id.btn_logout)).perform(scrollTo(), click())

            waitFor(withId(R.id.login_root))
            assertTrue("Log out must sign out of Firebase", FirebaseAuth.getInstance().currentUser == null)

            pressBackUnconditionally()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var resumed = 0
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).size
            }
            assertEquals("Back stack should be empty after logging out", 0, resumed)
        }
    }

    /** Signed in, open a chat, send a message: it shows in the thread and is saved in the database. */
    @Test
    fun sentMessageAppearsInChatAndIsSaved() {
        assumeTrue("Pass testEmail, testPassword and testFriendUid to run this test",
            email.isNotEmpty() && password.isNotEmpty() && friendUid.isNotEmpty())
        val user = Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS).user!!
        val text = "Espresso check ${System.currentTimeMillis()}"

        val intent = Intent(ApplicationProvider.getApplicationContext(), ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_UID, friendUid)
        ActivityScenario.launch<ChatActivity>(intent).use {
            waitFor(withId(R.id.chat_root))
            onView(withId(R.id.input_message)).perform(replaceText(text), closeSoftKeyboard())
            onView(withId(R.id.btn_send)).perform(click())

            // The message comes back through the Realtime Database listener
            waitFor(withText(text))

            val chatId = Db.chatId(user.uid, friendUid)
            val last = Tasks.await(Db.messages(chatId).orderByChild("timestamp").limitToLast(1).get(), 30, TimeUnit.SECONDS)
            val saved = last.children.first().getValue(Message::class.java)
            assertEquals(text, saved?.text)
            assertEquals(user.uid, saved?.senderId)
        }
        FirebaseAuth.getInstance().signOut()
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
