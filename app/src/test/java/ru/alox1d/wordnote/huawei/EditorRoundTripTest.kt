package ru.alox1d.wordnote.huawei

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.EditText
import com.github.irshulx.Editor
import com.github.irshulx.models.EditorTextStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditorRoundTripTest {
    private fun withEditor(block: (Editor) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_WordNote)
        controller.setup().use {
            val root = controller.get().layoutInflater.inflate(R.layout.dialog_task, null)
            controller.get().setContentView(root)
            block(root.findViewById(R.id.editor_dialogDescription))
        }
    }

    private fun inputs(editor: Editor): List<EditText> =
        (0 until editor.childCount).map { editor.getChildAt(it) as EditText }

    private fun restore(editor: Editor) {
        val saved = editor.contentAsSerialized
        editor.render(editor.getContentDeserialized(saved))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertCombined(editor: Editor) {
        assertTrue(editor.content.nodes.single().contentStyles.contains(EditorTextStyle.BOLDITALIC))
        assertEquals(Typeface.BOLD_ITALIC, inputs(editor).single().typeface.style)
        assertEquals("Formatted paragraph", inputs(editor).single().text.toString())
    }

    @Test fun toolbarBoldItalicInEitherOrderSurvivesRepeatedRestore() = withEditor { editor ->
        for (order in listOf(
            listOf(EditorTextStyle.BOLD, EditorTextStyle.ITALIC),
            listOf(EditorTextStyle.ITALIC, EditorTextStyle.BOLD)
        )) {
            editor.removeAllViews()
            editor.render("<p>Formatted paragraph</p>")
            editor.activeView = inputs(editor).single()
            order.forEach(editor::updateTextStyle)
            assertCombined(editor)
            repeat(2) {
                restore(editor)
                assertCombined(editor)
            }
        }
    }

    @Test fun headerCombinedFormattingAndIndentSurviveRestore() = withEditor { editor ->
        editor.render("<h2>Formatted paragraph</h2>")
        editor.activeView = inputs(editor).single()
        editor.updateTextStyle(EditorTextStyle.BOLD)
        editor.updateTextStyle(EditorTextStyle.ITALIC)
        editor.updateTextStyle(EditorTextStyle.INDENT)
        val originalSize = inputs(editor).single().textSize
        restore(editor)
        assertCombined(editor)
        val styles = editor.content.nodes.single().contentStyles
        assertTrue(styles.contains(EditorTextStyle.H2))
        assertTrue(styles.contains(EditorTextStyle.INDENT))
        assertEquals(originalSize, inputs(editor).single().textSize, 0f)
        assertTrue(inputs(editor).single().paddingLeft > 0)
    }

    @Test fun restoredCombinedFormattingCanToggleEitherAttribute() = withEditor { editor ->
        for ((toggle, remaining, expectedTypeface) in listOf(
            Triple(EditorTextStyle.BOLD, EditorTextStyle.ITALIC, Typeface.ITALIC),
            Triple(EditorTextStyle.ITALIC, EditorTextStyle.BOLD, Typeface.BOLD)
        )) {
            editor.removeAllViews()
            editor.render("<p>Formatted paragraph</p>")
            editor.activeView = inputs(editor).single()
            editor.updateTextStyle(EditorTextStyle.BOLD)
            editor.updateTextStyle(EditorTextStyle.ITALIC)
            restore(editor)
            editor.activeView = inputs(editor).single()
            editor.updateTextStyle(toggle)
            assertEquals(expectedTypeface, inputs(editor).single().typeface.style)
            val styles = editor.content.nodes.single().contentStyles
            assertTrue(styles.contains(remaining))
            assertFalse(styles.contains(EditorTextStyle.BOLDITALIC))
            restore(editor)
            assertEquals(expectedTypeface, inputs(editor).single().typeface.style)
        }
    }

    @Test fun combinedStyleCommandTogglesBothAttributesTogether() = withEditor { editor ->
        editor.render("<p>Formatted paragraph</p>")
        editor.activeView = inputs(editor).single()
        editor.updateTextStyle(EditorTextStyle.BOLD)
        editor.updateTextStyle(EditorTextStyle.BOLDITALIC)
        assertCombined(editor)
        assertFalse(editor.content.nodes.single().contentStyles.contains(EditorTextStyle.BOLD))
        editor.updateTextStyle(EditorTextStyle.BOLDITALIC)
        assertEquals(Typeface.NORMAL, inputs(editor).single().typeface.style)
        assertFalse(editor.content.nodes.single().contentStyles.contains(EditorTextStyle.BOLDITALIC))
    }

    @Test fun multilinePasteKeepsLiteralMarkupAndEntitiesOnEveryLine() = withEditor { editor ->
        editor.render("<p></p>")
        inputs(editor).single().editableText.append("<first> &amp;\n<tag> &amp; literal\nlast &lt; value")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("<first> &amp;", "<tag> &amp; literal", "last &lt; value"),
            inputs(editor).map { it.text.toString() })
        restore(editor)
        assertEquals(listOf("<first> &amp;", "<tag> &amp; literal", "last &lt; value"),
            inputs(editor).map { it.text.toString() })
    }

    @Test fun multilinePasteKeepsLeadingRepeatedAndTrailingLineBreaks() = withEditor { editor ->
        editor.render("<p></p>")
        inputs(editor).single().editableText.append("\nfirst\n\nlast\n")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("", "first", "", "last", ""), inputs(editor).map { it.text.toString() })
        restore(editor)
        assertEquals(listOf("", "first", "", "last", ""), inputs(editor).map { it.text.toString() })
    }

    @Test fun pastedSpansSurviveParagraphSplitAndRestore() = withEditor { editor ->
        editor.render("<p></p>")
        val pasted = SpannableString("bold\nitalic <tag> &amp;")
        pasted.setSpan(StyleSpan(Typeface.BOLD), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        pasted.setSpan(StyleSpan(Typeface.ITALIC), 5, pasted.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        inputs(editor).single().editableText.append(pasted)
        shadowOf(Looper.getMainLooper()).idle()
        repeat(2) { pass ->
            val lines = inputs(editor)
            assertEquals(listOf("bold", "italic <tag> &amp;"), lines.map { it.text.toString() })
            for ((line, expected) in lines.zip(listOf(Typeface.BOLD, Typeface.ITALIC))) {
                val spans = line.text.getSpans(0, line.length(), StyleSpan::class.java)
                assertTrue("Formatting missing after split/restore $pass", spans.any {
                    it.style == expected && line.text.getSpanStart(it) == 0 && line.text.getSpanEnd(it) == line.length()
                })
            }
            if (pass == 0) restore(editor)
        }
    }

    @Test fun multilineInsertionInMiddleRetainsExistingSuffix() = withEditor { editor ->
        editor.render("<p>before after</p>")
        inputs(editor).single().editableText.insert(7, "one\n<tag> &amp; ")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("before one", "<tag> &amp; after"), inputs(editor).map { it.text.toString() })
    }
}
