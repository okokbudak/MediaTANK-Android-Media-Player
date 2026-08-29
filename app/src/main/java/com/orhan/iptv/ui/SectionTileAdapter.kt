package com.orhan.iptv.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R

/** Ana sayfadaki renkli bölüm kutusu. */
data class Tile(
    val title: String,
    val iconRes: Int,
    val color: Int,
    val subtitle: String,
    val onClick: () -> Unit
)

/**
 * Bölüm kutuları.
 *
 * Arka plan koddan çiziliyor çünkü her kutu farklı renkte; odakta beyaz
 * kenarlık ekleniyor ki renkli zemin üzerinde odak yine de belli olsun.
 */
class SectionTileAdapter(
    private val tiles: List<Tile>
) : RecyclerView.Adapter<SectionTileAdapter.VH>() {

    override fun getItemCount() = tiles.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_section_tile, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(tiles[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val frame: View = view.findViewById(R.id.card_frame)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val title: TextView = view.findViewById(R.id.title)
        private val count: TextView = view.findViewById(R.id.count)

        fun bind(tile: Tile) {
            icon.setImageResource(tile.iconRes)
            icon.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            title.text = tile.title
            count.text = tile.subtitle

            frame.background = background(tile.color, focused = false)
            frame.setOnClickListener { tile.onClick() }
            frame.setOnFocusChangeListener { v, hasFocus ->
                v.background = background(tile.color, hasFocus)
                val scale = if (hasFocus) 1.08f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
            }
        }

        private fun background(color: Int, focused: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = itemView.resources.getDimension(R.dimen.radius_card)
            setColor(color)
            if (focused) setStroke(3, 0xFFFFFFFF.toInt())
        }
    }
}
