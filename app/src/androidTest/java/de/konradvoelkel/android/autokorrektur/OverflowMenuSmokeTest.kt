package de.konradvoelkel.android.autokorrektur

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.konradvoelkel.android.autokorrektur.shared.UnlockedDeviceRule
import de.konradvoelkel.android.autokorrektur.telemetry.Telemetry
import de.konradvoelkel.android.autokorrektur.ui.compose.DialogTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens every entry of the options menu and walks what it opens.
 *
 * Why this exists: on 2026-09-28 the published APK met a phone for the first time and died within
 * a minute. `about_dialog_content` carried an unescaped `%`, so `getString(id, versionName)` threw
 * `UnknownFormatConversionException` the moment "About & Licenses" was tapped (`a7f59c5`). Nothing
 * caught it, because no automated test had ever opened the menu — a gap the sibling projects share
 * (PLAYBOOK §3.7). A unit test over the string resources cannot catch the whole class of these:
 * the crash is in the *path*, not only in the text, and a format bug present in both locales
 * agrees with itself perfectly in any parity check.
 *
 * **Half Espresso, half Compose, on purpose.** Since the Compose migration's step 2 the toolbar
 * and its overflow menu are still Views while the dialogs they open are Composables, so this suite
 * drives both: `createAndroidComposeRule` hosts the activity and gives Espresso and the Compose
 * semantics tree at once. Assertions go through `DialogTags` rather than visible wording, which
 * changes with the locale and with every copy fix.
 *
 * [overflowMenu_hasExactlyTheEntriesThisSuiteCovers] is the guard that keeps it honest — add a
 * third menu item and this suite fails until it is walked here too, rather than silently going on
 * testing two of three.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OverflowMenuSmokeTest {

    /** Espresso cannot drive a locked screen; fail with that reason, not NoActivityResumedException. */
    @get:Rule(order = 0)
    val unlockedDevice = UnlockedDeviceRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        // The diagnostics test switches recording on, which writes a real events file into the
        // app's private storage. Leave the device as the suite found it.
        Telemetry.setEnabled(false)
        Telemetry.clear()
    }

    /**
     * Fails when someone adds a menu entry without extending this suite. Without it, "every entry
     * is covered" quietly becomes "the two entries that existed when this was written are covered".
     */
    @Test
    fun overflowMenu_hasExactlyTheEntriesThisSuiteCovers() {
        composeRule.activityRule.scenario.onActivity { activity ->
            // The toolbar's live menu, as the user's tap would find it — not a fresh inflation
            // into a detached menu, which would pass even if the activity never installed it.
            val menu = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).menu
            val ids = (0 until menu.size()).map { menu.getItem(it).itemId }.toSet()
            assertEquals(
                "menu_main.xml has entries this smoke test does not open — add them below",
                setOf(R.id.action_diagnostics, R.id.action_about),
                ids
            )
        }
    }

    /**
     * The direct regression for `a7f59c5`: the dialog body is a formatted string, and building it
     * is what threw. Reaching the assertion at all means `getString(id, args)` survived.
     */
    @Test
    fun aboutEntry_opensAndRendersItsFormattedBody() {
        openMenuEntry(R.string.action_about)

        composeRule.onNodeWithTag(DialogTags.ABOUT).assertIsDisplayed()
        // A fragment of the body that is locale-independent and carries no placeholder, so the
        // assertion is about the dialog having content rather than about any one wording.
        composeRule.onNodeWithText("GNU AGPLv3", substring = true).assertIsDisplayed()
    }

    @Test
    fun diagnosticsEntry_opensWithRecordingOffAndNothingRecorded() {
        openMenuEntry(R.string.action_diagnostics)

        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS).assertIsDisplayed()
        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_EVENTS)
            .assertTextEquals(string(R.string.diagnostics_events_empty))
    }

    /**
     * Usability run 002, UX-11. Deleting used to leave the switch on, so `Telemetry.clear()` minted
     * a fresh install id and wrote a new `session_start` immediately — the dialog then showed a
     * non-zero count and a brand-new id seconds after confirming "delete everything", which reads
     * as the deletion having failed. Recording must end up **off**, and the app must say so.
     */
    @Test
    fun diagnosticsDelete_turnsRecordingOffAndAcknowledgesIt() {
        openMenuEntry(R.string.action_diagnostics)

        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_SWITCH).performClick()
        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_SWITCH).assertIsOn()

        // performScrollTo() first: the dialog body is height-capped and scrolls, so Delete sits
        // below the fold on a small screen exactly as it does for a person (PLAYBOOK §3.7).
        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_DELETE).performScrollTo().performClick()
        composeRule.onNodeWithTag(DialogTags.DELETE_CONFIRM).performClick()

        composeRule.onNodeWithTag(DialogTags.DELETED_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.diagnostics_deleted)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.btn_ok)).performClick()

        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(DialogTags.DIAGNOSTICS_EVENTS)
            .assertTextEquals(string(R.string.diagnostics_events_empty))
    }

    /** The menu is still a View, so it is opened with Espresso; what it opens is Compose. */
    private fun openMenuEntry(titleRes: Int) {
        openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        onView(withText(titleRes)).perform(click())
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
