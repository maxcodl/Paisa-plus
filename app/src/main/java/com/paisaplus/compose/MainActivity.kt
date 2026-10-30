package com.paisaplus.compose

import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.ui.transactions.PayeeScreen
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import android.app.Application
import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.paisaplus.compose.data.LockSettings
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.ui.onboarding.OnboardingScreen
import com.paisaplus.compose.ui.accounts.AccountDetailScreen
import com.paisaplus.compose.ui.settings.AppLock
import com.paisaplus.compose.ui.settings.ProfileScreen
import com.paisaplus.compose.ui.settings.ImportCsvScreen
import com.paisaplus.compose.ui.settings.LockScreen
import com.paisaplus.compose.ui.settings.SettingsScreen
import com.paisaplus.compose.ui.theme.LocalNavLabels
import com.paisaplus.compose.ui.theme.LocalGlass
import com.paisaplus.compose.ui.theme.glass
import com.paisaplus.compose.ui.theme.Glass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.paisaplus.compose.ui.more.BudgetsScreen
import com.paisaplus.compose.ui.more.LoansScreen
import com.paisaplus.compose.ui.more.MoreScreen
import com.paisaplus.compose.ui.more.DebtsScreen
import com.paisaplus.compose.ui.more.RecurringScreen
import com.paisaplus.compose.ui.settings.BackupScreen
import com.paisaplus.compose.ui.more.ReportsScreen
import com.paisaplus.compose.ui.more.SearchScreen
import com.paisaplus.compose.work.AutoBackup
import com.paisaplus.compose.work.AutoPost
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paisaplus.compose.data.SettingsStore
import com.paisaplus.compose.data.ThemeSettings
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.accounts.AccountsScreen
import com.paisaplus.compose.ui.categories.CategoriesScreen
import com.paisaplus.compose.ui.home.HomeScreen
import com.paisaplus.compose.ui.theme.PaisaTheme
import com.paisaplus.compose.ui.transactions.TransactionEditScreen
import com.paisaplus.compose.ui.transactions.TransactionsScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PaisaApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(L10n.wrap(base))

    override fun onCreate() {
        super.onCreate()
        AutoPost.schedule(this)
        AutoBackup.schedule(this)
    }
}

/** Bottom-bar tabs, in order. Phase 4 makes this user-customizable. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", str(R.string.home), Icons.Rounded.Home),
    TRANSACTIONS(Routes.TRANSACTIONS, str(R.string.transactions), Icons.AutoMirrored.Rounded.ReceiptLong),
    ACCOUNTS("accounts", str(R.string.accounts), Icons.Rounded.AccountBalanceWallet),
    CATEGORIES(Routes.CATEGORIES, str(R.string.categories), Icons.Rounded.Category),
    REPORTS(Routes.REPORTS, str(R.string.reports), Icons.Rounded.DonutLarge),
    SEARCH(Routes.SEARCH, str(R.string.search), Icons.Rounded.Search),

    MORE(Routes.MORE, str(R.string.more), Icons.Rounded.GridView),
}

/** Which tabs the bar shows: Home first and fixed (its top bar always links to More), up to [MAX] total. */
object TabConfig {
    const val MAX = 5
    val DEFAULT = listOf(Tab.HOME, Tab.ACCOUNTS, Tab.REPORTS, Tab.SEARCH)

    fun parse(s: String?): List<Tab> {
        if (s.isNullOrBlank()) return DEFAULT
        val picked = s.split(',').mapNotNull { n -> Tab.entries.firstOrNull { it.name == n.trim() } }.distinct() - Tab.HOME
        return (listOf(Tab.HOME) + picked).take(MAX)
    }

    fun format(tabs: List<Tab>) = parse(tabs.joinToString(",") { it.name }).joinToString(",") { it.name }

    /** The bar as last composed; read by [go]. */
    @Volatile var active: List<Tab> = DEFAULT
}

object Routes {
    const val TRANSACTIONS = "transactions"
    const val CATEGORIES = "categories"
    const val MORE = "more"
    const val BUDGETS = "budgets"
    const val LOANS = "loans"
    const val RECURRING = "recurring"
    const val BACKUP = "backup"
    const val DEBTS = "debts"
    const val SETTINGS = "settings"
    const val IMPORT_MESSAGES = "import_messages"
    const val SCAN_LOG = "scan_log"
    const val PAYMENTS = "payments"
    const val PEOPLE = "people"
    const val IMPORT_CSV = "import_csv"
    const val PROFILE = "profile"
    const val REPORTS = "reports"
    const val SEARCH = "search"
}

/** Opens the add/edit transaction screen. id 0 = new. */
fun NavHostController.editTransaction(id: Long = 0, type: TransactionType = TransactionType.EXPENSE, account: Long = 0) =
    navigate("tx/$id?type=${type.name}&account=$account")

