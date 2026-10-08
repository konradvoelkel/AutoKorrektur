package de.konradvoelkel.android.autokorrektur.shared

import android.app.KeyguardManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Fails an Espresso test immediately, and with a reason, when the device's screen is locked.
 *
 * Behind a keyguard no activity ever reaches the RESUMED stage, so every Espresso interaction
 * dies with `NoActivityResumedException: No activities in stage RESUMED. Did you forget to launch
 * the activity?` — a message that points at the test and says nothing about the device. Without
 * this rule the failure is also *intermittent*, which is worse than being wrong: it depends on
 * whether the phone's screen timeout happens to fire during the run.
 *
 * Found on 2026-10-08, on the first full arm64 walk (FT-01). The Pixel 10 Pro has a 30-second
 * screen timeout and is on wireless debugging, so `stay_on_while_plugged_in` does not apply; a
 * 14-minute suite produced exactly one such failure, in the window where the screen went off.
 * Waking the screen afterwards made it *worse* rather than better — the device is secured, so the
 * screen came back to the lockscreen and all five tests in the class failed identically.
 *
 * This rule does not try to unlock anything: a secured keyguard needs the owner's credential, and
 * a test suite has no business holding one. It converts a confusing flake into a precondition that
 * names itself. Preparing a device for a run is in `TESTING.md` §5.
 *
 * Emulators are normally unlocked, so this rule is invisible there — which is the point: it fires
 * only on the real hardware where it is earned.
 */
class UnlockedDeviceRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val keyguard = context.getSystemService(KeyguardManager::class.java)
                if (keyguard != null && keyguard.isKeyguardLocked) {
                    val secured = keyguard.isKeyguardSecure
                    throw AssertionError(
                        buildString {
                            append("Device screen is locked, so no activity can resume and every ")
                            append("Espresso interaction in ${description.className} would fail with ")
                            append("NoActivityResumedException.\n")
                            append("This is a device precondition, not a test defect.\n")
                            if (secured) {
                                append("The keyguard is secured, so it can only be dismissed by its owner: ")
                                append("unlock the device by hand, then start the run.\n")
                            } else {
                                append("The keyguard is not secured: `adb shell input keyevent KEYCODE_WAKEUP` ")
                                append("then `adb shell wm dismiss-keyguard`.\n")
                            }
                            append("Also raise the screen timeout for the duration of the run, or it will ")
                            append("lock again mid-suite — see TESTING.md §5.")
                        }
                    )
                }
                base.evaluate()
            }
        }
}
