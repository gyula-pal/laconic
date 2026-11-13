package cc.gyula.wave.data

import cc.gyula.wave.core.AppBox
import cc.gyula.wave.core.BoxSplit
import cc.gyula.wave.core.BoxTree
import org.json.JSONArray
import org.json.JSONObject

/** A box is `{"box":3,"apps":["k1","k2"]}` (plus `"name":"Money"` when it has one); a split is `{"split":"h"|"v","ratio":0.5,"first":{..},"second":{..}}`. */
fun boxesToJson(tree: BoxTree): String = toObject(tree).toString()

private fun toObject(tree: BoxTree): JSONObject = when (tree) {
    is AppBox -> JSONObject().put("box", tree.id).put("apps", JSONArray(tree.apps)).apply {
        if (tree.name.isNotBlank()) put("name", tree.name)
    }
    is BoxSplit -> JSONObject()
        .put("split", if (tree.sideBySide) "h" else "v")
        .put("ratio", tree.ratio.toDouble())
        .put("first", toObject(tree.first))
        .put("second", toObject(tree.second))
}

/** The saved tree, or null when there is none or it is damaged: home then starts over with one box. */
fun boxesFromJson(json: String?): BoxTree? =
    if (json.isNullOrBlank()) null else runCatching { fromObject(JSONObject(json)) }.getOrNull()

// Throws on anything malformed; the caller turns that into null.
private fun fromObject(o: JSONObject): BoxTree =
    if (o.has("box")) {
        val apps = o.getJSONArray("apps")
        AppBox(o.getInt("box"), List(apps.length()) { apps.getString(it) }, o.optString("name", ""))
    } else {
        val ratio = o.getDouble("ratio").toFloat()
        require(ratio in 0f..1f)
        BoxSplit(
            sideBySide = when (o.getString("split")) { "h" -> true; "v" -> false; else -> error("split") },
            ratio = ratio,
            first = fromObject(o.getJSONObject("first")),
            second = fromObject(o.getJSONObject("second")),
        )
    }