/** A transaction tap: its payee page (every transaction with that name), or the editor when it has no name. */
fun NavHostController.openPayee(e: ExpenseEntity) = if (e.name.isBlank()) editTransaction(e.id) else navigate("payee/${e.id}")

fun NavHostController.openTab(t: Tab) = navigate(t.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Navigates to [route], switching tabs instead of stacking when it is in the bar. */
fun NavHostController.go(route: String) =
    TabConfig.active.firstOrNull { it.route == route }?.let { openTab(it) } ?: navigate(route)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: SettingsStore

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(L10n.wrap(newBase))

    private var locked by mutableStateOf(false)
    private var lockCfg = LockSettings()
    private var stoppedAt = 0L
    private var coldStart = true
    /** Set by the widget's "Add expense" deep link. */
    private var pendingAdd by mutableStateOf(false)
    /** Route asked for by a notification (e.g. the update notice opens Settings › App updates). */
    private var pendingOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAdd = intent?.getBooleanExtra(EXTRA_ADD, false) == true
        pendingOpen = intent?.getStringExtra(EXTRA_OPEN)
        lifecycleScope.launch {
            settings.lock.collect { l ->
                lockCfg = l
                if (l.secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                if (coldStart && l.enabled && AppLock.available(this@MainActivity)) {
                    locked = true
                    AppLock.prompt(this@MainActivity) { locked = false }
                }
                coldStart = false
            }
        }
        setContent {
            val theme by settings.theme.collectAsStateWithLifecycle(ThemeSettings())
            val money by settings.money.collectAsStateWithLifecycle(Money.format)
            Money.use(money)
            val monthStart by settings.monthStart.collectAsStateWithLifecycle(1)
            PaisaTheme(theme) { androidx.compose.runtime.CompositionLocalProvider(com.paisaplus.compose.ui.theme.LocalMonthStart provides monthStart) {
                // Recompose every amount when the currency/locale changes.
                key(money) {
                    if (locked) LockScreen { AppLock.prompt(this) { locked = false } }
                    else PaisaRoot(addRequested = pendingAdd, onAddHandled = { pendingAdd = false }, openRequested = pendingOpen, onOpenHandled = { pendingOpen = null })
                }
            } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ADD, false)) pendingAdd = true
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingOpen = it }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val away = System.currentTimeMillis() - stoppedAt
        if (stoppedAt > 0 && !locked && lockCfg.enabled && away >= lockCfg.afterMinutes * 60_000L && AppLock.available(this)) {
            locked = true
            AppLock.prompt(this) { locked = false }
        }
    }

    companion object {
        const val EXTRA_ADD = "add_expense"
        const val EXTRA_OPEN = "open_route"
        const val OPEN_UPDATES = "${Routes.SETTINGS}/UPDATES"
    }
}

@Composable
private fun PaisaRoot(addRequested: Boolean, onAddHandled: () -> Unit, openRequested: String? = null, onOpenHandled: () -> Unit = {}, vm: AppViewModel = hiltViewModel()) {
    val d by vm.data.collectAsStateWithLifecycle()
    val done by vm.onboarded.collectAsStateWithLifecycle()
    val automationIntroSeen by vm.automationIntroSeen.collectAsStateWithLifecycle()
    val quickAddLaunch = androidx.compose.runtime.saveable.rememberSaveable { addRequested }
    val ob = done
    // Decided once at launch: existing users (data already here) never see onboarding, but data
    // arriving mid-onboarding (CSV import) must not cut the flow short.
    var existing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
    if (existing == null && ob != null && !d.loading) existing = d.accounts.isNotEmpty()
    when {
        ob == null || existing == null || automationIntroSeen == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        !ob && existing == false -> OnboardingScreen(vm)
        automationIntroSeen == false && !quickAddLaunch -> com.paisaplus.compose.ui.onboarding.AutomationSetupScreen(onDone = { vm.finishAutomationIntro() })
        else -> {
            LaunchedEffect(Unit) { if (!ob) vm.setOnboarded() }
            MainNav(vm, addRequested, onAddHandled, openRequested, onOpenHandled)
            com.paisaplus.compose.update.UpdatePrompt()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** The app's snackbar in the theme's rounded shape; swipe it either way to dismiss. */
@Composable
private fun SwipeSnackbar(data: androidx.compose.material3.SnackbarData) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> (v != androidx.compose.material3.SwipeToDismissBoxValue.Settled).also { if (it) data.dismiss() } },
    )
    androidx.compose.material3.SwipeToDismissBox(state, backgroundContent = {}) {
        androidx.compose.material3.Snackbar(data, Modifier.padding(horizontal = 12.dp), shape = MaterialTheme.shapes.extraLarge)
    }
}

