package com.inamulhaq.i230839

import java.text.NumberFormat
import java.util.Locale

/** An item for sale, stored under marketplace/{id}. [image] is a Base64 JPEG (see [Images]). */
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
        val CATEGORIES = listOf("Vehicles", "Furniture", "Electronics", "Home & garden", "Clothing", "Books", "Other")
    }

    /** "Rs 45,000", as on the Marketplace cards. */
    val priceText: String get() = "Rs " + NumberFormat.getIntegerInstance(Locale.US).format(price)

    /** Listings from the last 24 hours get the red "JUST LISTED" label. */
    val isNew: Boolean get() = System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000L
}
