package com.inamulhaq.i230839

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Inflates one item_person row (avatar, name, subtitle, friend button) into [parent].
 * Tapping the row opens that user's profile; the round button follows
 * Add friend → Requested → Friends live. Returns the [Watch] so the screen can stop it.
 */
object PersonRow {

    fun add(parent: ViewGroup, user: User, subtitle: String, showAction: Boolean = true): Watch? {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        Avatars.show(row.findViewById(R.id.person_avatar), user)
        row.findViewById<TextView>(R.id.person_name).text = user.fullName
        row.findViewById<TextView>(R.id.person_subtitle).text = subtitle
        row.setOnClickListener { openProfile(parent, user.uid) }
        parent.addView(row)

        val action = row.findViewById<ImageView>(R.id.person_action)
        if (!showAction || user.uid == Db.uid) {
            action.visibility = View.GONE
            return null
        }
        var state = FriendState.NONE
        action.setOnClickListener { Friends.toggle(user.uid, state) }
        return Friends.watchState(user.uid) {
            state = it
            styleAction(action, it)
        }
    }

    fun openProfile(view: View, uid: String) {
        val context = view.context
        val screen = if (uid == Db.uid) ProfileActivity::class.java else OtherProfileActivity::class.java
        context.startActivity(Intent(context, screen).putExtra(OtherProfileActivity.EXTRA_UID, uid))
    }

    /** Teal person+ for "Add friend", a check for Requested / Friends. */
    private fun styleAction(action: ImageView, state: FriendState) {
        val context = action.context
        val (icon, description) = when (state) {
            FriendState.NONE -> R.drawable.ic_person_add to "Add friend"
            FriendState.REQUESTED -> R.drawable.ic_clock to "Requested"
            FriendState.INCOMING -> R.drawable.ic_person_add to "Confirm request"
            FriendState.FRIENDS -> R.drawable.ic_check to "Friends"
        }
        action.setImageResource(icon)
        action.contentDescription = description
        action.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(context, if (state == FriendState.INCOMING) R.color.teal else R.color.teal_light)
        )
        action.setColorFilter(ContextCompat.getColor(context, if (state == FriendState.INCOMING) R.color.white else R.color.teal))
    }
}
