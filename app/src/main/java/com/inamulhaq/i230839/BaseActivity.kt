package com.inamulhaq.i230839

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
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
}
