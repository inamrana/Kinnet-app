package com.kinnect.i230577_i230839

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * 23 · Marketplace tab — "Today's picks" are live listings from marketplace/, newest first.
 * Sell opens the Sell screen; Categories filters the grid; tapping an item lets you message
 * the seller (or remove your own listing).
 */
class MarketplaceActivity : BaseActivity() {

    private val watch = Watch()
    private var listings = listOf<Listing>()
    private var category: String? = null
    private lateinit var grid: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marketplace)
        setupTopTabs(selected = 2)

        grid = findViewById(R.id.listings_grid)
        tapOpens(SellActivity::class.java, R.id.btn_sell)
        onTap(R.id.btn_categories) { pickCategory() }

        watch.on(Db.marketplace.orderByChild("timestamp").limitToLast(MAX_LISTINGS)) { snapshot ->
            listings = snapshot.children.mapNotNull { child ->
                child.getValue(Listing::class.java)?.copy(id = child.key.orEmpty())
            }.sortedByDescending { it.timestamp }
            showListings()
        }
    }

    private fun showListings() {
        val shown = listings.filter { category == null || it.category == category }
        findViewById<TextView>(R.id.picks_count).text =
            if (shown.size == 1) "1 item" else "${shown.size} items"
        findViewById<TextView>(R.id.listings_empty).apply {
            visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            text = if (category == null) "Nothing for sale yet. Tap Sell to list the first item."
            else "Nothing in $category yet."
        }

        // Two cards per row, like the design's grid
        grid.removeAllViews()
        shown.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, if (rowIndex == 0) dp(14) else dp(18), 0, 0)
            }
            pair.forEachIndexed { index, listing -> row.addView(card(row, listing, index)) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(10) })
            grid.addView(row)
        }
    }

    private fun card(row: LinearLayout, listing: Listing, index: Int): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_listing, row, false)
        if (index == 1) (card.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
        Images.show(card.findViewById<ImageView>(R.id.listing_image), listing.image)
        card.findViewById<View>(R.id.listing_new).visibility = if (listing.isNew) View.VISIBLE else View.GONE
        card.findViewById<TextView>(R.id.listing_price).text = listing.priceText
        card.findViewById<TextView>(R.id.listing_title).text = listing.title
        card.findViewById<TextView>(R.id.listing_location).text = listing.location.ifEmpty { listing.category }
        card.findViewById<ImageView>(R.id.listing_image).contentDescription = listing.title
        card.setOnClickListener { openListing(listing) }
        return card
    }

    private fun openListing(listing: Listing) {
        val dialog = AlertDialog.Builder(this).setTitle("${listing.title} · ${listing.priceText}")
        if (listing.sellerId == Db.uid) {
            dialog.setMessage("This is your listing.")
                .setPositiveButton("Remove listing") { _, _ -> Db.marketplace.child(listing.id).removeValue() }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        UserCache.get(listing.sellerId) { seller ->
            val place = listing.location.ifEmpty { "Karachi" }
            dialog.setMessage("Sold by ${seller.fullName} · $place · listed ${TimeAgo.short(listing.timestamp)} ago")
                .setPositiveButton("Message seller") { _, _ ->
                    startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_UID, listing.sellerId))
                }
                .setNeutralButton("View profile") { _, _ -> PersonRow.openProfile(grid, listing.sellerId) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun pickCategory() {
        val options = arrayOf("All categories") + Listing.CATEGORIES
        AlertDialog.Builder(this)
            .setTitle("Categories")
            .setItems(options) { _, which ->
                category = if (which == 0) null else options[which]
                findViewById<TextView>(R.id.categories_label).text = category ?: "Categories"
                showListings()
            }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        watch.stop()
        super.onDestroy()
    }

    private companion object {
        const val MAX_LISTINGS = 60
    }
}
