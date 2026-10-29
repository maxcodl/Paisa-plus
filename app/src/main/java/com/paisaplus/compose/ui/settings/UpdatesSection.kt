package com.paisaplus.compose.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.data.UpdateFrequency
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.components.ListGroup
import com.paisaplus.compose.ui.components.ListHeader
import com.paisaplus.compose.ui.components.PrefBlock
import com.paisaplus.compose.ui.components.PrefRow
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.SwitchRow
import com.paisaplus.compose.update.UpdateState
import com.paisaplus.compose.update.UpdatesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val checkedFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/** Settings › App updates: schedule, beta channel, check now, and the found release with download + install. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdatesSection(vm: UpdatesViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val last = if (prefs.lastCheck == 0L) str(R.string.not_yet)
    else Instant.ofEpochMilli(prefs.lastCheck).atZone(ZoneId.systemDefault()).format(checkedFmt)

    Column {
        ListGroup {
            item {
                PrefBlock(Icons.Rounded.Schedule, 5, str(R.string.check_for_updates), str(R.string.version_x, vm.version)) {
                    SegmentedTabs(UpdateFrequency.entries, prefs.frequency, {
                        str(when (it) { UpdateFrequency.OFF -> R.string.off; UpdateFrequency.DAILY -> R.string.daily; UpdateFrequency.WEEKLY -> R.string.weekly; UpdateFrequency.MONTHLY -> R.string.monthly })
                    }, {
                        // The "new version" notice needs notification permission on Android 13+.
                        if (it != UpdateFrequency.OFF && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.setPrefs(prefs.copy(frequency = it))
                    })
                }
            }
            item { SwitchRow(Icons.Rounded.Science, 3, str(R.string.beta_releases), str(R.string.beta_releases_summary), prefs.beta) { vm.setPrefs(prefs.copy(beta = it)) } }
            item {
                PrefRow(
                    Icons.Rounded.Refresh, 1, str(R.string.check_now),
                    when (val s = state) {
                        UpdateState.Checking -> str(R.string.checking)
                        UpdateState.UpToDate -> str(R.string.up_to_date)
                        is UpdateState.Failed -> s.message
                        else -> str(R.string.last_checked_x, last)
                    },
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                    onClick = { vm.check() },
                )
            }
        }

        val release = when (val s = state) {
            is UpdateState.Available -> s.release
            is UpdateState.Downloading -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        }
        if (release != null) {
            ListHeader(str(R.string.update_x_available, release.version))
            ListGroup {
                item { PrefRow(Icons.Rounded.NewReleases, 6, release.name, com.paisaplus.compose.update.releaseNotes(release.notes).ifBlank { null }) }
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val s = state
                        if (s is UpdateState.Downloading) {
                            LinearWavyProgressIndicator({ s.progress }, Modifier.fillMaxWidth())
                            Text(str(R.string.downloading_x, (s.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({
                                // Android asks once to allow installs from Paisa+; send the user there first.
                                if (!vm.canInstall()) ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                                else vm.install(release)
                            }, enabled = release.sha256 != null) { Text(str(R.string.download_and_install)) }
                            OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.page))) }) { Text(str(R.string.release_notes)) }
                        }
                        if (release.sha256 == null) Text(str(R.string.update_no_checksum), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        else Text(str(R.string.update_install_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
