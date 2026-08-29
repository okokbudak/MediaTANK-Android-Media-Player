package com.orhan.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository

/** Kart biçimi. LIST dikey liste görünümü, diğerleri ray ve ızgara içindir. */
enum class CardStyle { POSTER, CHANNEL, WIDE, LIST }

/**
 * Tek bir yatay rayın adaptörü.
 *
 * Odak davranışı DESIGN.md'ye uyuyor: odaklanan kart 1.1 ölçekte büyüyor ve
 * birincil renkte kenarlık kazanıyor. Ölçekleme View üzerinde yapılıyor,
 * ray `clipToPadding=false` olduğu için kırpılmıyor.
 */
class MediaAdapter(
    private val style: CardStyle,
    private val onClick: (MediaItem) -> Unit,
    private val onFocus: (MediaItem) -> Unit = {},
    /** Uzun basış: favoriye ekle/çıkar. */
    private val onLongClick: (MediaItem) -> Unit = {},
    /**
     * Kart, ızgara sütununun tamamını kaplasın.
     * Yatay raylarda kartlar sabit genişliktedir; ızgarada ise sütun çok daha
     * geniş olabiliyor ve sabit genişlik uzun kanal adlarını gereksiz yere
     * kırpıyor.
     */
    private val fillWidth: Boolean = false
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private var items: List<MediaItem> = emptyList()

    fun submit(list: List<MediaItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (style) {
            CardStyle.POSTER -> R.layout.item_poster
            CardStyle.CHANNEL -> R.layout.item_channel
            CardStyle.WIDE -> R.layout.item_wide
            CardStyle.LIST -> R.layout.item_list
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        if (fillWidth) {
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                // Sağ kenar boşluğu sütunlar arası aralığı verir; ızgarada
                // satırlar arası boşluk da gerekiyor.
                params.bottomMargin = view.resources.getDimensionPixelSize(R.dimen.card_gap)
                view.layoutParams = params
            }
        }
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {

        private val frame: View = view.findViewById(R.id.card_frame)
        private val title: TextView = view.findViewById(R.id.title)
        private val image: ImageView? = view.findViewById(R.id.poster) ?: view.findViewById(R.id.logo)
        private val badge: TextView? = view.findViewById(R.id.badge)
        private val subtitle: TextView? = view.findViewById(R.id.subtitle)
        private val progress: ProgressBar? = view.findViewById(R.id.progress)

        fun bind(item: MediaItem) {
            title.text = item.title
            subtitle?.text = item.group.orEmpty()
            subtitle?.visibility = if (item.group.isNullOrBlank()) View.GONE else View.VISIBLE

            // Rozet köşesi paylaşılıyor: izlendi işareti favori yıldızını,
            // o da "CANLI" etiketini geçersiz kılıyor.
            val favorite = Repository.isFavorite(item.id)
            val watched = Repository.isWatched(item.id)
            badge?.let { view ->
                when {
                    watched -> {
                        view.visibility = View.VISIBLE
                        view.text = "✓"
                    }
                    favorite -> {
                        view.visibility = View.VISIBLE
                        view.text = "★"
                    }
                    style == CardStyle.CHANNEL -> {
                        view.visibility = View.VISIBLE
                        view.text = view.context.getString(R.string.badge_live)
                    }
                    item.kind == MediaKind.LIVE -> {
                        view.visibility = View.VISIBLE
                        view.text = view.context.getString(R.string.badge_live)
                    }
                    else -> view.visibility = View.GONE
                }
            }

            val placeholder = when (item.kind) {
                MediaKind.FOLDER -> R.drawable.ic_folder
                MediaKind.FILE -> R.drawable.ic_file_video
                MediaKind.LIVE -> R.drawable.ic_live
                MediaKind.SERIES -> R.drawable.ic_series
                MediaKind.VOD -> R.drawable.ic_movie
                MediaKind.PHOTO -> R.drawable.ic_photo
                MediaKind.AUDIO -> R.drawable.ic_music
            }

            image?.let { view ->
                if (item.logoUrl.isNullOrBlank()) {
                    // CENTER: ikon kendi boyutunda kalsın. FIT_CENTER kullanılırsa
                    // 24dp'lik vektör kartı baştan başa dolduran gri bir bloğa
                    // dönüşüyor ve afişli kartların yanında çirkin duruyor.
                    Glide.with(view).clear(view)
                    view.setImageResource(placeholder)
                    view.scaleType = ImageView.ScaleType.CENTER
                } else {
                    view.scaleType =
                        if (style == CardStyle.CHANNEL) ImageView.ScaleType.FIT_CENTER
                        else ImageView.ScaleType.CENTER_CROP
                    Glide.with(view).load(item.logoUrl)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(view)
                }
            }

            // Kaldığı yer çubuğu yalnız geniş kartlarda ve süre bilinen içerikte.
            progress?.let { bar ->
                val saved = Repository.position(item.id)
                if (saved > 0) {
                    bar.visibility = View.VISIBLE
                    // Süre bilinmiyor; en azından "başlanmış" olduğunu göster.
                    bar.progress = 35
                } else {
                    bar.visibility = View.GONE
                }
            }

            frame.setOnClickListener { onClick(item) }
            frame.setOnLongClickListener {
                onLongClick(item)
                // Yıldızı hemen yansıt.
                notifyItemChanged(bindingAdapterPosition)
                true
            }
            frame.setOnFocusChangeListener { v, hasFocus ->
                // Sütunu dolduran kartlarda büyütme komşuların üstüne taşar;
                // orada kenarlık ve renk zaten yeterli geri bildirim veriyor.
                val stretched = style == CardStyle.LIST || fillWidth
                val scale = if (hasFocus && !stretched) FOCUS_SCALE else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
                title.animate().alpha(if (hasFocus) 1f else 0.75f).setDuration(140).start()
                if (hasFocus) onFocus(item)
            }
            title.alpha = 0.75f
        }
    }

    private companion object {
        /** DESIGN.md: focus-scale 1.1 */
        const val FOCUS_SCALE = 1.1f
    }
}
