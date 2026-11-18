package com.kinnect.i230577_i230839

import java.text.NumberFormat
import java.util.Locale

/** An item for sale from listings/list.php. [image] is the photo's URL on the Kinnect server. */
data class Listing(
    val id: String = "",
    val sellerId: String = "",
    val title: String = "",
    val price: Long = 0L,
    val location: String = "",
    val category: String = "",
    val image: String = "",
    val timestamp: Long = 0L,
) {
    companion object {
        fun from(json: org.json.JSONObject) = Listing(
            id = json.optString("id"),
            sellerId = json.optString("sellerId"),
            title = json.optString("title"),
            price = json.optLong("price"),
            location = json.optString("location"),
            category = json.optString("category"),
            image = json.optString("image"),
            timestamp = json.optLong("timestamp"),
        )

        val CATEGORIES = listOf("Vehicles", "Furniture", "Electronics", "Home & garden", "Clothing", "Books", "Other")
    }

    /** "Rs 45,000", as on the Marketplace cards. */
    val priceText: String get() = "Rs " + NumberFormat.getIntegerInstance(Locale.US).format(price)

    /** Listings from the last 24 hours get the red "JUST LISTED" label. */
    val isNew: Boolean get() = System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000L
}
