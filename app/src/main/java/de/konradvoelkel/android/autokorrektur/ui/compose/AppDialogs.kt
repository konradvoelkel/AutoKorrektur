package de.konradvoelkel.android.autokorrektur.ui.compose

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.konradvoelkel.android.autokorrektur.BuildConfig
import de.konradvoelkel.android.autokorrektur.R
import de.konradvoelkel.android.autokorrektur.telemetry.Telemetry
import de.konradvoelkel.android.autokorrektur.utils.AppLogger
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test tags for the Compose dialogs. Shared with `OverflowMenuSmokeTest` so the suite does not
 * match on user-visible wording, which changes with the locale and with every copy fix.
 */
object DialogTags {
    const val ABOUT = "about_dialog"
    const val DIAGNOSTICS = "diagnostics_dialog"
    const val DIAGNOSTICS_SWITCH = "diagnostics_switch"
    const val DIAGNOSTICS_DELETE = "diagnostics_delete"
    const val DIAGNOSTICS_EVENTS = "diagnostics_events"
    const val DIAGNOSTICS_SUMMARY = "diagnostics_summary"

    /** "Delete" is the confirm dialog's title *and* its button; tags keep the test off the wording. */
    const val DELETE_CONFIRM = "diagnostics_delete_confirm"
    const val DELETED_NOTICE = "diagnostics_deleted_notice"
}

/** Which global dialog is open. Hoisted into [MainActivity] so the menu can drive it. */
enum class AppDialog { NONE, ABOUT, DIAGNOSTICS }

