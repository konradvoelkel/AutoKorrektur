package de.konradvoelkel.android.autokorrektur

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Enforces the app's localization contract: **`values/strings.xml` is English, complete, and the
 * only fallback; `values-de/strings.xml` is a complete German override of every translatable
 * entry.** German and English are the two fully supported languages; any other locale gets
 * English, never a mix.
 *
 * ## History
 * Until 2026-09-21 the default file was a German/English mix (51 of 121 entries German) and
 * `values-de`/`values-en` each covered only half the key space, so German and English devices
 * happened to look right while any third locale (fr, es, pl, …) rendered "Select Image" next to
 * "ZURÜCKSETZEN". That was fixed by folding `values-en` into the default file and completing
 * `values-de`; this test used to be a ratchet allow-listing the 51 offenders and is now strict.
 *
 * ## What it checks
 * 1. Every `<string>` / `<string-array>` / `<plurals>` in the default file has a `values-de` entry and vice
 *    versa (a key only in `values-de` would crash resolution on every other locale; a key only in
 *    default would silently show English on German devices). `translatable="false"` entries are
 *    exempt from needing a German counterpart.
 * 2. A German entry that is byte-identical to the English one is an untranslated copy-paste,
 *    unless it is a proper noun, number or format-only string listed in [identicalInBothLanguages]
 *    — that list should only ever hold strings that genuinely read the same in both languages.
 * 3. Format placeholders (`%1$d`, `%2$s`, …) match between the two languages, so a translation
 *    cannot crash `getString(res, args)` at runtime.
 * 4. No `values-en` directory exists any more (its content would silently shadow the default
 *    file for English devices and drift again).
 * 5. Every string that carries a real format specifier is a *valid* format string, so
 *    `getString(res, args)` cannot throw on a literal `%`. This shipped once: `about_dialog_content`
 *    ended in "100% On-Device", `String.format` read `% O` as the conversion `'O'`, and tapping
 *    "About & Licenses" killed the app in both languages (2026-09-28, found on a phone). Check 3
 *    could not catch it — both locales had the *same* bug, so their placeholders agreed perfectly.
 */
class StringResourceLocalizationTest {

    /** Entries that legitimately read the same in German and English. */
    private val identicalInBothLanguages = setOf(
        "app_name", "first_fragment_label", "start", "start_btn", "label_original",
        "loading_status", "video_progress_zero", "btn_share_instagram", "engine_cloud",
        "export_ratio_story", "export_title", "diagnostics_install_id_label",
        // "OK" is "OK" in both languages; it exists as an app string at all so that dialog
        // buttons follow the app's language rather than the device's (UX-14).
        "btn_ok",
        // string-arrays whose items are model names / megapixel counts
        "yolo_model_options",
    )

    /**
     * Locates `app/src/main/res` regardless of whether the JVM test process's working directory
     * is the `app/` module (the Gradle default) or the repo root (some IDE run configurations).
     */
    private fun resDir(): File {
        val fromModuleDir = File("src/main/res")
        if (fromModuleDir.isDirectory) return fromModuleDir
        val fromRepoRoot = File("app/src/main/res")
        if (fromRepoRoot.isDirectory) return fromRepoRoot
        throw IllegalStateException(
            "Could not locate app/src/main/res from working directory " +
                "${File(".").absolutePath} — run this test via Gradle (./gradlew :app:testFullDebugUnitTest)."
        )
    }

    private data class Entry(val value: String, val translatable: Boolean)

