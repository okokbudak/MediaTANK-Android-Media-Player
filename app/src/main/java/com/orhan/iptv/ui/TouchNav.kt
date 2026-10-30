package com.orhan.iptv.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.orhan.iptv.R

/**
 * Dokunmatik ekranlar için gezinme çubuğu.
 *
 * Uygulama televizyon kumandasına göre yazıldı: geri dönmek kumandanın GERİ
 * tuşuyla oluyordu ve ekranda hiçbir geri düğmesi yoktu. Tabletlerde sistem
 * geri tuşu/hareketi her cihazda kolay erişilir olmuyor ve kullanıcı bir
 * ekrandan nasıl çıkacağını bulamıyordu.
 *
 * [install], etkinliğin içeriğini üstte "Geri" düğmesi olan bir sütuna sarar.
 * Her ekranın düzenine tek tek dokunmak yerine böyle yapılıyor, yani yeni bir
 * ekran yalnızca bu çağrıyı eklemeli. Televizyonda (dokunmatik yok) hiçbir şey
 * yapmaz; kumandanın odak sırası değişmez.
 */
object TouchNav {

    /** Cihaz parmakla dokunulan bir ekran mı? Bkz. `values-finger/bools.xml`. */
    fun isTouch(activity: Activity): Boolean =
        activity.resources.getBoolean(R.bool.is_touch)

    /**
     * `setContentView` çağrısından HEMEN SONRA çağrılmalı.
     * İçerik görünümleri ve kimlikleri değişmez; yalnızca üstlerine bir çubuk eklenir.
     */
    fun install(activity: Activity) {
        if (!isTouch(activity)) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) ?: return
        // İki kez çağrılırsa ikinci çubuk eklenmesin.
        if (root.getTag(R.id.touch_nav_installed) == true) return

        content.removeView(root)

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setTag(R.id.touch_nav_installed, true)
        }

        val bar = activity.layoutInflater.inflate(R.layout.view_touch_bar, column, false)
        bar.findViewById<View>(R.id.touch_back).setOnClickListener { goBack(activity) }
        column.addView(bar)

        column.addView(
            root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        content.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * Sistem GERİ'siyle aynı yol. `onBackPressedDispatcher` değil, doğrudan
     * `onBackPressed()`: NAS ve müzik gezginleri "bir üst klasöre çık" mantığını
     * bu metodu geçersiz kılarak yapıyor; dispatcher'a gidilirse o mantık
     * atlanır ve ekran doğrudan kapanırdı.
     */
    @Suppress("DEPRECATION")
    fun goBack(activity: Activity) {
        activity.onBackPressed()
    }
}