@Composable
private fun MainNav(vm: AppViewModel, addRequested: Boolean, onAddHandled: () -> Unit, openRequested: String? = null, onOpenHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    TabConfig.active = tabs
    val route = entry?.destination?.route
    // One behaviour for every destination, reset per route so a new screen starts un-scrolled.
    val topBar = androidx.compose.runtime.key(route) { androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior() }
    val tab = tabs.firstOrNull { it.route == route }
    val labels = LocalNavLabels.current

    LaunchedEffect(addRequested) { if (addRequested) { nav.editTransaction(); onAddHandled() } }
    LaunchedEffect(openRequested) { openRequested?.let { nav.navigate(it); onOpenHandled() } }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            // [holdMs]: stays that long (or until swiped away) instead of the few seconds of a normal message.
            if (m.holdMs > 0) launch { delay(150); val shown = snackbar.currentSnackbarData; delay(m.holdMs); shown?.dismiss() }
            val r = snackbar.showSnackbar(
                m.text, m.action,
                withDismissAction = m.sticky || m.holdMs > 0,
                duration = when {
                    m.sticky || m.holdMs > 0 -> SnackbarDuration.Indefinite
                    m.action != null -> SnackbarDuration.Long
                    else -> SnackbarDuration.Short
                },
            )
            if (r == SnackbarResult.ActionPerformed) m.onAction()
        }
    }

    val bg = LocalGlass.current
    val screens = rememberLayerBackdrop()
    // The bar refracts the background and the screen scrolling under it.
    val behindBar = if (bg != null) rememberCombinedBackdrop(bg.backdrop, screens) else null

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) { data -> key(data) { SwipeSnackbar(data) } } },
        bottomBar = {
            if (tab != null && behindBar != null) GlassTabBar(tabs, tab, labels, bg!!.copy(backdrop = behindBar)) { nav.openTab(it) }
            else if (tab != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { nav.openTab(t) },
                        icon = { Icon(t.icon, if (labels) null else t.label) },
                        label = if (labels) ({ Text(t.label, maxLines = 1) }) else null,
                    )
                }
            }
        },
    ) { pad ->
        // Ignore a second Back tap while the current destination is entering or leaving.
        // Lifecycle state also covers predictive/system navigation managed by NavHost.
        val back = {
            if (nav.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) nav.popBackStack()
            Unit
        }
        val openSettings: (String) -> Unit = { destination ->
            if (nav.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                nav.navigate(destination) { launchSingleTop = true }
            }
        }
        // Tabs in the bar get no back arrow.
        fun up(t: Tab) = if (t in tabs) null else back
        androidx.compose.runtime.CompositionLocalProvider(com.paisaplus.compose.ui.components.LocalTopBarBehavior provides topBar) {
        NavHost(
            nav, startDestination = Tab.HOME.route,
            modifier = (if (isSettingsDestination(route)) Modifier else Modifier.nestedScroll(topBar.nestedScrollConnection))
                .then(if (bg != null) Modifier.layerBackdrop(screens) else Modifier),
            enterTransition = {
                if (isSettingsDestination(initialState.destination.route) || isSettingsDestination(targetState.destination.route)) {
                    slideIntoContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start, androidx.compose.animation.core.tween(260))
                } else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
            },
            exitTransition = {
                if (isSettingsDestination(initialState.destination.route) || isSettingsDestination(targetState.destination.route)) {
                    slideOutOfContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start, androidx.compose.animation.core.tween(260), targetOffset = { it / 4 }) +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                } else androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
            },
            popEnterTransition = {
                if (isSettingsDestination(initialState.destination.route) || isSettingsDestination(targetState.destination.route)) {
                    slideIntoContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End, androidx.compose.animation.core.tween(260), initialOffset = { it / 4 }) +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180))
                } else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
            },
            popExitTransition = {
                if (isSettingsDestination(initialState.destination.route) || isSettingsDestination(targetState.destination.route)) {
                    slideOutOfContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End, androidx.compose.animation.core.tween(260))
                } else androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
            },
            // Predictive Back has separate defaults (including a 70% scale-out).
            // Keep pages at their original size while the gesture seeks this slide.
            predictivePopEnterTransition = { _ ->
                slideIntoContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End, androidx.compose.animation.core.tween(260), initialOffset = { it / 4 })
            },
            predictivePopExitTransition = { _ ->
                slideOutOfContainer(androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End, androidx.compose.animation.core.tween(260))
            },
        ) {
            composable(Tab.HOME.route) { HomeScreen(vm, nav, pad) }
            composable(Tab.ACCOUNTS.route) { AccountsScreen(vm, pad, up(Tab.ACCOUNTS)) { nav.navigate("account/$it") } }
            composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                AccountDetailScreen(vm, id, back, onAdd = { nav.editTransaction(account = it) }, onPayBill = { nav.navigate("transfer?to=$it") }, onOpenTx = { tx -> vm.data.value.expenses.firstOrNull { it.id == tx }?.let(nav::openPayee) })
            }
            composable(Routes.PROFILE) { ProfileScreen(vm, back) { nav.go(it) } }
            composable(Tab.REPORTS.route) { ReportsScreen(vm, up(Tab.REPORTS), pad) }
            composable(Tab.SEARCH.route) { SearchScreen(vm, up(Tab.SEARCH), pad) { tx -> vm.data.value.expenses.firstOrNull { it.id == tx }?.let(nav::openPayee) } }
            composable("payee/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PayeeScreen(vm, e.arguments!!.getLong("id"), back) { nav.editTransaction(it) }
            }
            composable(Tab.TRANSACTIONS.route) { TransactionsScreen(vm, nav, pad, up(Tab.TRANSACTIONS)) }
            composable(Tab.CATEGORIES.route) { CategoriesScreen(vm, pad, up(Tab.CATEGORIES)) }
            composable(Tab.MORE.route) { MoreScreen(nav, pad, up(Tab.MORE)) }
            composable(Routes.BUDGETS) { BudgetsScreen(vm, back) }
            composable(Routes.LOANS) { LoansScreen(vm, back) }
            composable(Routes.RECURRING) { RecurringScreen(vm, back) }
            composable(Routes.BACKUP) { BackupScreen(vm, back) }
            composable(Routes.DEBTS) { DebtsScreen(vm, back) }
            composable(Routes.PEOPLE) { com.paisaplus.compose.ui.settings.PeopleScreen(vm, back, onOpenPayee = { nav.navigate("payee/$it") }, onOpenDebts = { nav.navigate(Routes.DEBTS) }) }
            composable(Routes.PAYMENTS) { com.paisaplus.compose.ui.settings.PaymentsInboxScreen(vm, back, onOpenTx = { nav.editTransaction(it) }) }
            composable(Routes.IMPORT_MESSAGES) { com.paisaplus.compose.ui.settings.PaymentsInboxScreen(vm, back, onOpenTx = { nav.editTransaction(it) }) }
            composable(Routes.IMPORT_CSV) { ImportCsvScreen(vm, back) }
            composable(Routes.SCAN_LOG) { com.paisaplus.compose.ui.settings.ScanLogScreen(back) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, back, openSettings) }
            composable("${Routes.SETTINGS}/{page}") { e ->
                val page = com.paisaplus.compose.ui.settings.SettingsPage.entries.firstOrNull { it.name == e.arguments?.getString("page") }
                    ?: com.paisaplus.compose.ui.settings.SettingsPage.MONEY
                com.paisaplus.compose.ui.settings.SettingsPageScreen(vm, page, back, openSettings)
            }
            composable("transfer?to={to}", arguments = listOf(navArgument("to") { type = NavType.LongType; defaultValue = 0L })) { e ->
                com.paisaplus.compose.ui.transactions.TransferScreen(vm, initialTo = e.arguments!!.getLong("to"), onDone = { nav.popBackStack() })
            }
            composable(
                "tx/{id}?type={type}&account={account}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name },
                    navArgument("account") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                TransactionEditScreen(
                    vm,
                    id = e.arguments!!.getLong("id"),
                    initialType = TransactionType.valueOf(e.arguments!!.getString("type")!!),
                    onDone = { nav.popBackStack() },
                    initialAccount = e.arguments!!.getLong("account"),
                )
            }
        }
        }
    }
}