    private fun parseStrings(file: File): Map<String, Entry> {
        assertTrue("Expected strings.xml at ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = mutableMapOf<String, Entry>()
        for (tag in listOf("string", "string-array", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val translatable = el.getAttribute("translatable") != "false"
                val value = if (tag == "string") el.textContent else {
                    val items = el.getElementsByTagName("item")
                    (0 until items.length).joinToString(" ") { items.item(it).textContent }
                }
                result[el.getAttribute("name")] = Entry(value, translatable)
            }
        }
        return result
    }

    private val placeholderRegex = Regex("""%(\d+\$)?[sdf]""")

    @Test
    fun `values-de covers every translatable default entry and nothing more`() {
        val res = resDir()
        val default = parseStrings(File(res, "values/strings.xml"))
        val de = parseStrings(File(res, "values-de/strings.xml"))

        val missingInDe = default.filterValues { it.translatable }.keys - de.keys
        val onlyInDe = de.keys - default.keys

        assertTrue(
            "These default (English) entries have no German translation in values-de/strings.xml, " +
                "so German devices would show English for them: $missingInDe",
            missingInDe.isEmpty()
        )
        assertTrue(
            "These keys exist only in values-de/strings.xml and not in the default file, which " +
                "would crash resource resolution on every non-German locale: $onlyInDe",
            onlyInDe.isEmpty()
        )
    }

    @Test
    fun `German entries are actually translated`() {
        val res = resDir()
        val default = parseStrings(File(res, "values/strings.xml"))
        val de = parseStrings(File(res, "values-de/strings.xml"))

        val untranslated = (default.keys intersect de.keys)
            .filter { default.getValue(it).value == de.getValue(it).value }
            .toSet() - identicalInBothLanguages
        val staleAllowlist = identicalInBothLanguages.filter { key ->
            key in default && key in de && default.getValue(key).value != de.getValue(key).value
        }

        assertTrue(
            "These values-de entries are byte-identical to the English default — untranslated? " +
                "Translate them, or add them to identicalInBothLanguages if they really read the " +
                "same in both languages: $untranslated",
            untranslated.isEmpty()
        )
        assertTrue(
            "These keys are listed in identicalInBothLanguages but differ between the languages " +
                "now — remove them from the list: $staleAllowlist",
            staleAllowlist.isEmpty()
        )
    }

    @Test
    fun `format placeholders agree between English and German`() {
        val res = resDir()
        val default = parseStrings(File(res, "values/strings.xml"))
        val de = parseStrings(File(res, "values-de/strings.xml"))

        val mismatched = (default.keys intersect de.keys).filter { key ->
            val en = placeholderRegex.findAll(default.getValue(key).value).map { it.value }.sorted().toList()
            val ge = placeholderRegex.findAll(de.getValue(key).value).map { it.value }.sorted().toList()
            en != ge
        }
        assertTrue(
            "Format placeholders differ between values/ and values-de/ for: $mismatched — " +
                "getString(res, args) would throw or print garbage in one language",
            mismatched.isEmpty()
        )
    }

    /**
     * Java's conversion characters, from `%[index$][flags][width][.precision]conversion`. Anything
     * else after a `%` makes `String.format` throw `UnknownFormatConversionException` at runtime.
     * Note `o` (octal) is valid and `O` is not — that single letter was the 2026-09-28 crash.
     */
    private val validConversions = "bBhHsScCdoxXeEfgGaAtTn".toSet()

    // The dollar in "%1$s" must reach the regex escaped. Writing it as a bare ${'$'} makes it an
    // end-of-line anchor instead of a literal, "%1$s" then reads as the invalid conversion '$',
    // sawSpecifier below never flips, and every real finding is swallowed — this test passed
    // against the very crash it was written for until `detectorRecognisesKnownGoodAndBadStrings`
    // was added to hold it honest.
    private val specifierRegex =
        Regex("%(\\d+\\${'$'})?([-#+ 0,(]*)(\\d+)?(\\.\\d+)?(.)", RegexOption.DOT_MATCHES_ALL)

    /** One complaint per malformed `%` in [value]; empty if the string is sound or never formatted. */
    private fun formatProblems(value: String): List<String> {
        val problems = mutableListOf<String>()
        var sawSpecifier = false
        var i = 0
        while (i < value.length) {
            if (value[i] != '%') {
                i++
                continue
            }
            if (value.startsWith("%%", i)) {
                i += 2
                continue
            }
            val match = specifierRegex.matchAt(value, i)
            val conversion = match?.groupValues?.get(5)?.firstOrNull()
            if (match != null && conversion in validConversions) {
                sawSpecifier = true
                i = match.range.last + 1
            } else {
                val around = value.substring(maxOf(0, i - 18), minOf(value.length, i + 18))
                problems += "invalid conversion '${conversion ?: "<end of string>"}' near " +
                    "\"…${around.replace("\n", "\\n")}…\""
                i++
            }
        }
        // A string with no specifier at all never reaches String.format (e.g. video_progress_zero,
        // which is only a layout's android:text="0%"), so a bare % there is harmless — and
        // escaping it would render a literal "%%" on screen.
        return if (sawSpecifier) problems else emptyList()
    }

    /**
     * Holds the detector itself honest. A checker that silently answers "nothing wrong" is worse
     * than no checker, and this one did exactly that on its first draft.
     */
    @Test
    fun `detector recognises known good and bad strings`() {
        // Valid: a positional argument plus a properly escaped literal percent.
        assertTrue(
            "A sound format string must produce no complaints",
            formatProblems("Version: %1\$s — 100%% on-device").isEmpty()
        )
        // The real crash: %1$s marks the string as formatted, "% O" is then conversion 'O'.
        val real = formatProblems("Version: %1\$s\n\n100% On-Device AI processing.")
        assertTrue(
            "The 2026-09-28 crash string must be reported, got: $real",
            real.any { it.contains("'O'") }
        )
        // Not formatted at all (a layout's android:text): a bare % is fine and must not be flagged.
        assertTrue(
            "A string with no specifier is never formatted and must not be flagged",
            formatProblems("0%").isEmpty()
        )
    }

    @Test
    fun `formatted strings are valid format strings in both languages`() {
        val res = resDir()
        val broken = mutableListOf<String>()
        for (dir in listOf("values", "values-de")) {
            for ((key, entry) in parseStrings(File(res, "$dir/strings.xml"))) {
                formatProblems(entry.value).forEach { broken += "$dir/strings.xml [$key]: $it" }
            }
        }
        assertTrue(
            "These strings carry a format placeholder, so getString(res, args) runs String.format " +
                "over the whole text — and these would throw UnknownFormatConversionException the " +
                "moment that screen opens. Escape a literal percent as %%:\n" +
                broken.joinToString("\n"),
            broken.isEmpty()
        )
    }

    @Test
    fun `no values-en override exists`() {
        val res = resDir()
        val en = File(res, "values-en")
        assertTrue(
            "values-en/ is back. The default values/strings.xml IS the English translation; a " +
                "values-en override would shadow it for English devices and start drifting again.",
            !en.exists()
        )
    }
}
