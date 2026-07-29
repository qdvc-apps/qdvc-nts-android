package qdvc.notetoself.android.app.data

import android.content.Context
import org.json.JSONObject
import qdvc.notetoself.android.app.model.ThemeSpec

/** Loads JSON themes from assets/themes. Malformed files are skipped (B5). */
class ThemeRepository(private val context: Context) {

    private var cache: List<ThemeSpec>? = null

    fun all(): List<ThemeSpec> {
        cache?.let { return it }
        val am = context.assets
        val files = runCatching { am.list("themes") }.getOrNull().orEmpty()
        val specs = files.filter { it.endsWith(".json") }.mapNotNull { file ->
            runCatching {
                val text = am.open("themes/$file").bufferedReader().use { it.readText() }
                val o = JSONObject(text)
                val colorsObj = o.getJSONObject("colors")
                val colors = colorsObj.keys().asSequence()
                    .associateWith { colorsObj.getString(it) }
                ThemeSpec(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    dark = o.getBoolean("dark"),
                    colors = colors,
                )
            }.getOrNull()
        }.sortedBy { it.name }
        cache = specs
        return specs
    }

    fun light(): List<ThemeSpec> = all().filter { !it.dark }
    fun dark(): List<ThemeSpec> = all().filter { it.dark }

    fun byId(id: String, fallbackDark: Boolean): ThemeSpec =
        all().firstOrNull { it.id == id }
            ?: all().firstOrNull { it.dark == fallbackDark }
            ?: all().first()
}