/** Related settings destinations share a mirrored horizontal navigation transition. */
private fun isSettingsDestination(route: String?) = route == Routes.SETTINGS || route?.startsWith("${Routes.SETTINGS}/") == true ||
    route in setOf(Routes.PROFILE, Routes.IMPORT_MESSAGES, Routes.IMPORT_CSV, Routes.BACKUP)

/** Floating liquid-glass capsule replacing the NavigationBar when glass is on. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GlassTabBar(tabs: List<Tab>, selected: Tab, showLabels: Boolean, glass: Glass, onSelect: (Tab) -> Unit) {
    val c = MaterialTheme.colorScheme
    // Labels clip past ~1.3x text size; the icons then carry the tab (they keep their content descriptions).
    val labels = showLabels && LocalDensity.current.fontScale <= 1.3f
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .height(if (labels) 68.dp else 60.dp)
            .glass(glass, CircleShape, c.surfaceContainer, alpha = 0.5f, blur = 16.dp, lens = 24.dp)
            .padding(6.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val on = t == selected
            val pill by animateColorAsState(if (on) c.secondaryContainer.copy(alpha = 0.85f) else Color.Transparent, spec)
            val fg by animateColorAsState(if (on) c.onSecondaryContainer else c.onSurfaceVariant, spec)
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(pill)
                    .selectable(on, role = Role.Tab) { onSelect(t) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, if (labels) null else t.label, tint = fg)
                if (labels) Text(t.label, color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
