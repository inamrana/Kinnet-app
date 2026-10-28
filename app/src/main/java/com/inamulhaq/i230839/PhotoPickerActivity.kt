package com.inamulhaq.i230839

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 08 · Photo picker — the device gallery with a large preview of the focused item.
 *
 * Posts ([MODE_POST]) pick one or more photos with numbered selection.
 * Stories ([MODE_STORY]) pick a single photo or video. The chosen items go back as content URIs.
 */
class PhotoPickerActivity : BaseActivity() {

    private val mode by lazy { intent.getStringExtra(EXTRA_MODE) ?: MODE_POST }
    private val forStory get() = mode == MODE_STORY

    private var all = listOf<GalleryItem>()
    private var shown = listOf<GalleryItem>()
    private val selected = mutableListOf<GalleryItem>()
    private var focused: GalleryItem? = null
    private var multiple = true
    private var tab = TAB_GALLERY

    private lateinit var grid: RecyclerView
    private val adapter = GridAdapter()

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermission()) loadGallery() else showMessage("Allow access to your photos to pick from the gallery.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_picker)
        multiple = !forStory

        grid = findViewById(R.id.picker_grid)
        grid.layoutManager = GridLayoutManager(this, COLUMNS)
        grid.adapter = adapter

        tapCloses(R.id.btn_cancel)
        onTap(R.id.btn_next) { finishWithSelection() }
        onTap(R.id.btn_select_multiple) { toggleMultiple() }
        onTap(R.id.tab_gallery) { selectTab(TAB_GALLERY) }
        onTap(R.id.tab_photo) { selectTab(TAB_PHOTO) }
        onTap(R.id.tab_video) {
            if (forStory) selectTab(TAB_VIDEO) else toast("Posts can include photos only")
        }
        findViewById<View>(R.id.btn_select_multiple).visibility = if (forStory) View.GONE else View.VISIBLE
        selectTab(TAB_GALLERY)
        updateHeader()

        if (hasPermission()) loadGallery() else askPermission.launch(Gallery.permissions())
    }

    private fun hasPermission() = Gallery.permissions().any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadGallery() {
        Gallery.load(this, includeVideos = forStory) { items ->
            all = items
            applyFilter()
            if (items.isEmpty()) showMessage("No photos on this device yet.")
        }
    }

    private fun selectTab(newTab: Int) {
        tab = newTab
        listOf(
            Triple(TAB_GALLERY, R.id.tab_gallery_line, R.id.tab_gallery_label),
            Triple(TAB_PHOTO, R.id.tab_photo_line, R.id.tab_photo_label),
            Triple(TAB_VIDEO, R.id.tab_video_line, R.id.tab_video_label),
        ).forEach { (id, line, label) ->
            val active = id == tab
            findViewById<View>(line).visibility = if (active) View.VISIBLE else View.INVISIBLE
            findViewById<TextView>(label).setTextColor(
                ContextCompat.getColor(this, if (active) R.color.teal else R.color.text_secondary)
            )
        }
        applyFilter()
    }

    private fun applyFilter() {
        shown = when (tab) {
            TAB_PHOTO -> all.filter { !it.isVideo }
            TAB_VIDEO -> all.filter { it.isVideo }
            else -> all
        }
        if (focused == null || focused !in shown) focus(shown.firstOrNull())
        adapter.notifyDataSetChanged()
    }

    private fun focus(item: GalleryItem?) {
        focused = item
        val preview = findViewById<ImageView>(R.id.picker_preview)
        if (item == null) preview.setImageDrawable(null) else Gallery.thumbnail(preview, item, 1000)
        findViewById<View>(R.id.picker_message).visibility = View.GONE
    }

    private fun onItemTapped(item: GalleryItem) {
        focus(item)
        if (multiple) {
            if (item in selected) selected.remove(item)
            else if (selected.size < MAX_PHOTOS) selected.add(item)
            else toast("You can add up to $MAX_PHOTOS photos")
        } else {
            selected.clear()
            selected.add(item)
        }
        adapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun toggleMultiple() {
        multiple = !multiple
        if (!multiple && selected.size > 1) {
            val keep = focused?.takeIf { it in selected } ?: selected.first()
            selected.clear()
            selected.add(keep)
        }
        findViewById<TextView>(R.id.select_multiple_label).text = if (multiple) "SELECT MULTIPLE" else "SELECT ONE"
        adapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun updateHeader() {
        val next = findViewById<TextView>(R.id.btn_next)
        next.text = if (selected.size > 1) "Next (${selected.size})" else "Next"
        next.alpha = if (selected.isEmpty()) 0.4f else 1f
    }

    private fun finishWithSelection() {
        if (selected.isEmpty()) {
            toast(if (forStory) "Pick a photo or video" else "Pick at least one photo")
            return
        }
        val data = Intent()
            .putParcelableArrayListExtra(EXTRA_URIS, ArrayList<Uri>(selected.map { it.uri }))
            .putExtra(EXTRA_IS_VIDEO, selected.first().isVideo)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun showMessage(text: String) {
        findViewById<TextView>(R.id.picker_message).apply {
            visibility = View.VISIBLE
            this.text = text
        }
    }

    private inner class GridAdapter : RecyclerView.Adapter<GridAdapter.Cell>() {

        inner class Cell(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.gallery_thumb)
            val focusBorder: View = view.findViewById(R.id.gallery_focus)
            val ring: View = view.findViewById(R.id.gallery_ring)
            val number: TextView = view.findViewById(R.id.gallery_number)
            val duration: TextView = view.findViewById(R.id.gallery_duration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Cell(LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_photo, parent, false))

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(cell: Cell, position: Int) {
            val item = shown[position]
            Gallery.thumbnail(cell.thumb, item, 300)
            val order = selected.indexOf(item)
            cell.focusBorder.visibility = if (item == focused) View.VISIBLE else View.GONE
            cell.number.visibility = if (order >= 0) View.VISIBLE else View.GONE
            cell.ring.visibility = if (order >= 0) View.GONE else View.VISIBLE
            cell.number.text = (order + 1).toString()
            cell.duration.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            cell.duration.text = Gallery.duration(item.durationMs)
            cell.itemView.setOnClickListener { onItemTapped(item) }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_POST = "post"
        const val MODE_STORY = "story"
        const val EXTRA_URIS = "uris"
        const val EXTRA_IS_VIDEO = "isVideo"
        private const val COLUMNS = 4
        private const val MAX_PHOTOS = 10
        private const val TAB_GALLERY = 0
        private const val TAB_PHOTO = 1
        private const val TAB_VIDEO = 2
    }
}
