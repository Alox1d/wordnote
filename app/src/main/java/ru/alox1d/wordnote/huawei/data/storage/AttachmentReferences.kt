package ru.alox1d.wordnote.huawei.data.storage

import android.graphics.Color
import android.text.Html
import com.google.gson.JsonObject
import com.github.irshulx.models.EditorTextStyle
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import ru.alox1d.wordnote.huawei.domain.model.ModelTask

/** Only image source fields are rewritten; captions and other rich-text attributes survive. */
object AttachmentReferences {
    private val imageSource = Regex("""(<img\b[^>]*?\bsrc\s*=\s*)(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

    fun isRemote(path: String) = path.startsWith("https://", true) || path.startsWith("http://", true)

    fun paths(task: ModelTask): Set<String> = linkedSetOf<String>().also { paths ->
        map(task) { path -> if (!isRemote(path)) paths += path; path }
    }

    fun map(task: ModelTask, transform: (String) -> String): ModelTask {
        val original = task.description.orEmpty()
        var description = original
        run {
            val json = validated(original)
            val nodes = json.getAsJsonArray("nodes")
            var changed = false
            fun visit(node: JsonElement, depth: Int) {
                require(depth <= 64 && node.isJsonObject) { "Invalid note structure" }
                val obj = node.asJsonObject
                if (obj.get("type")?.asString == "img") {
                    val content = obj.getAsJsonArray("content")
                    require(content != null && content.size() > 0 && content[0].isJsonPrimitive && content[0].asJsonPrimitive.isString) { "Invalid image reference" }
                    val path = content[0].asString
                    require(path.isNotBlank()) { "Invalid image reference" }
                    val replacement = transform(path)
                    if (replacement != path) { content.set(0, com.google.gson.JsonPrimitive(replacement)); changed = true }
                }
                val children = obj.get("childs")
                if (children != null && !children.isJsonNull) {
                    require(children.isJsonArray) { "Invalid note structure" }
                    children.asJsonArray.forEach { visit(it, depth + 1) }
                }
            }
            nodes.asJsonArray.forEach { visit(it, 0) }
            if (changed) description = json.toString()
        }
        val html = imageSource.replace(task.descriptionHTML.orEmpty()) { match ->
            val source = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]!!.value
            @Suppress("DEPRECATION") val decoded = Html.fromHtml(source).toString()
            val replacement = transform(decoded)
            if (replacement == decoded) match.value else match.groupValues[1] + "\"" + replacement.replace("&", "&amp;").replace("\"", "&quot;") + "\""
        }
        return ModelTask(task.title, description, html, task.date, task.priority, task.status, task.timeStamp, task.isHidden)
    }

    fun validate(task: ModelTask) { validated(requireNotNull(task.description)) }

    /** Check the actual native editor contract before a restore can replace any rows. */
    private fun validated(description: String): JsonObject {
        checkDepth(description)
        val root = JsonParser().parse(description)
        require(root.isJsonObject) { "Invalid editor document" }
        val nodes = root.asJsonObject.get("nodes")
        require(nodes != null && nodes.isJsonArray) { "Invalid editor document" }
        var count = 0
        val styles = EditorTextStyle.values().map { it.name }.toSet()
        fun string(value: JsonElement?) = value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString
        fun visit(value: JsonElement, allowed: Set<String>, depth: Int) {
            require(depth <= 64 && ++count <= 100_000 && value.isJsonObject) { "Invalid editor structure" }
            val node = value.asJsonObject
            require(string(node.get("type")) && node.get("type").asString in allowed) { "Unsupported editor block" }
            val type = node.get("type").asString
            val content = node.get("content")
            require(content != null && content.isJsonArray && content.asJsonArray.all { string(it) }) { "Invalid editor text" }
            val children = node.get("childs")
            require(children == null || children.isJsonNull || children.isJsonArray) { "Invalid editor children" }
            val childNodes = if (children == null || children.isJsonNull) com.google.gson.JsonArray() else children.asJsonArray
            if (type in setOf("INPUT", "IMG_SUB", "UL_LI", "OL_LI")) {
                require(content.asJsonArray.size() >= 1 && childNodes.size() == 0) { "Invalid text block" }
                val textStyles = node.get("contentStyles")
                require(textStyles != null && textStyles.isJsonArray && textStyles.asJsonArray.all { string(it) && it.asString in styles }) { "Invalid text styles" }
                val settings = node.get("textSettings")
                require(settings == null || settings.isJsonNull || settings.isJsonObject) { "Invalid text settings" }
                if (type == "INPUT" || type == "IMG_SUB") require(settings != null && settings.isJsonObject) { "Missing text settings" }
                if (settings != null && settings.isJsonObject) {
                    val color = settings.asJsonObject.get("textColor")
                    if (color != null && !color.isJsonNull) {
                        require(string(color)) { "Invalid text color" }
                        Color.parseColor(color.asString)
                    } else require(type == "INPUT" || type == "IMG_SUB") { "Missing list color" }
                }
            }
            when (type) {
                "img" -> {
                    require(content.asJsonArray.size() >= 1 && content.asJsonArray[0].asString.isNotBlank() && childNodes.size() == 1) { "Invalid image block" }
                    visit(childNodes[0], setOf("IMG_SUB"), depth + 1)
                }
                "ul", "ol" -> childNodes.forEach { visit(it, setOf(if (type == "ul") "UL_LI" else "OL_LI"), depth + 1) }
                "map" -> {
                    require(content.asJsonArray.size() >= 2 && childNodes.size() == 0) { "Invalid map block" }
                    val coordinates = content.asJsonArray[0].asString.split(",")
                    require(coordinates.size == 2) { "Invalid map coordinates" }
                    val latitude = coordinates[0].trim().toDoubleOrNull()
                    val longitude = coordinates[1].trim().toDoubleOrNull()
                    require(latitude != null && latitude.isFinite() && latitude in -90.0..90.0 &&
                        longitude != null && longitude.isFinite() && longitude in -180.0..180.0) { "Invalid map coordinates" }
                }
                else -> require(childNodes.size() == 0) { "Unexpected editor children" }
            }
        }
        // Macros require app-specific renderer hooks which WordNote does not install.
        nodes.asJsonArray.forEach { visit(it, setOf("INPUT", "hr", "img", "ul", "ol", "map"), 0) }
        return root.asJsonObject
    }

    // Bound nesting before invoking the JSON parser on an untrusted restored note.
    private fun checkDepth(value: String) {
        require(value.length <= 8 * 1024 * 1024) { "Note is too large" }
        var depth = 0
        var quoted = false
        var escaped = false
        value.forEach { ch ->
            if (quoted) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 128) { "Note nesting is too deep" } }
                '}', ']' -> depth--
            }
        }
    }
}
