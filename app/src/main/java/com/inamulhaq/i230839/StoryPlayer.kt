package com.inamulhaq.i230839

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.ContextCompat

/** Places a story's text label on the card at its saved position and in its colour. */
object StoryLabel {

    fun apply(label: TextView, text: String, style: String, x: Double, y: Double) {
        if (text.isEmpty()) {
            label.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE
        label.text = text
        style(label, style)
        val parent = label.parent as View
        parent.post {
            label.translationX = (x * (parent.width - label.width)).toFloat().coerceAtLeast(0f)
            label.translationY = (y * (parent.height - label.height)).toFloat().coerceAtLeast(0f)
        }
    }

    /** Orange label with white text (Story editor design) or white with dark text (Story viewer design). */
    fun style(label: TextView, style: String) {
        val context = label.context
        val white = style == Story.LABEL_WHITE
        label.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (white) R.color.white else R.color.badge_red)
        )
        label.setTextColor(ContextCompat.getColor(context, if (white) R.color.text_primary else R.color.white))
        label.rotation = if (white) -3f else 2f
    }
}

/**
 * Plays a list of stories in the viewer screens: progress segments at the top, 5 seconds per photo
 * (or the video's length), tap left to go back and right to go forward.
 */
class StoryPlayer(
    private val activity: Activity,
    private val onShow: (index: Int, story: Story) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val image = activity.findViewById<ImageView>(R.id.story_image)
    private val video = activity.findViewById<VideoView>(R.id.story_video)
    private val label = activity.findViewById<TextView>(R.id.story_label)
    private val progress = activity.findViewById<LinearLayout>(R.id.story_progress)
    private var stories = listOf<Story>()
    private var index = 0
    private var animator: ValueAnimator? = null
    private val fills = mutableListOf<View>()

    init {
        activity.findViewById<View>(R.id.story_prev).setOnClickListener { previous() }
        activity.findViewById<View>(R.id.story_next).setOnClickListener { next() }
        video.setOnPreparedListener { player ->
            player.isLooping = false
            video.start()
            runTimer(player.duration.toLong().coerceIn(1_000L, 30_000L))
        }
        video.setOnCompletionListener { next() }
    }

    val current: Story? get() = stories.getOrNull(index)

    /** Replaces the list (for example when a new story arrives) and keeps the current story if still there. */
    fun setStories(list: List<Story>, startAt: Int = -1) {
        val keepId = current?.id
        stories = list
        buildSegments()
        if (list.isEmpty()) { onFinished(); return }
        val target = when {
            startAt >= 0 -> startAt
            keepId != null -> list.indexOfFirst { it.id == keepId }.takeIf { it >= 0 } ?: 0
            else -> 0
        }
        if (keepId == null || list.getOrNull(target)?.id != keepId) show(target) else updateSegments()
    }

    private fun buildSegments() {
        progress.removeAllViews()
        fills.clear()
        val gap = (4 * activity.resources.displayMetrics.density).toInt()
        stories.forEachIndexed { i, _ ->
            val track = FrameLayout(activity).apply { setBackgroundResource(R.drawable.bg_progress_track) }
            val fill = View(activity).apply {
                setBackgroundResource(R.drawable.bg_progress_fill)
                pivotX = 0f
                scaleX = 0f
            }
            track.addView(fill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) params.marginStart = gap
            progress.addView(track, params)
            fills += fill
        }
        updateSegments()
    }

    private fun updateSegments() {
        fills.forEachIndexed { i, fill -> if (i != index) fill.scaleX = if (i < index) 1f else 0f }
    }

    private fun show(i: Int) {
        animator?.cancel()
        index = i
        updateSegments()
        val story = stories[i]
        onShow(i, story)
        StoryLabel.apply(label, story.label, story.labelStyle, story.labelX, story.labelY)
        Images.show(image, story.thumb)
        image.visibility = View.VISIBLE
        video.visibility = View.GONE
        video.stopPlayback()
        val id = story.id
        Stories.loadMedia(activity, story) { photo, file ->
            if (current?.id != id || activity.isFinishing) return@loadMedia
            when {
                photo != null -> {
                    Images.show(image, photo)
                    runTimer(PHOTO_MS)
                }
                file != null -> {
                    video.visibility = View.VISIBLE
                    video.setVideoPath(file.absolutePath)
                }
                else -> runTimer(PHOTO_MS)
            }
        }
        Stories.markViewed(story)
    }

    private fun runTimer(duration: Long) {
        animator?.cancel()
        val fill = fills.getOrNull(index) ?: return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { fill.scaleX = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled && !current!!.isVideo) next()
                }
            })
            start()
        }
    }

    fun next() {
        if (index + 1 < stories.size) show(index + 1) else onFinished()
    }

    fun previous() {
        show((index - 1).coerceAtLeast(0))
    }

    fun pause() {
        animator?.pause()
        if (video.isPlaying) video.pause()
    }

    fun resume() {
        animator?.resume()
        if (video.visibility == View.VISIBLE && !video.isPlaying) video.start()
    }

    fun stop() {
        animator?.cancel()
        video.stopPlayback()
    }

    private companion object {
        const val PHOTO_MS = 5_000L
    }
}
