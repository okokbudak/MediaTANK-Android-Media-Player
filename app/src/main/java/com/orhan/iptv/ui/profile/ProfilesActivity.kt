package com.orhan.iptv.ui.profile

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.Profile
import com.orhan.iptv.data.Repository
import com.orhan.iptv.ui.MainActivity

/**
 * "Kim izliyor?" ekranı — uygulamanın giriş noktası.
 *
 * Profil seçildikten sonra [Repository] tüm tercihleri (favoriler, kaldığı yer)
 * o profilin ad alanından okur.
 */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private val adapter = ProfileAdapter(
        onClick = { profile -> enter(profile) },
        onLongClick = { profile -> showProfileOptions(profile) },
        onAddClick = { promptCreate() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)

        list = findViewById(R.id.profile_list)
        list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        list.adapter = adapter

        // Hiç profil yoksa doğrudan oluşturma penceresini aç: ilk açılışta
        // kullanıcıyı boş bir ekranla karşılamayalım.
        if (Repository.profiles().isEmpty()) promptCreate(firstRun = true)
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(Repository.profiles())
    }

    private fun enter(profile: Profile) {
        Repository.setActiveProfile(profile.id)
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun promptCreate(firstRun: Boolean = false) {
        val input = EditText(this).apply {
            hint = "Profil adı"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(if (firstRun) "İlk profilinizi oluşturun" else "Yeni profil")
            .setView(input)
            .setPositiveButton("Oluştur") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "İzleyici" }
                val profile = Profile(
                    id = Repository.newId(),
                    name = name,
                    // Renkleri sırayla dağıt; profiller görsel olarak ayrışsın.
                    color = Profile.PALETTE[Repository.profiles().size % Profile.PALETTE.size]
                )
                Repository.addProfile(profile)
                adapter.submit(Repository.profiles())
                if (firstRun) enter(profile)
            }
            .setNegativeButton("Vazgeç", null)
            .setCancelable(!firstRun)
            .show()
    }

    /** Uzun basınca çıkan seçenekler: adı düzelt veya profili sil. */
    private fun showProfileOptions(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(arrayOf("Adını düzenle", "Profili sil")) { _, which ->
                if (which == 0) promptRename(profile) else confirmDelete(profile)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun promptRename(profile: Profile) {
        val input = EditText(this).apply {
            setText(profile.name)
            setSelection(profile.name.length)
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Profil adı")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                // Kimlik korunuyor: favoriler ve kaldığı yer kayıtları
                // profil kimliğine bağlı, ad değişince kaybolmamalı.
                Repository.addProfile(profile.copy(name = name))
                adapter.submit(Repository.profiles())
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun confirmDelete(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle("${profile.name} silinsin mi?")
            .setMessage("Bu profilin favorileri ve kaldığı yer kayıtları da silinir.")
            .setPositiveButton("Sil") { _, _ ->
                Repository.removeProfile(profile.id)
                adapter.submit(Repository.profiles())
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
}

/**
 * Profil listesi. Son öğe her zaman "+" ekleme kartıdır, bu yüzden
 * [getItemCount] profil sayısından bir fazladır.
 */
private class ProfileAdapter(
    private val onClick: (Profile) -> Unit,
    private val onLongClick: (Profile) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    private var profiles: List<Profile> = emptyList()

    fun submit(list: List<Profile>) {
        profiles = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = profiles.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (position < profiles.size) holder.bind(profiles[position]) else holder.bindAdd()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val frame: View = view.findViewById(R.id.card_frame)
        private val avatar: TextView = view.findViewById(R.id.avatar)
        private val name: TextView = view.findViewById(R.id.name)

        fun bind(profile: Profile) {
            avatar.text = profile.initial
            avatar.background = circle(profile.color)
            name.text = profile.name
            frame.setOnClickListener { onClick(profile) }
            frame.setOnLongClickListener { onLongClick(profile); true }
            applyFocusScale()
        }

        fun bindAdd() {
            avatar.text = "+"
            avatar.background = circle(0xFF2A2A2A.toInt())
            name.text = "Profil ekle"
            frame.setOnClickListener { onAddClick() }
            frame.setOnLongClickListener(null)
            applyFocusScale()
        }

        private fun applyFocusScale() {
            frame.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.1f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
                name.alpha = if (hasFocus) 1f else 0.7f
            }
            name.alpha = 0.7f
        }

        private fun circle(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }
}