/**
 * About & Licenses.
 *
 * The body is one formatted string. Building it is what killed the published APK on its first
 * contact with a phone — an unescaped `%` made `getString(id, versionName)` throw
 * `UnknownFormatConversionException` (`a7f59c5`) — so this stays a single resource with a single
 * argument, and `StringResourceLocalizationTest` check 5 plus `OverflowMenuSmokeTest` both guard it.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(DialogTags.ABOUT),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_dialog_title)) },
        text = {
            Text(
                text = stringResource(R.string.about_dialog_content, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        },
    )
}

/**
 * Diagnostics: the opt-in switch, what is recorded, the recorded events in plain words, and
 * Export / Delete.
 *
 * Everything the View version earned in usability run 002 is kept, and one thing gets better.
 * The View dialog had to cap its own body in `setOnShowListener` against
 * `displayMetrics.heightPixels`, because an `AlertDialog` does not scroll the view handed to
 * `setView` and the body had grown taller than a 720x1280 screen — far enough to push the
 * dialog's own Delete button off the bottom. Here that is `heightIn(max = ...)` on a scrolling
 * column, measured by layout instead of patched after the fact.
 */
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxBodyHeight = (configuration.screenHeightDp * 0.5f).dp

    // Read at composition time, not inside the gesture callback: `LocalContext.current` is not
    // invalidated by a Configuration change while `stringResource` is (lint:
    // LocalContextGetResourceValueCall). This app switches language at runtime, so that is not a
    // theoretical case here.
    val clipLabel = stringResource(R.string.diagnostics_title)

    // Telemetry is a process-wide object, not observable state; this counter is what makes the
    // dialog recompose after a switch, a delete, or anything else that changes the store.
    var revision by remember { mutableIntStateOf(0) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var deletedNotice by remember { mutableStateOf(false) }

    val enabled = remember(revision) { Telemetry.isEnabled }
    val eventCount = remember(revision) { Telemetry.eventCount() }
    val sizeKb = remember(revision) { Telemetry.sizeBytes() / 1024.0 }
    val installId = remember(revision) { Telemetry.installId }
    // `configuration` as a second key: readableEvents() reads resources and lint cannot see
    // through the call. Without it the event lines would stay in the old language after a
    // language switch, because remember(revision) alone would not re-run.
    val events = remember(revision, configuration) { readableEvents(context) }

    AlertDialog(
        modifier = Modifier.testTag(DialogTags.DIAGNOSTICS),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnostics_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_switch),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { Telemetry.setEnabled(it); revision++ },
                        modifier = Modifier.testTag(DialogTags.DIAGNOSTICS_SWITCH),
                    )
                }

                Text(
                    text = stringResource(R.string.diagnostics_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Long-press copies the id, which is shown in full: it is a random UUID, so there
                // is nothing to protect by shortening it, and a field tester has to be able to
                // quote it in a bug report (UX-21).
                Text(
                    text = buildString {
                        append(
                            pluralStringResource(
                                R.plurals.diagnostics_event_count, eventCount, eventCount
                            )
                        )
                        append(" · ").append(String.format(Locale.ROOT, "%.1f KB", sizeKb))
                        if (installId != null) {
                            append('\n').append(
                                stringResource(R.string.diagnostics_install_id_label, installId)
                            )
                            append('\n').append(stringResource(R.string.diagnostics_install_id_hint))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(DialogTags.DIAGNOSTICS_SUMMARY)
                        .pointerInput(installId) {
                            detectTapGestures(onLongPress = {
                                val id = installId ?: return@detectTapGestures
                                val clipboard = context
                                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, id))
                                Toast.makeText(
                                    context,
                                    R.string.diagnostics_install_id_copied,
                                    Toast.LENGTH_SHORT
                                ).show()
                            })
                        },
                )

                // Delete lives in the body rather than the button row: three buttons stack on a
                // narrow phone, and a destructive action should not sit shoulder to shoulder with
                // Cancel anyway.
                TextButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.testTag(DialogTags.DIAGNOSTICS_DELETE),
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = stringResource(R.string.diagnostics_events_heading),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (events.isEmpty()) stringResource(R.string.diagnostics_events_empty)
                    else events.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(DialogTags.DIAGNOSTICS_EVENTS),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { exportDiagnostics(context); onDismiss() }) {
                Text(stringResource(R.string.diagnostics_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.diagnostics_delete)) },
            text = { Text(stringResource(R.string.diagnostics_delete_confirm)) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag(DialogTags.DELETE_CONFIRM), onClick = {
                    // Off first, then clear: while recording is on, Telemetry.clear() mints a new
                    // install id and writes a fresh session_start at once, so the dialog came back
                    // showing a non-zero count and a brand-new id seconds after the user confirmed
                    // "delete everything" — which reads as the deletion having failed (UX-11).
                    Telemetry.setEnabled(false)
                    Telemetry.clear()
                    revision++
                    confirmingDelete = false
                    deletedNotice = true
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    if (deletedNotice) {
        AlertDialog(
            modifier = Modifier.testTag(DialogTags.DELETED_NOTICE),
            onDismissRequest = { deletedNotice = false },
            title = { Text(stringResource(R.string.diagnostics_deleted_title)) },
            text = { Text(stringResource(R.string.diagnostics_deleted)) },
            confirmButton = {
                TextButton(onClick = { deletedNotice = false }) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
        )
    }
}

/**
 * Turns the stored JSON Lines into one human sentence each, newest first. Until usability run 002
 * (UX-12) the only way to see what had been recorded was to export a `.jsonl` to another app,
 * which the privacy-conscious non-developer the feature exists for cannot read.
 */
private fun readableEvents(context: Context): List<String> {
    val timeFormat = SimpleDateFormat(
        context.getString(R.string.diagnostics_event_time_format), Locale.getDefault()
    )
    return Telemetry.readLines().asReversed().mapNotNull { line ->
        val json = try {
            JSONObject(line)
        } catch (e: Exception) {
            AppLogger.warn("Diagnostics: unreadable line (${e.javaClass.simpleName})")
            return@mapNotNull null
        }
        val name = json.optString("event")
        val stamp = json.optLong("t", 0L).takeIf { it > 0L }?.let { timeFormat.format(Date(it)) }
        val body = when (name) {
            "session_start" -> context.getString(
                R.string.diagnostics_event_session_start, json.optString("app_version", "?")
            )

            "pipeline_run" -> {
                val detections = json.optInt("detections", 0)
                val vehicles = context.resources.getQuantityString(
                    R.plurals.diagnostics_event_vehicles, detections, detections
                )
                val seconds =
                    String.format(Locale.getDefault(), "%.1f", json.optLong("total_ms") / 1000.0)
                context.getString(R.string.diagnostics_event_pipeline_run, vehicles, seconds)
            }

            "crash" -> context.getString(
                R.string.diagnostics_event_crash, json.optString("exception", "?")
            )

            else -> context.getString(R.string.diagnostics_event_generic, name)
        }
        if (stamp != null) "$stamp — $body" else body
    }
}

/** Copies the events file into the FileProvider `cache` path and opens the share sheet. */
private fun exportDiagnostics(context: Context) {
    val file = try {
        Telemetry.buildExportFile(context)
    } catch (e: Exception) {
        AppLogger.error("Diagnostics export failed", e)
        null
    }
    if (file == null) {
        Toast.makeText(context, R.string.diagnostics_export_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_export_subject))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.diagnostics_export_chooser))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
