package com.paisaplus.compose.ui.settings

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.Routes
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.Avatar
import com.paisaplus.compose.ui.components.LinkRow
import com.paisaplus.compose.ui.components.ListGroup
import com.paisaplus.compose.ui.components.ListHeader

/** Profile (name, photo) plus shortcuts to every non-tab screen, so Home needs no More button. */
@Composable
fun ProfileScreen(vm: AppViewModel, onBack: () -> Unit, open: (String) -> Unit) {
    val name by vm.userName.collectAsStateWithLifecycle()
    val image by vm.userImage.collectAsStateWithLifecycle()
    var draft by rememberSaveable(name) { mutableStateOf(name) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::setUserImage) }
    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(str(R.string.profile)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back)) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(image, draft, 120.dp, Modifier.clickable(role = Role.Button, onClickLabel = str(R.string.change_photo), onClick = choose))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(choose, Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.PhotoCamera, null, Modifier.padding(end = 8.dp)); Text(if (image.isEmpty()) str(R.string.add_photo) else str(R.string.change_photo))
                        }
                        if (image.isNotEmpty()) TextButton({ vm.setUserImage(null) }, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.remove)) }
                    }
                    OutlinedTextField(
                        draft, { draft = it; vm.setUserName(it) }, label = { Text(str(R.string.name)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { ListHeader(str(R.string.manage)) }
            item {
                ListGroup {
                    item { LinkRow(Icons.Rounded.Savings, 1, str(R.string.budgets), str(R.string.monthly_limits_per_category)) { open(Routes.BUDGETS) } }
                    item { LinkRow(Icons.Rounded.RequestQuote, 2, str(R.string.loans_emis), str(R.string.instalments_and_what_s_left)) { open(Routes.LOANS) } }
                    item { LinkRow(Icons.Rounded.Handshake, 3, str(R.string.debts), str(R.string.money_lent_and_borrowed)) { open(Routes.DEBTS) } }
                    item { LinkRow(Icons.Rounded.EventRepeat, 4, str(R.string.recurring), str(R.string.bills_and_income_that_repeat)) { open(Routes.RECURRING) } }
                    item { LinkRow(Icons.Rounded.People, 0, str(R.string.people), str(R.string.people_summary)) { open(Routes.PEOPLE) } }
                    item { LinkRow(Icons.Rounded.Category, 5, str(R.string.categories), str(R.string.icons_colours_budgets)) { open(Routes.CATEGORIES) } }
                    item { LinkRow(Icons.Rounded.AutoAwesome, 6, str(R.string.sms_ai), str(R.string.sms_ai_summary)) { open("${Routes.SETTINGS}/AUTOMATION") } }
                    item { LinkRow(Icons.Rounded.Notifications, 0, str(R.string.notifications), str(R.string.notifications_summary)) { open("${Routes.SETTINGS}/NOTIFICATIONS") } }
                }
            }
            item { ListHeader(str(R.string.app)) }
            item {
                ListGroup {
                    item { LinkRow(Icons.Rounded.Settings, 7, str(R.string.settings), str(R.string.appearance_glass_security)) { open(Routes.SETTINGS) } }
                    item { LinkRow(Icons.Rounded.Backup, 0, str(R.string.backup_restore), str(R.string.export_restore_daily_auto_backup)) { open(Routes.BACKUP) } }
                    item { LinkRow(Icons.Rounded.UploadFile, 6, str(R.string.import_bank_statement), "CSV") { open(Routes.IMPORT_CSV) } }
                }
            }
            item { ListHeader(str(R.string.about)) }
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                val version = androidx.compose.runtime.remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
                ListGroup {
                    item { LinkRow(Icons.Rounded.SystemUpdate, 5, str(R.string.app_updates), str(R.string.version_x, version)) { open("${Routes.SETTINGS}/UPDATES") } }
                    item { LinkRow(Icons.Rounded.Code, 2, str(R.string.source_code), "github.com/maxcodl/Paisa-plus") { uri.openUri("https://github.com/maxcodl/Paisa-plus/") } }
                }
            }
            item {
                Text(
                    str(R.string.made_with_love), Modifier.fillMaxWidth().padding(top = 24.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
