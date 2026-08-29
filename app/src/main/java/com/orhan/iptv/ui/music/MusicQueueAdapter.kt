package com.orhan.iptv.ui.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem

/**
 * Çalma sırası listesi.
 *
 * Çalan parça vurgulanıyor; kullanıcı listeden herhangi bir parçaya atlayabiliyor.
 */
class MusicQueueAdapter(
    private val onSelected: (index: Int) -> Unit
) : RecyclerView.Adapter<MusicQueueAdapter.VH>() {

    private var items: List<MediaItem> = emptyList()
    private var playingIndex = 0

    fun submit(list: List<MediaItem>, playing: Int) {
        items = list
        playingIndex = playing
        notifyDataSetChanged()
    }

    fun setPlaying(index: Int) {
        val previous = playingIndex
        playingIndex = index
        notifyItemChanged(previous)
        notifyItemChanged(index)
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
            val position = bindingAdapterPosition
            title.text = "${position + 1}.  ${item.title}"
            subtitle.text = item.group.orEmpty()
            subtitle.visibility = if (item.group.isNullOrBlank()) View.GONE else View.VISIBLE

            icon.setImageResource(R.drawable.ic_music)
            // Çalan parça hem simgesiyle hem tam opaklığıyla ayrışsın.
            val playing = position == playingIndex
            icon.alpha = if (playing) 1f else 0.35f
            title.alpha = if (playing) 1f else 0.7f

            frame.setOnClickListener { onSelected(position) }
        }
    }
}
