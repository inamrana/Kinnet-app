package com.kinnect.i230577_i230839

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File

/**
 * Sell an item — extra screen for Marketplace (not in the original 23), built in the same style
 * as Edit profile. Uploads the listing and its photo to listings/create.php.
 */
class SellActivity : BaseActivity() {

    private var photo: File? = null
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
        onTap(R.id.sell_photo_card) { pickImageFile { showPhoto(it) } }
        onTap(R.id.sell_category_box) { pickCategory() }
    }

    private fun showPhoto(file: File) {
        photo?.delete()
        photo = file
        Images.show(findViewById<ImageView>(R.id.sell_photo), file.absolutePath)
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
        if (Session.uid == null) return
        val image = photo
        val name = title.text.toString().trim()
        val amount = price.text.toString().toLongOrNull()
        when {
            image == null -> { toast("Add a photo of the item"); return }
            name.isEmpty() -> { title.error = "Required"; return }
            amount == null || amount <= 0 -> { price.error = "Enter a price"; return }
        }

        findViewById<View>(R.id.btn_publish).isEnabled = false
        val fields = mapOf(
            "title" to name,
            "price" to amount,
            "location" to location.text.toString().trim(),
            "category" to category,
            "clientId" to Sync.newClientId(),
        )
        Api.upload("listings/create.php", fields, mapOf("image" to image!!)) { result ->
            if (result.ok) {
                image.delete()
                photo = null
                toast("Listed on Marketplace")
                finish()
            } else {
                findViewById<View>(R.id.btn_publish).isEnabled = true
                toast(if (result.offline) "You're offline. Try again when you're connected." else result.message)
            }
        }
    }
}
