package cn.yiweiqiu.lessonledger.ui.nav

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.yiweiqiu.lessonledger.data.reminder.ReminderNav
import cn.yiweiqiu.lessonledger.domain.StatsPeriod
import cn.yiweiqiu.lessonledger.ui.checkin.CheckInBadgeViewModel
import cn.yiweiqiu.lessonledger.ui.checkin.CheckInScreen
import cn.yiweiqiu.lessonledger.ui.components.AuroraBackground
import cn.yiweiqiu.lessonledger.ui.components.GlassNavBar
import cn.yiweiqiu.lessonledger.ui.components.GlassNavItem
import cn.yiweiqiu.lessonledger.ui.courses.ChildrenScreen
import cn.yiweiqiu.lessonledger.ui.courses.ChildrenViewModel
import cn.yiweiqiu.lessonledger.ui.courses.ClosedCoursesScreen
import cn.yiweiqiu.lessonledger.ui.courses.CourseDetailRoute
import cn.yiweiqiu.lessonledger.ui.courses.CoursesTabScreen
import cn.yiweiqiu.lessonledger.ui.forms.ChildFormSheet
import cn.yiweiqiu.lessonledger.ui.home.HomeScreen
import cn.yiweiqiu.lessonledger.ui.mine.MineScreen
import cn.yiweiqiu.lessonledger.ui.settings.SettingsModules
import cn.yiweiqiu.lessonledger.ui.settings.SettingsScreen
import cn.yiweiqiu.lessonledger.ui.settings.SyncDetailScreen
import cn.yiweiqiu.lessonledger.ui.settings.AboutScreen
import cn.yiweiqiu.lessonledger.ui.settings.AppearanceScreen
import cn.yiweiqiu.lessonledger.ui.settings.DataScreen
import cn.yiweiqiu.lessonledger.ui.settings.DataSourceScreen
import cn.yiweiqiu.lessonledger.ui.settings.ReminderScreen
import cn.yiweiqiu.lessonledger.ui.settings.RulesScreen
import cn.yiweiqiu.lessonledger.ui.settings.SyncSettingsScreen
import cn.yiweiqiu.lessonledger.ui.stats.PaymentsDetailScreen
import cn.yiweiqiu.lessonledger.ui.stats.StatsDrill
import cn.yiweiqiu.lessonledger.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val COURSES = "courses"
    const val CHECKIN = "checkin"
    const val STATS = "stats"
    const val MINE = "mine"
    const val CHILDREN = "children"
    const val SETTINGS = "settings"
    const val SETTINGS_MODULE = "settings/{module}"
    const val SYNC = "sync"
    const val COURSE = "course/{courseId}"
    const val CLOSED_COURSES = "courses/closed?childId={childId}"
    const val PAYMENTS = "payments?period={period}&childId={childId}&category={category}&org={org}&month={month}"

    fun course(id: String) = "course/$id"

    /** 已结束课程全量清单：带上课程页当前筛的孩子，进去看到的还是同一个孩子 */
    fun closedCourses(childId: String?) = "courses/closed?childId=${childId?.let(Uri::encode) ?: ""}"

    /** 设置模块子页：appearance / reminder / sync / rules / data / about */
    fun settingsModule(module: String) = "settings/$module"

    /** 统计数字的钻取目标：把过滤条件编码成「缴费明细」路由 */
    fun payments(drill: StatsDrill = StatsDrill()): String {
        val enc: (String?) -> String = { it?.let(Uri::encode) ?: "" }
        return "payments?period=${enc(drill.period?.name)}" +
            "&childId=${enc(drill.childId)}" +
            "&category=${enc(drill.category?.name)}" +
            "&org=${enc(drill.org)}" +
            "&month=${enc(drill.month?.toString())}"
    }
}

/**
 * 底部导航：今天 / 课程 / 统计 / 我的。
 *
 * 打卡不再是独立 tab——它是一个动作，不是一页内容：「今天」页每节课后面直接挂
 * 「已上」按钮，进来就能打；月历与补打卡降为二级页（点「全部记录 ›」进入）。
 * 这样首页不必再堆课程列表，和「课程」tab 彻底分工。
 * 「今天」还有没打的卡时挂一个小角标。
 */
private val navItems = listOf(
    GlassNavItem(Routes.HOME, "今天", Icons.Rounded.Home),
    GlassNavItem(Routes.COURSES, "课程", Icons.Rounded.MenuBook),
    GlassNavItem(Routes.STATS, "统计", Icons.Rounded.PieChart),
    GlassNavItem(Routes.MINE, "我的", Icons.Rounded.Person),
)

