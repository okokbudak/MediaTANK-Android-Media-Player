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

    /**
     * Seçili sezonu değiştirir.
     *
     * Burada `notifyDataSetChanged()` KULLANILMAMALI. Kumandayla bir sezona
     * odaklanınca seçim değişiyor ve bu çağrı o sırada odaktaki çipi de
     * yeniden çiziyor; RecyclerView veri seti tümden değişince odağı geri
     * veremiyor, odak kayboluyor ve kullanıcı sağ tuşla bir sonraki sezona
     * hiç geçemiyordu. Yalnızca değişen iki öğe, ve yük (payload) ile
     * güncelleniyor: yük varken öğe yeniden oluşturulmadığı için odak kalıyor.
     */
    fun setSelected(season: Int) {
        val previous = selected
        if (previous == season) return
        selected = season
        previous?.let { seasons.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
        seasons.indexOf(season)
            .takeIf { it >= 0 }
            ?.let { notifyItemChanged(it, PAYLOAD_SELECTION) }
    }

    override fun getItemCount() = seasons.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_season, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(seasons[position])

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            // Yalnız seçili görünümü güncelle; dinleyicilere ve odağa dokunma.
            holder.renderSelection(seasons[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val chip: TextView = view.findViewById(R.id.season_chip)

        fun bind(season: Int) {
            chip.text = season.toString()
            renderSelection(season)
            // Odaklanınca da seç: TV'de yönle gezinirken ayrıca OK'a basmak gerekmesin.
            chip.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && season != selected) onSelected(season)
            }
            chip.setOnClickListener { onSelected(season) }
        }

        fun renderSelection(season: Int) {
            chip.isActivated = season == selected
        }
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
