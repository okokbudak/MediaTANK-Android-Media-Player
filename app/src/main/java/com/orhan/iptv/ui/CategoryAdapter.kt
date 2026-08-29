package com.orhan.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R

/** Sol sütundaki bir kategori: ad ve içerdiği öğe sayısı. */
data class Category(val title: String, val count: Int)

/**
 * Kategori listesi.
 *
 * Seçim odaktan ayrı tutuluyor: kullanıcı sağdaki ızgaraya geçtiğinde odak
 * kategoriden ayrılır ama hangi kategoride olduğu görünmeye devam etmeli.
 * Bu yüzden seçili satır `activated`, odaklı satır `focused` durumuyla
 * boyanıyor (bkz. category_selector.xml).
 */
class CategoryAdapter(
    private val onSelected: (index: Int, category: Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<Category> = emptyList()
    private var selectedIndex = 0

    fun submit(list: List<Category>, selectIndex: Int = 0) {
        items = list
        selectedIndex = selectIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    fun selected(): Category? = items.getOrNull(selectedIndex)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val frame: View = view.findViewById(R.id.card_frame)
        private val title: TextView = view.findViewById(R.id.title)
        private val count: TextView = view.findViewById(R.id.count)

        fun bind(category: Category) {
            title.text = category.title
            count.text = "${category.count} içerik"
            frame.isActivated = bindingAdapterPosition == selectedIndex

            val select = {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position != selectedIndex) {
                    val previous = selectedIndex
                    selectedIndex = position
                    notifyItemChanged(previous)
                    notifyItemChanged(position)
                    onSelected(position, items[position])
                }
            }

            // TV'de kategoriler odaklanınca değişmeli: kullanıcı yukarı/aşağı
            // gezinirken sağdaki içerik anında güncellensin, ayrıca OK'a
            // basmak gerekmesin.
            frame.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) select() }
            frame.setOnClickListener { select() }
        }
    }
}
