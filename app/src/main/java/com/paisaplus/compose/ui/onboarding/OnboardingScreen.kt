package com.paisaplus.compose.ui.onboarding

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.OptionCard
import com.paisaplus.compose.ui.components.PillButton
import com.paisaplus.compose.ui.settings.ImportCsvScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import com.paisaplus.compose.data.ThemeMode
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.ChoiceChips
import com.paisaplus.compose.ui.settings.Swatches
import com.paisaplus.compose.ui.settings.accents
import com.paisaplus.compose.ui.settings.schemeSummary
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.paisaplus.compose.ui.components.Avatar
import java.util.Currency
import java.util.Locale

// Restore / import / start fresh comes last, once name, currency and theme are set.
private enum class Page { WELCOME, FEATURES, NOTIFY, ALERTS, SMS, AGREE, BACKUP, THEME, NAME, CURRENCY, SETUP }

/** Pages with a Not now: each asks for one optional permission or choice. */
private val optional = setOf(Page.NOTIFY, Page.ALERTS, Page.SMS, Page.BACKUP)

/** First run only (no accounts yet). Restoring a backup ends onboarding as soon as data lands. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val pages = Page.entries.filter { it != Page.NOTIFY || Build.VERSION.SDK_INT >= 33 }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val money by vm.money.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val photo by vm.userImage.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::setUserImage) }
    var agreed by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    var csvImport by rememberSaveable { mutableStateOf(false) }
    var imported by rememberSaveable { mutableStateOf(false) }
    val page = pages[pager.currentPage]
    val next: () -> Unit = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    val backupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupDir); next() }
    fun listenerOn() = ctx.getSystemService(android.app.NotificationManager::class.java)
        .isNotificationListenerAccessGranted(android.content.ComponentName(ctx, com.paisaplus.compose.messages.BankNotificationListener::class.java))
    // Coming back from Android's notification-access screen with access granted moves on by itself.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (pages.getOrNull(pager.currentPage) == Page.ALERTS && listenerOn()) next()
    }
    BackHandler(pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    val hasData = vm.data.collectAsStateWithLifecycle().value.accounts.isNotEmpty()
    if (csvImport) {
        // A successful commit returns to the last page, now the "all set" screen.
        val before = rememberSaveable { vm.csvCommits }
        LaunchedEffect(vm.csvCommits) { if (vm.csvCommits != before) { imported = true; csvImport = false } }
        BackHandler { csvImport = false }
        ImportCsvScreen(vm) { csvImport = false }
        return
    }
    if (vm.importing) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), Alignment.Center) { LoadingIndicator() }
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(
                "${pager.currentPage + 1}/${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
            )
            HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = false) { i ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    when (pages[i]) {
                        Page.SETUP -> if (imported) {
                            Hero(Icons.Rounded.CheckCircle)
                            Copy(str(R.string.you_re_all_set), str(R.string.your_statement_is_imported))
                        } else {
                            Hero(Icons.Rounded.AccountBalanceWallet)
                            Spacer(Modifier.size(40.dp))
                            OptionCard(Icons.Rounded.Restore, str(R.string.restore_a_backup), { confirmRestore = true })
                            Spacer(Modifier.size(12.dp))
                            OptionCard(Icons.Rounded.UploadFile, str(R.string.import_a_bank_statement_csv), { csvImport = true })
                            Spacer(Modifier.size(8.dp))
                            Text(
                                str(R.string.paisa_or_paisa_backups),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            )
                            Copy(str(R.string.set_up_paisa), str(R.string.start_fresh_or_bring_your))
                        }
                        Page.WELCOME -> {
                            Hero(Icons.Rounded.Savings)
                            Copy(str(R.string.welcome_to_paisa), str(R.string.your_data_stays_on_this))
                        }
                        Page.FEATURES -> {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Feature(Icons.Rounded.Savings, str(R.string.budgets), str(R.string.monthly_limits))
                                Feature(Icons.Rounded.RequestQuote, str(R.string.loans_emis), str(R.string.auto_posted_installments))
                                Feature(Icons.Rounded.Handshake, str(R.string.debts), str(R.string.lent_and_borrowed))
                                Feature(Icons.Rounded.EventRepeat, str(R.string.recurring), str(R.string.with_reminders))
                                Feature(Icons.Rounded.Insights, str(R.string.reports), str(R.string.daily_and_by_category))
                            }
                            Copy(str(R.string.what_you_can_track), str(R.string.works_offline))
                        }
                        Page.NOTIFY -> {
                            Hero(Icons.Rounded.NotificationsActive)
                            Copy(str(R.string.reminders), str(R.string.on_the_day_a_payment))
                        }
                        Page.ALERTS -> {
                            Hero(Icons.Rounded.Sms)
                            Copy(str(R.string.onb_alerts_title), str(R.string.onb_alerts_body))
                            if (Build.VERSION.SDK_INT >= 33) {
                                Spacer(Modifier.size(12.dp))
                                Text(str(R.string.restricted_settings_steps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                TextButton({ ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))) }, Modifier.heightIn(min = 48.dp)) {
                                    Text(str(R.string.open_app_info))
                                }
                            }
                        }
                        Page.SMS -> {
                            Hero(Icons.Rounded.History)
                            Copy(str(R.string.onb_sms_title), str(R.string.onb_sms_body))
                        }
                        Page.BACKUP -> {
                            Hero(Icons.Rounded.CloudSync)
                            Copy(str(R.string.onb_backup_title), str(R.string.onb_backup_body))
                        }
                        Page.AGREE -> {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(
                                    str(R.string.i_ll_back_up_my),
                                    str(R.string.lost_data_can_t_be),
                                ).forEachIndexed { k, text ->
                                    Check(text, k in agreed) { agreed = if (k in agreed) agreed - k else agreed + k }
                                }
                            }
                            Copy(str(R.string.your_data_your_control), str(R.string.daily_backup_is_in_settings))
                        }
                        Page.THEME -> {
                            // Live: every pick restyles this screen immediately.
                            val t = theme
                            ThemePreview()
                            Spacer(Modifier.size(24.dp))
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SegmentedTabs(ThemeMode.entries, t.mode, { str(when (it) { ThemeMode.SYSTEM -> R.string.system_language; ThemeMode.LIGHT -> R.string.light; ThemeMode.DARK -> R.string.dark }) }, { vm.setTheme(t.copy(mode = it)) })
                                if (Build.VERSION.SDK_INT >= 31) Check(str(R.string.wallpaper_colours), t.dynamicColor) { vm.setTheme(t.copy(dynamicColor = !t.dynamicColor)) }
                                if (!t.dynamicColor || Build.VERSION.SDK_INT < 31) Swatches(accents, t.accent, firstIsDefault = true) { vm.setTheme(t.copy(accent = it)) }
                                ChoiceChips(schemeSummary.keys.toList(), t.scheme, { it.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") }, { vm.setTheme(t.copy(scheme = it)) })
                                Check(str(R.string.liquid_glass), t.glass) { vm.setTheme(t.copy(glass = !t.glass)) }
                            }
                            Copy(str(R.string.pick_your_colours), str(R.string.more_options_in_settings))
                        }
                        Page.NAME -> {
                            val choosePhoto = { keyboard?.hide(); pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            Avatar(photo, name, 128.dp, Modifier.clip(CircleShape).clickable(onClickLabel = str(R.string.add_photo), onClick = choosePhoto))
                            TextButton(choosePhoto, Modifier.heightIn(min = 48.dp)) { Text(str(if (photo.isEmpty()) R.string.add_photo else R.string.change_photo)) }
                            Spacer(Modifier.size(16.dp))
                            OutlinedTextField(
                                name, { name = it }, placeholder = { Text(str(R.string.your_name)) }, singleLine = true,
                                shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            )
                            Copy(str(R.string.what_should_we_call_you), str(R.string.for_the_greeting_on_home))
                        }
                        Page.CURRENCY -> {
                            Hero(Icons.Rounded.CurrencyRupee)
                            Spacer(Modifier.size(32.dp))
                            val cur = runCatching { Currency.getInstance(money.currency) }.getOrNull()
                            OptionCard(Icons.Rounded.CurrencyRupee, "${cur?.getSymbol(Locale.forLanguageTag(money.locale))}  ${cur?.displayName ?: money.currency}", { pickCurrency = true })
                            Spacer(Modifier.size(8.dp))
                            Text(str(R.string.example_x, Money.formatter(money).format(1234567.5)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Copy(str(R.string.your_currency), str(R.string.changeable_in_settings))
                        }
                    }
                }
            }
            val cta: Triple<String, Boolean, () -> Unit> = when (page) {
                // After a CSV import the data is already here: startFresh only adds what is missing.
                Page.SETUP -> Triple(str(if (hasData) R.string.get_started else R.string.start_fresh), true) { vm.startFresh() }
                Page.NOTIFY -> Triple(str(R.string.allow_notifications), true) { notify.launch(Manifest.permission.POST_NOTIFICATIONS) }
                Page.ALERTS -> Triple(str(R.string.allow_access), true) {
                    if (listenerOn()) next() else ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                Page.SMS -> Triple(str(R.string.allow_sms_access), true) { askSms.launch(Manifest.permission.READ_SMS) }
                Page.BACKUP -> Triple(str(R.string.choose_backup_folder), true) { backupFolder.launch(null) }
                Page.AGREE -> Triple(str(R.string.i_agree), agreed.size == 2, next)
                Page.NAME -> Triple(str(R.string.next), name.isNotBlank()) { keyboard?.hide(); vm.setUserName(name); next() }
                else -> Triple(str(R.string.next), true, next)
            }
            val (label, enabled, action) = cta
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (page in optional) TextButton(next, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.not_now)) }
                PillButton(label, action, enabled = enabled)
            }
        }
    }

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text(str(R.string.restore_a_backup)) },
        text = { Text(str(R.string.pick_the_json_file)) },
        confirmButton = { TextButton({ confirmRestore = false; picker.launch(arrayOf("application/json", "*/*")) }) { Text(str(R.string.choose_file)) } },
        dismissButton = { TextButton({ confirmRestore = false }) { Text(str(R.string.cancel)) } },
    )
    if (pickCurrency) CurrencyPicker(money.currency) { code -> code?.let { vm.setMoney(money.copy(currency = it)) }; pickCurrency = false }
}

