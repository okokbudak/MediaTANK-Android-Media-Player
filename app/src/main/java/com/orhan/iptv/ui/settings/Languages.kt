package com.orhan.iptv.ui.settings

/**
 * Ses ve altyazı için seçilebilen diller.
 *
 * Kodlar ISO 639-1; oynatıcı içerikteki kanal dilleriyle bunları eşleştiriyor.
 * Boş kod "seçme / kapalı" anlamına gelir.
 */
object Languages {

    data class Entry(val code: String, val label: String)

    val AUDIO = listOf(
        Entry("", "Otomatik (ilk kanal)"),
        Entry("tr", "Türkçe"),
        Entry("en", "İngilizce"),
        Entry("de", "Almanca"),
        Entry("fr", "Fransızca"),
        Entry("es", "İspanyolca"),
        Entry("ar", "Arapça"),
        Entry("ru", "Rusça")
    )

    val SUBTITLE = listOf(
        Entry("", "Kapalı"),
        Entry("tr", "Türkçe"),
        Entry("en", "İngilizce"),
        Entry("de", "Almanca"),
        Entry("fr", "Fransızca"),
        Entry("es", "İspanyolca"),
        Entry("ar", "Arapça"),
        Entry("ru", "Rusça")
    )

    fun labelFor(list: List<Entry>, code: String): String =
        list.firstOrNull { it.code == code }?.label ?: code

    /**
     * Bir kanal etiketinin/dil kodunun istenen dile karşılık gelip gelmediği.
     *
     * Yayınlar dili bazen kod ("tur", "tr"), bazen ad ("Turkish", "Türkçe")
     * olarak veriyor; her iki biçimi de tanımak gerekiyor.
     */
    fun matches(code: String, trackLanguage: String?, trackLabel: String?): Boolean {
        if (code.isBlank()) return false
        val needles = ALIASES[code] ?: setOf(code)
        val haystack = "${trackLanguage.orEmpty()} ${trackLabel.orEmpty()}".lowercase()
        return needles.any { haystack.contains(it) }
    }

    private val ALIASES = mapOf(
        "tr" to setOf("tr", "tur", "turkish", "türkçe", "turkce"),
        "en" to setOf("en", "eng", "english", "ingilizce"),
        "de" to setOf("de", "ger", "deu", "german", "almanca", "deutsch"),
        "fr" to setOf("fr", "fre", "fra", "french", "fransızca"),
        "es" to setOf("es", "spa", "spanish", "ispanyolca", "español"),
        "ar" to setOf("ar", "ara", "arabic", "arapça"),
        "ru" to setOf("ru", "rus", "russian", "rusça")
    )
}
