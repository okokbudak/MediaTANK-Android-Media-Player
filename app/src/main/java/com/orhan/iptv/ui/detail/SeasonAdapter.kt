package com.orhan.iptv.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R

/**
 * Yatay sezon seçici.
 *
 * Seçim odaktan ayrı: kullanıcı bölüm listesine geçtiğinde hangi sezonda
 * olduğu görünmeye devam etmeli.
 */
class SeasonAdapter(
    private val onSelected: (season: Int) -> Unit
) : RecyclerView.Adapter<SeasonAdapter.VH>() {

    private var seasons: List<Int> = emptyList()
    private var selected: Int? = null

    fun submit(list: List<Int>) {
        seasons = list
        selected = list.firstOrNull()
        notifyDataSetChanged()
    }

    fun setSelected(season: Int) {
        selected = season
        notifyDataSetChanged()
    }

    override fun getItemCount() = seasons.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_season, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(seasons[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val chip: TextView = view.findViewById(R.id.season_chip)

        fun bind(season: Int) {
            chip.text = season.toString()
            chip.isActivated = season == selected
            // Odaklanınca da seç: TV'de yönle gezinirken ayrıca OK'a basmak gerekmesin.
            chip.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onSelected(season) }
            chip.setOnClickListener { onSelected(season) }
        }
    }
}
