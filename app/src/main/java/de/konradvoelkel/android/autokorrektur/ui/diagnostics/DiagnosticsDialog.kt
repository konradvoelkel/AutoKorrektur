package de.konradvoelkel.android.autokorrektur.ui.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.konradvoelkel.android.autokorrektur.R
import de.konradvoelkel.android.autokorrektur.databinding.DialogDiagnosticsBinding
import de.konradvoelkel.android.autokorrektur.telemetry.Telemetry
import de.konradvoelkel.android.autokorrektur.utils.AppLogger
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "Diagnostics" menu entry: the opt-in switch for [Telemetry], a summary of what has been
 * recorded, the recorded events in plain words, and Export (share sheet, via the FileProvider
 * `cache` path) / Delete actions.
 *
 * Kept as a plain dialog rather than a settings screen because it is the app's only setting and
 * the `core` tier has no settings surface at all (docs/PRODUCT_TIERS.md). Available on
 * every flavor — field testers on `core` are exactly who the export is for.
 *
 * Three things here come from usability run 002, where the persona was a non-developer reading
 * this screen adversarially (`reports/2026-09-28-ivan-002.adoc`):
 * - **UX-12**: the events are rendered in words, because exporting a `.jsonl` to another app was
 *   previously the only way to see them.
 * - **UX-11**: deleting now switches recording off as well. It used to leave the switch on, so
 *   [Telemetry.clear] immediately minted a fresh install id and wrote a new `session_start` — and
 *   the dialog then showed a non-zero count and a brand-new id seconds after confirming
 *   "delete everything", which reads as the deletion having failed.
 * - **UX-14**: the buttons use the app's own strings, so they follow the app's language.
 */
object DiagnosticsDialog {

    fun show(activity: Activity) {
        val binding = DialogDiagnosticsBinding.inflate(LayoutInflater.from(activity))

        binding.btnDiagnosticsDelete.setOnClickListener { confirmDelete(activity, binding) }

        binding.tvDiagnosticsSummary.setOnLongClickListener {
            val id = Telemetry.installId ?: return@setOnLongClickListener false
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.diagnostics_title), id))
            Toast.makeText(activity, R.string.diagnostics_install_id_copied, Toast.LENGTH_SHORT).show()
            true
        }

        render(activity, binding)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.diagnostics_title)
            .setView(binding.root)
            .setPositiveButton(R.string.diagnostics_export) { _, _ -> export(activity) }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        // Cap the scrolling body at half the screen. With the description plus the event list the
        // dialog otherwise grows taller than a 720x1280 screen and its buttons fall off the
        // bottom. The ScrollView asks for its full content height, so the ceiling has to be
        // imposed here, once the real measurement exists.
        dialog.setOnShowListener {
            val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.5f).toInt()
            binding.root.let { body ->
                if (body.height > maxHeight) {
                    body.layoutParams = body.layoutParams.apply { height = maxHeight }
                    body.requestLayout()
                }
            }
        }
        dialog.show()
    }

    /**
     * Redraws switch, summary and event list from the current [Telemetry] state. The switch's
     * listener is detached while its state is set, or restoring it after a delete (UX-11) would
     * call straight back into [Telemetry.setEnabled].
     */
    private fun render(activity: Activity, binding: DialogDiagnosticsBinding) {
        binding.switchDiagnostics.setOnCheckedChangeListener(null)
        binding.switchDiagnostics.isChecked = Telemetry.isEnabled
        binding.switchDiagnostics.setOnCheckedChangeListener { _, checked ->
            Telemetry.setEnabled(checked)
            render(activity, binding)
        }

        binding.tvDiagnosticsSummary.text = summary(activity)

        val lines = readableEvents(activity)
        binding.tvDiagnosticsEvents.text =
            if (lines.isEmpty()) activity.getString(R.string.diagnostics_events_empty)
            else lines.joinToString("\n")
    }

    private fun summary(activity: Activity): String {
        val count = Telemetry.eventCount()
        val kb = Telemetry.sizeBytes() / 1024.0
        val id = Telemetry.installId
        return buildString {
            append(activity.resources.getQuantityString(R.plurals.diagnostics_event_count, count, count))
            append(" · ").append(String.format(Locale.ROOT, "%.1f KB", kb))
            if (id != null) {
                append('\n').append(activity.getString(R.string.diagnostics_install_id_label, id))
                append('\n').append(activity.getString(R.string.diagnostics_install_id_hint))
            }
        }
    }

    /**
     * Turns the stored JSON Lines into one human sentence each, newest first. Anything that fails
     * to parse, or an event type this does not know, falls back to naming the event — the point is
     * that the user can see *that* something was recorded and roughly what, never a stack of JSON.
     */
    private fun readableEvents(activity: Activity): List<String> {
        val timeFormat = SimpleDateFormat(
            activity.getString(R.string.diagnostics_event_time_format),
            Locale.getDefault()
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
                "session_start" -> activity.getString(
                    R.string.diagnostics_event_session_start,
                    json.optString("app_version", "?")
                )

                "pipeline_run" -> {
                    val detections = json.optInt("detections", 0)
                    val vehicles = activity.resources.getQuantityString(
                        R.plurals.diagnostics_event_vehicles, detections, detections
                    )
                    val seconds = String.format(Locale.getDefault(), "%.1f", json.optLong("total_ms") / 1000.0)
                    activity.getString(R.string.diagnostics_event_pipeline_run, vehicles, seconds)
                }

                "crash" -> activity.getString(
                    R.string.diagnostics_event_crash,
                    json.optString("exception", "?")
                )

                else -> activity.getString(R.string.diagnostics_event_generic, name)
            }
            if (stamp != null) "$stamp — $body" else body
        }
    }

    private fun export(activity: Activity) {
        val file = try {
            Telemetry.buildExportFile(activity)
        } catch (e: Exception) {
            AppLogger.error("Diagnostics export failed", e)
            null
        }
        if (file == null) {
            Toast.makeText(activity, R.string.diagnostics_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.diagnostics_export_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.diagnostics_export_chooser)))
    }

    /**
     * Deleting switches recording **off** first and only then clears, so that [Telemetry.clear]
     * does not re-create an install id and a `session_start` behind the user's back (UX-11). The
     * outcome is then acknowledged in a dialog rather than a toast: it reports two state changes
     * at once, and the user who chose to delete their data is the one who most wants to be sure
     * it happened.
     */
    private fun confirmDelete(activity: Activity, binding: DialogDiagnosticsBinding) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.diagnostics_delete)
            .setMessage(R.string.diagnostics_delete_confirm)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                Telemetry.setEnabled(false)
                Telemetry.clear()
                render(activity, binding)
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.diagnostics_deleted_title)
                    .setMessage(R.string.diagnostics_deleted)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
