package de.konradvoelkel.android.autokorrektur

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.konradvoelkel.android.autokorrektur.telemetry.Telemetry
import org.hamcrest.CoreMatchers.allOf
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
 * So the point of this suite is coverage of the surface rather than depth: open each entry, and
 * assert the thing it opens actually rendered. Any exception on the way is a failure by itself.
 *
 * [overflowMenu_hasExactlyTheEntriesThisSuiteCovers] is the guard that keeps it honest — add a
 * third menu item and this suite fails until it is walked here too, rather than silently going on
 * testing two of three.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OverflowMenuSmokeTest {

    /** Espresso cannot drive a locked screen; fail with that reason, not NoActivityResumedException. */
    @get:Rule
    val unlockedDevice = de.konradvoelkel.android.autokorrektur.shared.UnlockedDeviceRule()

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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
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
    }

    /**
     * The direct regression for `a7f59c5`: the dialog body is a formatted string, and building it
     * is what threw. Reaching the assertion at all means `getString(id, args)` survived.
     */
    @Test
    fun aboutEntry_opensAndRendersItsFormattedBody() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
            onView(withText(R.string.action_about)).perform(click())

            onView(withText(R.string.about_dialog_title)).inRoot(isDialog())
                .check(matches(isDisplayed()))
            // A fragment of the body that carries no placeholder and no `%`, so the assertion is
            // about the dialog having content rather than about any one wording.
            onView(withText(containsLicenceLine())).inRoot(isDialog())
                .check(matches(isDisplayed()))

            onView(allOf(withText(R.string.btn_ok), isAssignableFrom(Button::class.java)))
                .inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun diagnosticsEntry_opensWithRecordingOffAndNothingRecorded() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            openDiagnostics()

            onView(withId(R.id.switchDiagnostics)).inRoot(isDialog())
                .check(matches(isNotChecked()))
            onView(withId(R.id.tvDiagnosticsEvents)).inRoot(isDialog())
                .check(matches(withText(R.string.diagnostics_events_empty)))

            dismissDialog()
        }
    }

    /**
     * Usability run 002, UX-11. Deleting used to leave the switch on, so `Telemetry.clear()` minted
     * a fresh install id and wrote a new `session_start` immediately — the dialog then showed a
     * non-zero count and a brand-new id seconds after the user confirmed "delete everything", which
     * reads as the deletion having failed. Recording must end up **off**, and the app must say so.
     */
    @Test
    fun diagnosticsDelete_turnsRecordingOffAndAcknowledgesIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            openDiagnostics()

            onView(withId(R.id.switchDiagnostics)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.switchDiagnostics)).inRoot(isDialog()).check(matches(isChecked()))

            // scrollTo() first: the dialog body is a height-capped ScrollView (it used to grow
            // taller than a 720x1280 screen and push the dialog's own buttons off the bottom), so
            // Delete sits below the fold here exactly as it does for a person on this screen.
            onView(withId(R.id.btnDiagnosticsDelete)).inRoot(isDialog())
                .perform(scrollTo(), click())

            // "Delete" is the confirmation dialog's title *and* its positive button — the very
            // ambiguity that made a usability persona mis-tap the title and report the feature as
            // broken. Espresso needs the same disambiguation a person does.
            onView(allOf(withText(R.string.btn_delete), isAssignableFrom(Button::class.java)))
                .inRoot(isDialog()).perform(click())

            onView(withText(R.string.diagnostics_deleted)).inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(allOf(withText(R.string.btn_ok), isAssignableFrom(Button::class.java)))
                .inRoot(isDialog()).perform(click())

            onView(withId(R.id.switchDiagnostics)).inRoot(isDialog())
                .check(matches(isNotChecked()))
            onView(withId(R.id.tvDiagnosticsEvents)).inRoot(isDialog())
                .check(matches(withText(R.string.diagnostics_events_empty)))

            dismissDialog()
        }
    }

    private fun openDiagnostics() {
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
        onView(withText(R.string.action_diagnostics)).perform(click())
        onView(withText(R.string.diagnostics_title)).inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    private fun dismissDialog() {
        onView(allOf(withText(R.string.btn_cancel), isAssignableFrom(Button::class.java)))
            .inRoot(isDialog()).perform(click())
    }

    /** A line of the About body that is locale-independent and placeholder-free. */
    private fun containsLicenceLine() =
        org.hamcrest.Matchers.containsString("GNU AGPLv3")
}
