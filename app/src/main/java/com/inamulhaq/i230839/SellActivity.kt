package com.inamulhaq.i230839

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.ServerValue

/**
 * Sell an item — extra screen for Marketplace (not in the original 23), built in the same style
 * as Edit profile. Saves a [Listing] to marketplace/{id}; the photo is a Base64 JPEG.
 */
class SellActivity : BaseActivity() {

    private var photo = ""
    private var category = "Other"
    private lateinit var title: EditText
    private lateinit var price: EditText
    private lateinit var location: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell)

        title = findViewById(R.id.input_title)
        price = findViewById(R.id.input_price)
        location = findViewById(R.id.input_location)

        tapCloses(R.id.btn_cancel)
        onTap(R.id.btn_publish) { publish() }
        onTap(R.id.sell_photo_card) { pickImage { showPhoto(it) } }
        onTap(R.id.sell_category_box) { pickCategory() }
    }

    private fun showPhoto(base64: String) {
        photo = base64
        Images.show(findViewById<ImageView>(R.id.sell_photo), base64)
        findViewById<View>(R.id.sell_photo_hint).visibility = View.GONE
    }

    private fun pickCategory() {
        val options = Listing.CATEGORIES.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Category")
            .setSingleChoiceItems(options, options.indexOf(category)) { dialog, which ->
                category = options[which]
                findViewById<TextView>(R.id.sell_category).text = category
                dialog.dismiss()
            }
            .show()
    }

    private fun publish() {
        val me = Db.uid ?: return
        val name = title.text.toString().trim()
        val amount = price.text.toString().toLongOrNull()
        when {
            photo.isEmpty() -> { toast("Add a photo of the item"); return }
            name.isEmpty() -> { title.error = "Required"; return }
            amount == null || amount <= 0 -> { price.error = "Enter a price"; return }
        }

        findViewById<View>(R.id.btn_publish).isEnabled = false
        val ref = Db.marketplace.push()
        val data = mapOf(
            "id" to ref.key,
            "sellerId" to me,
            "title" to name,
            "price" to amount,
            "location" to location.text.toString().trim(),
            "category" to category,
            "image" to photo,
            "timestamp" to ServerValue.TIMESTAMP,
        )
        ref.setValue(data)
            .addOnSuccessListener {
                toast("Listed on Marketplace")
                finish()
            }
            .addOnFailureListener {
                findViewById<View>(R.id.btn_publish).isEnabled = true
                toast(it.localizedMessage ?: "Could not publish the listing")
            }
    }
}