private val barRoutes = setOf(Routes.HOME, Routes.COURSES, Routes.STATS, Routes.MINE)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in barRoutes

    // 当前落在哪个底栏 tab 上：切 tab 时要用它当 popUpTo 的锚点（见 navigateTab 注释）。
    var currentTab by remember { mutableStateOf(Routes.HOME) }
    LaunchedEffect(currentRoute) {
        currentRoute?.takeIf { it in barRoutes }?.let { currentTab = it }
    }

    // 四个底栏 tab 上按返回键 = 直接退出应用，不在 tab 之间来回跳。
    // 返回栈的清理交给 navigateTab；这里再兜一层，让「在 tab 页按返回就退出」
    // 这个行为不受返回栈当时是什么形状的影响。二级页 showBottomBar 为 false、
    // 不注册，照常走 NavController 的 popBackStack 逐级返回。
    val context = LocalContext.current
    BackHandler(enabled = showBottomBar) {
        (context as? Activity)?.finish()
    }

    // 「添加孩子」直达通道：首页 / 课程页的空状态按钮直接弹表单，
    // 不用再先进「孩子档案」页点一次——添加孩子是第一步，不该绕路。
    val childrenVm: ChildrenViewModel = hiltViewModel()
    var showAddChild by remember { mutableStateOf(false) }

    // 打卡角标：今天还有几节没打卡
    val badgeVm: CheckInBadgeViewModel = hiltViewModel()
    val pendingToday by badgeVm.pendingToday.collectAsStateWithLifecycle()

    // 通知点击进来 → 直接跳打卡页（二级页，带返回）
    val openCheckInSignal by ReminderNav.openCheckIn.collectAsStateWithLifecycle()
    LaunchedEffect(openCheckInSignal) {
        if (openCheckInSignal > 0) {
            navController.navigate(Routes.CHECKIN) { launchSingleTop = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 极光底色画在最底层：所有玻璃条、玻璃卡片都靠它透出颜色
        AuroraBackground()

        Scaffold(
            containerColor = Color.Transparent,
            // 系统栏由各页面自己处理，这里不额外加内边距
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // 底栏不用 Scaffold 的槽位：悬浮胶囊要盖在内容之上，
            // 内容照常铺到屏幕最底，靠各页面的 bottomBarInset() 避开。
            bottomBar = {},
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            // 横屏时左右可能有系统栏，内容要避开；底部留白交给各页面自己按玻璃条高度处理
            val systemBars = WindowInsets.systemBars.asPaddingValues()
            // Scaffold 的容器是透明的，contentColor 会被解析成 Unspecified，
            // 这里显式补回 onSurface，否则所有没指定颜色的文字在深色模式下会变成黑色。
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(
                        start = systemBars.calculateStartPadding(layoutDirection) + innerPadding.calculateStartPadding(layoutDirection),
                        end = systemBars.calculateEndPadding(layoutDirection) + innerPadding.calculateEndPadding(layoutDirection),
                    ),
                ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenCourse = { navController.navigate(Routes.course(it)) },
                        onOpenSync = { navController.navigate(Routes.SYNC) },
                        onGoCourses = { navController.navigateTab(Routes.COURSES, currentTab) },
                        onOpenCheckIn = { navController.navigate(Routes.CHECKIN) },
                        onAddChild = { showAddChild = true },
                        onOpenMonthPayments = { navController.navigate(Routes.payments(StatsDrill(period = StatsPeriod.MONTH))) },
                    )
                }
                composable(Routes.COURSES) {
                    CoursesTabScreen(
                        onOpenCourse = { navController.navigate(Routes.course(it)) },
                        onOpenChildren = { navController.navigate(Routes.CHILDREN) },
                        onAddChild = { showAddChild = true },
                        onOpenClosed = { navController.navigate(Routes.closedCourses(it)) },
                    )
                }
                composable(
                    route = Routes.CLOSED_COURSES,
                    arguments = listOf(
                        navArgument("childId") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    ClosedCoursesScreen(
                        childId = entry.arguments?.getString("childId")?.takeIf { it.isNotBlank() },
                        onBack = { navController.popBackStack() },
                        onOpenCourse = { navController.navigate(Routes.course(it)) },
                    )
                }
                composable(Routes.CHECKIN) {
                    CheckInScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.STATS) {
                    StatsScreen(
                        onDrill = { drill -> navController.navigate(Routes.payments(drill)) },
                    )
                }
                composable(Routes.MINE) {
                    MineScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenChildren = { navController.navigate(Routes.CHILDREN) },
                        onAddChild = { showAddChild = true },
                        onOpenChildPayments = { childId ->
                            navController.navigate(Routes.payments(StatsDrill(childId = childId)))
                        },
                        onOpenDataSource = {
                            navController.navigate(Routes.settingsModule(SettingsModules.DEMO))
                        },
                    )
                }
                composable(
                    route = Routes.PAYMENTS,
                    arguments = listOf(
                        navArgument("period") { type = NavType.StringType; defaultValue = "ALL" },
                        navArgument("childId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("category") { type = NavType.StringType; defaultValue = "" },
                        navArgument("org") { type = NavType.StringType; defaultValue = "" },
                        navArgument("month") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    PaymentsDetailScreen(
                        onOpenCourse = { navController.navigate(Routes.course(it)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.CHILDREN) {
                    ChildrenScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.COURSE,
                    arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
                ) { entry ->
                    val courseId = entry.arguments?.getString("courseId").orEmpty()
                    CourseDetailRoute(
                        courseId = courseId,
                        onBack = { navController.popBackStack() },
                        // 续费开新一期后会拿到一条新课程，直接替掉当前这一页，
                        // 否则返回栈里会叠着"第 1 期 → 第 2 期"两份
                        onOpenCourse = { newId ->
                            if (newId != courseId) {
                                navController.navigate(Routes.course(newId)) {
                                    popUpTo(Routes.COURSE) { inclusive = true }
                                }
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSyncDetail = { navController.navigate(Routes.SYNC) },
                        onOpenModule = { module -> navController.navigate(Routes.settingsModule(module)) },
                    )
                }
                composable(
                    route = Routes.SETTINGS_MODULE,
                    arguments = listOf(navArgument("module") { type = NavType.StringType }),
                ) { entry ->
                    when (entry.arguments?.getString("module")) {
                        SettingsModules.APPEARANCE -> AppearanceScreen(
                            onBack = { navController.popBackStack() },
                        )
                        SettingsModules.REMINDER -> ReminderScreen(
                            onBack = { navController.popBackStack() },
                        )
                        SettingsModules.SYNC -> SyncSettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenSyncDetail = { navController.navigate(Routes.SYNC) },
                        )
                        SettingsModules.RULES -> RulesScreen(
                            onBack = { navController.popBackStack() },
                        )
                        SettingsModules.DATA -> DataScreen(
                            onBack = { navController.popBackStack() },
                        )
                        SettingsModules.DEMO -> DataSourceScreen(
                            onBack = { navController.popBackStack() },
                            onOpenData = { navController.navigate(Routes.settingsModule(SettingsModules.DATA)) },
                        )
                        SettingsModules.ABOUT -> AboutScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.SYNC) {
                    SyncDetailScreen(onBack = { navController.popBackStack() })
                }
                }
            }
        }

        // 悬浮玻璃底栏：盖在内容之上，铺满宽度、离底边有距离
        if (showBottomBar) {
            GlassNavBar(
                // 「今天」tab 挂「今天还有几节没打卡」的小圆点角标
                items = navItems.map {
                    if (it.route == Routes.HOME) it.copy(badge = pendingToday) else it
                },
                currentRoute = currentRoute.orEmpty(),
                onSelect = { navController.navigateTab(it, currentTab) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // 「添加孩子」表单：首页 / 课程页的空状态按钮直达
        if (showAddChild) {
            ChildFormSheet(
                initial = null,
                onDismiss = { showAddChild = false },
                onSave = { name, nickname, birthday, color, note ->
                    childrenVm.saveChild(null, name, nickname, birthday, color, note)
                    showAddChild = false
                },
            )
        }
    }
}

/**
 * 切 tab：新 tab 就是「这一截到头了」——返回键在它上面就直接退出应用。
 *
 * popUpTo 的锚点必须是**当前所在的 tab**，不能写死成 startDestination。
 * 写死的话只有第一次切换有效：那时起始页「今天」还在栈底，inclusive 能把它连带上层
 * 一起弹掉；可它一旦出栈，之后的每次切换都变成了「在一个找不到锚点的栈上 navigate」，
 * Navigation 弹不掉任何东西，页面只进不出——
 *   依次点 课程 → 统计 → 我的，栈里就是 [课程, 统计, 我的]，
 *   按返回键会在三个 tab 之间来回跳，要按三次才退出去。
 * 锚点跟着当前 tab 走，每次切换都把自己连同上面压着的二级页一起弹掉，
 * 栈里始终只剩刚选中的那一个 tab。
 * saveState / restoreState 照常：栈清了不等于状态丢了，切回来滚动位置和筛选还在。
 */
private fun NavHostController.navigateTab(route: String, currentTab: String) {
    navigate(route) {
        popUpTo(currentTab) {
            inclusive = true
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
