package com.orhan.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind

/** Dikey listeler (NAS gezgini, dizi bölümleri) için satır adaptörü. */
class RowAdapter(
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<RowAdapter.VH>() {

    private var items: List<MediaItem> = emptyList()

    fun submit(list: List<MediaItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val frame: View = view.findViewById(R.id.card_frame)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val title: TextView = view.findViewById(R.id.title)
        private val subtitle: TextView = view.findViewById(R.id.subtitle)

        fun bind(item: MediaItem) {
            title.text = item.title
            subtitle.text = item.group.orEmpty()
            subtitle.visibility = if (item.group.isNullOrBlank()) View.GONE else View.VISIBLE

            icon.setImageResource(
                when (item.kind) {
                    MediaKind.FOLDER -> R.drawable.ic_folder
                    MediaKind.LIVE -> R.drawable.ic_live
                    else -> R.drawable.ic_file_video
                }
            )

            frame.setOnClickListener { onClick(item) }
        }
    }
}
