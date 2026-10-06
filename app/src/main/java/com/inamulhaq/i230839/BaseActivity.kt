package com.inamulhaq.i230839

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * Parent of every screen in Kinnect.
 *
 * - Draws the app edge-to-edge and pads the root layout so content never hides
 *   behind the status bar, navigation bar or keyboard.
 * - Offers tiny helpers for the simple Intent based navigation used across the app.
 * - Wires the five top tabs (Home, Friends, Marketplace, Notifications, Menu).
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applySystemBarInsets()
    }

    /** Adds the system bar (and keyboard) sizes to the root view's own padding. */
    private fun applySystemBarInsets() {
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
        val start = root.paddingLeft
        val top = root.paddingTop
        val end = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                start + bars.left,
                top + bars.top,
                end + bars.right,
                bottom + max(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    /** Opens [screen] on top of the current one, so Back returns here. */
    protected fun open(screen: Class<*>) {
        startActivity(Intent(this, screen))
    }

    /** Runs [action] when the view with [id] is tapped. */
    protected fun onTap(id: Int, action: () -> Unit) {
        findViewById<View>(id)?.setOnClickListener { action() }
    }

    /** Taps on any of [ids] open [screen]. */
    protected fun tapOpens(screen: Class<*>, vararg ids: Int) {
        ids.forEach { id -> onTap(id) { open(screen) } }
    }

    /** Taps on any of [ids] close this screen (same as pressing Back). */
    protected fun tapCloses(vararg ids: Int) {
        ids.forEach { id -> onTap(id) { finish() } }
    }

    /**
     * Highlights the [selected] tab and makes the five top tabs switch screens.
     * Already-open tab screens are brought to the front instead of being stacked again.
     */
    protected fun setupTopTabs(selected: Int) {
        TABS.forEachIndexed { index, tab ->
            val active = index == selected
            findViewById<ImageView>(tab.iconId)?.setColorFilter(
                ContextCompat.getColor(this, if (active) R.color.teal else R.color.text_primary)
            )
            findViewById<View>(tab.indicatorId)?.visibility =
                if (active) View.VISIBLE else View.INVISIBLE
            findViewById<View>(tab.tabId)?.apply {
                isSelected = active
                setOnClickListener { if (!active) switchTab(tab.screen) }
            }
        }
        // Search and Chats buttons are part of every main header.
        tapOpens(SearchActivity::class.java, R.id.btn_search)
        tapOpens(ChatsActivity::class.java, R.id.btn_messenger)
    }

    /** Switches to another main tab without stacking duplicate tab screens. */
    protected fun switchTab(screen: Class<*>) {
        val intent = Intent(this, screen).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in_fast, R.anim.none)
    }

    /** Log out: back to Log in with an empty back stack. */
    protected fun logOut() {
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    /** Enter the app after logging in / signing up: Home becomes the only screen in the stack. */
    protected fun enterHome() {
        val intent = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private data class Tab(val tabId: Int, val iconId: Int, val indicatorId: Int, val screen: Class<*>)

    private companion object {
        val TABS = listOf(
            Tab(R.id.tab_home, R.id.tab_home_icon, R.id.tab_home_line, HomeActivity::class.java),
            Tab(R.id.tab_friends, R.id.tab_friends_icon, R.id.tab_friends_line, FriendsActivity::class.java),
            Tab(R.id.tab_market, R.id.tab_market_icon, R.id.tab_market_line, MarketplaceActivity::class.java),
            Tab(R.id.tab_notif, R.id.tab_notif_icon, R.id.tab_notif_line, NotificationsActivity::class.java),
            Tab(R.id.tab_menu, R.id.tab_menu_icon, R.id.tab_menu_line, MenuActivity::class.java),
        )
    }
}