/** Mini Home balance card in the live theme (glass included when on). */
@Composable
private fun ThemePreview() {
    val c = MaterialTheme.colorScheme
    TintCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(str(R.string.bank_balance), style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
            AmountText(125000.0, style = MaterialTheme.typography.displaySmallEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(c.primary, c.secondary, c.tertiary, c.primaryContainer, c.tertiaryContainer).forEach {
                    Box(Modifier.size(24.dp).background(it, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                PillButton(str(R.string.add), {})
            }
        }
    }
}

@Composable
private fun Hero(icon: ImageVector) {
    Box(Modifier.size(128.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ColumnScope.Copy(title: String, body: String) {
    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
    Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.size(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.size(24.dp))
}

@Composable
private fun Feature(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Check(text: String, on: Boolean, toggle: () -> Unit) {
    Surface(
        onClick = toggle,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().semantics { toggleableState = ToggleableState(on) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CurrencyPicker(selected: String, onDone: (String?) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(str(R.string.currency)) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text(str(R.string.search_rupee_inr_dollar)) }, singleLine = true)
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Money.currencies.filter { q.isBlank() || it.currencyCode.contains(q, true) || it.displayName.contains(q, true) || it.symbol.contains(q) }.forEach { c ->
                        TextButton({ onDone(c.currencyCode) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${c.symbol}  ${c.displayName} (${c.currencyCode})", Modifier.weight(1f))
                            if (c.currencyCode == selected) Icon(Icons.Rounded.CheckCircle, str(R.string.selected))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onDone(null) }) { Text(str(R.string.close)) } },
    )
}
