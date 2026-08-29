package com.orhan.iptv.ui.nas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.smb.SmbThumbnails

/** NAS gezgininin çizim biçimi. */
enum class NasViewMode(val label: String) {
    LIST("Liste"),
    GRID("Kılavuz"),
    TILE("Karo")
}

/**
 * NAS klasör içeriği adaptörü.
 *
 * Liste modunda satır, ızgara/karo modunda 16:9 kart çiziyor. Fotoğraflarda
 * gerçek ön izleme yükleniyor; klasör ve videolarda tür ikonu kalıyor.
 */
class NasAdapter(
    private val scope: LifecycleCoroutineScope,
    private var mode: NasViewMode,
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<NasAdapter.VH>() {

    private var items: List<MediaItem> = emptyList()

    fun submit(list: List<MediaItem>) {
        items = list
        notifyDataSetChanged()
    }

    fun setMode(newMode: NasViewMode) {
        mode = newMode
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout =
            if (mode == NasViewMode.LIST) R.layout.item_row else R.layout.item_nas_tile
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun onViewRecycled(holder: VH) {
        // Geri dönüştürülen satırın ön izleme işini bırak; hızlı kaydırmada
        // NAS onlarca gereksiz istekle boğulmasın.
        holder.cancelThumbnail()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val frame: View = view.findViewById(R.id.card_frame)
        private val title: TextView = view.findViewById(R.id.title)
        private val subtitle: TextView? = view.findViewById(R.id.subtitle)
        private val badge: TextView? = view.findViewById(R.id.badge)
        private val image: ImageView =
            view.findViewById(R.id.poster) ?: view.findViewById(R.id.icon)

        fun cancelThumbnail() = SmbThumbnails.cancel(image)

        fun bind(item: MediaItem) {
            title.text = item.title
            subtitle?.visibility = View.GONE

            val icon = when (item.kind) {
                MediaKind.FOLDER -> R.drawable.ic_folder
                MediaKind.PHOTO -> R.drawable.ic_photo
                else -> R.drawable.ic_file_video
            }

            badge?.visibility = View.GONE

            SmbThumbnails.cancel(image)
            image.setImageResource(icon)
            // Ön izlemesi olmayan türlerde ikon kendi boyutunda dursun,
            // kartı dolduran dev bir blok olmasın.
            image.scaleType = ImageView.ScaleType.CENTER

            if (item.kind == MediaKind.PHOTO) {
                SmbThumbnails.load(scope, image, item) {
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                }
            }

            frame.setOnClickListener { onClick(item) }
        }
    }
}
