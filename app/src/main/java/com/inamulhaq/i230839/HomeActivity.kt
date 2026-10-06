package com.inamulhaq.i230839

import android.os.Bundle

/**
 * 04 · Home feed.
 * Links to Search, Chats, Create post, Comments, the reaction picker,
 * a Story viewer, your Profile and (through "Create story") the Camera.
 */
class HomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setupTopTabs(selected = 0)

        tapOpens(ProfileActivity::class.java, R.id.composer_avatar)
        tapOpens(CreatePostActivity::class.java, R.id.composer_input, R.id.composer_photo)
        tapOpens(CameraActivity::class.java, R.id.story_create)
        tapOpens(
            StoryViewerActivity::class.java,
            R.id.story_omar, R.id.story_sara, R.id.story_hamza, R.id.story_aisha
        )
        tapOpens(CommentsActivity::class.java, R.id.action_comment, R.id.post_comment_count)
        // Like opens the reaction picker (long press works too, as in most social apps)
        tapOpens(ReactionPickerActivity::class.java, R.id.action_like)
        findViewById<android.view.View>(R.id.action_like).setOnLongClickListener {
            open(ReactionPickerActivity::class.java)
            true
        }
    }
}
