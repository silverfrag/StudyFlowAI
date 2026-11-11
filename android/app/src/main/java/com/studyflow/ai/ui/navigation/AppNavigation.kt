package com.studyflow.ai.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.studyflow.ai.data.local.AppPreferences
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.ui.screens.assistant.AssistantScreen
import com.studyflow.ai.ui.screens.courses.CoursesScreen
import com.studyflow.ai.ui.screens.documents.CourseDetailScreen
import com.studyflow.ai.ui.screens.home.HomeScreen
import com.studyflow.ai.ui.screens.profile.ProfileScreen
import com.studyflow.ai.ui.screens.progress.ProgressScreen
import com.studyflow.ai.ui.screens.quiz.QuizScreen
import com.studyflow.ai.ui.screens.result.ResultScreen
import com.studyflow.ai.ui.screens.viewer.DocumentViewerScreen
import com.studyflow.ai.ui.theme.studyFlowSemanticColors

private fun buildDocumentViewerRoute(
    documentId: String,
    page: Int = 1,
    title: String = "",
    suggestedPages: List<Int> = emptyList(),
    topic: String = ""
): String {
    val safePage = page.coerceAtLeast(1)
    val encodedTitle = Uri.encode(title)
    val pagesCsv = suggestedPages.filter { it >= 1 }.distinct().joinToString(",")
    val encodedTopic = Uri.encode(topic)
    return "document_viewer/$documentId?page=$safePage&title=$encodedTitle&pages=$pagesCsv&topic=$encodedTopic"
}

sealed class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavTab(
        route = "home",
        label = "Trang chủ",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Courses : BottomNavTab(
        route = "courses",
        label = "Môn học",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook
    )

    data object Assistant : BottomNavTab(
        route = "assistant",
        label = "AI",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )

    data object Progress : BottomNavTab(
        route = "progress",
        label = "Tiến độ",
        selectedIcon = Icons.AutoMirrored.Filled.TrendingUp,
        unselectedIcon = Icons.AutoMirrored.Outlined.TrendingUp
    )
}

class AppNavigationStateViewModel : ViewModel() {
    var latestQuizResult by mutableStateOf<QuizSubmitResponse?>(null)
    var latestQuizCourseId by mutableStateOf<String?>(null)
    var preferredAssistantCourseId by mutableStateOf<String?>(null)
}

@Composable
fun StudyFlowApp(
    sessionState: AppNavigationStateViewModel = viewModel(),
    appPreferences: AppPreferences = AppPreferences.getInstance(LocalContext.current),
    isDarkTheme: Boolean = false
) {
    val navController = rememberNavController()
    val themeMode by appPreferences.themeMode.collectAsState()
    val studentProfile by appPreferences.profile.collectAsState()
    val tabs = remember {
        listOf(
            BottomNavTab.Home,
            BottomNavTab.Courses,
            BottomNavTab.Assistant,
            BottomNavTab.Progress
        )
    }
    val tabRoutes = remember(tabs) { tabs.map { it.route }.toSet() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Lưu tạm kết quả Quiz vừa nộp (giữ qua configuration change như xoay màn hình / đổi theme)
    val latestQuizResult = sessionState.latestQuizResult
    val latestQuizCourseId = sessionState.latestQuizCourseId
    val preferredAssistantCourseId = sessionState.preferredAssistantCourseId

    val showBottomBar = tabs.any { it.route == currentRoute }
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar && !isImeVisible,
                enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 2 }
            ) {
                FloatingPillBottomDock(
                    tabs = tabs,
                    currentDestination = currentDestination,
                    onTabSelected = { tab ->
                        if (tab == BottomNavTab.Assistant) {
                            sessionState.preferredAssistantCourseId = null
                        }
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavTab.Home.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = {
                if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes) {
                    fadeIn(tween(210)) + scaleIn(initialScale = 0.985f, animationSpec = tween(210))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(260)
                    ) + fadeIn(tween(220))
                }
            },
            exitTransition = {
                if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes) {
                    fadeOut(tween(170))
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(240),
                        targetOffset = { it / 5 }
                    ) + fadeOut(tween(200))
                }
            },
            popEnterTransition = {
                if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes) {
                    fadeIn(tween(200))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(250),
                        initialOffset = { it / 5 }
                    ) + fadeIn(tween(210))
                }
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(250)
                ) + fadeOut(tween(200))
            }
        ) {
            composable(BottomNavTab.Home.route) {
                HomeScreen(
                    profile = studentProfile,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkMode = { appPreferences.toggleLightDark(isDarkTheme) },
                    onOpenProfile = {
                        navController.navigate("profile") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCourses = {
                        navController.navigate(BottomNavTab.Courses.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAssistant = {
                        navController.navigate(BottomNavTab.Assistant.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProgress = {
                        navController.navigate(BottomNavTab.Progress.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenCourseDetail = { courseId ->
                        navController.navigate("course_detail/$courseId")
                    }
                )
            }

            composable("profile") {
                ProfileScreen(
                    profile = studentProfile,
                    themeMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    onSelectThemeMode = { mode -> appPreferences.setThemeMode(mode) },
                    onToggleDarkMode = { appPreferences.toggleLightDark(isDarkTheme) },
                    onUpdateProfile = { name, goal, minutes ->
                        appPreferences.updateProfile(name, goal, minutes)
                    },
                    onBack = { navController.popBackStack() },
                    onOpenCourseDetail = { courseId ->
                        navController.navigate("course_detail/$courseId")
                    },
                    onNavigateToCourses = {
                        navController.navigate(BottomNavTab.Courses.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAssistant = {
                        navController.navigate(BottomNavTab.Assistant.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProgress = {
                        navController.navigate(BottomNavTab.Progress.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(BottomNavTab.Courses.route) {
                CoursesScreen(
                    onOpenCourseDetail = { courseId ->
                        navController.navigate("course_detail/$courseId")
                    }
                )
            }

            composable(BottomNavTab.Assistant.route) {
                AssistantScreen(
                    preferredCourseId = preferredAssistantCourseId,
                    onNavigateToCourses = {
                        navController.navigate(BottomNavTab.Courses.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenCitationDocument = { docId, page, title ->
                        navController.navigate(
                            buildDocumentViewerRoute(
                                documentId = docId,
                                page = page,
                                title = title,
                                suggestedPages = listOf(page)
                            )
                        )
                    }
                )
            }

            composable(BottomNavTab.Progress.route) {
                ProgressScreen(
                    onOpenCourseDetail = { courseId ->
                        navController.navigate("course_detail/$courseId")
                    },
                    onOpenDocument = { docId, page, title, pages, topic ->
                        navController.navigate(
                            buildDocumentViewerRoute(
                                documentId = docId,
                                page = page,
                                title = title,
                                suggestedPages = pages,
                                topic = topic
                            )
                        )
                    },
                    onStartPracticeQuiz = { quizId, courseId ->
                        sessionState.latestQuizCourseId = courseId
                        navController.navigate("quiz/$quizId")
                    },
                    onNavigateToCourses = {
                        navController.navigate(BottomNavTab.Courses.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(
                route = "course_detail/{courseId}",
                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
                CourseDetailScreen(
                    courseId = courseId,
                    onBack = { navController.popBackStack() },
                    onOpenAssistantForCourse = { cid ->
                        sessionState.preferredAssistantCourseId = cid
                        navController.navigate(BottomNavTab.Assistant.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onStartQuiz = { quizId ->
                        sessionState.latestQuizCourseId = courseId
                        navController.navigate("quiz/$quizId")
                    },
                    onOpenDocument = { docId, title ->
                        navController.navigate(
                            buildDocumentViewerRoute(
                                documentId = docId,
                                page = 1,
                                title = title
                            )
                        )
                    }
                )
            }

            composable(
                route = "quiz/{quizId}",
                arguments = listOf(navArgument("quizId") { type = NavType.StringType })
            ) { backStackEntry ->
                val quizId = backStackEntry.arguments?.getString("quizId") ?: return@composable
                QuizScreen(
                    quizId = quizId,
                    onBack = { navController.popBackStack() },
                    onQuizSubmitted = { result ->
                        sessionState.latestQuizResult = result
                        navController.navigate("quiz_result") {
                            popUpTo("quiz/$quizId") { inclusive = true }
                        }
                    }
                )
            }

            composable("quiz_result") {
                val courseIdForBack = latestQuizCourseId
                ResultScreen(
                    result = latestQuizResult,
                    onNavigateToHome = {
                        val popped = navController.popBackStack(BottomNavTab.Home.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(BottomNavTab.Home.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onNavigateToProgress = {
                        val popped = navController.popBackStack(BottomNavTab.Progress.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(BottomNavTab.Progress.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onNavigateToCourseDetail = courseIdForBack?.let { cid ->
                        {
                            val popped = navController.popBackStack("course_detail/$cid", inclusive = false)
                            if (!popped) {
                                navController.navigate("course_detail/$cid") {
                                    popUpTo("quiz_result") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onOpenCitationDocument = { docId, page, title, pages, topic ->
                        navController.navigate(
                            buildDocumentViewerRoute(
                                documentId = docId,
                                page = page,
                                title = title,
                                suggestedPages = pages,
                                topic = topic
                            )
                        )
                    }
                )
            }

            composable(
                route = "document_viewer/{documentId}?page={page}&title={title}&pages={pages}&topic={topic}",
                arguments = listOf(
                    navArgument("documentId") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = 1 },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("pages") { type = NavType.StringType; defaultValue = "" },
                    navArgument("topic") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                val initialPage = (backStackEntry.arguments?.getInt("page") ?: 1).coerceAtLeast(1)
                val title = backStackEntry.arguments?.getString("title").orEmpty()
                val pagesRaw = backStackEntry.arguments?.getString("pages").orEmpty()
                val topic = backStackEntry.arguments?.getString("topic").orEmpty()
                val parsedPages = remember(pagesRaw) {
                    pagesRaw.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it >= 1 }
                        .distinct()
                        .sorted()
                }
                DocumentViewerScreen(
                    documentId = documentId,
                    initialPage = initialPage,
                    documentTitle = title,
                    suggestedPages = parsedPages,
                    focusTopic = topic,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun FloatingPillBottomDock(
    tabs: List<BottomNavTab>,
    currentDestination: androidx.navigation.NavDestination?,
    onTabSelected: (BottomNavTab) -> Unit
) {
    val semanticColors = studyFlowSemanticColors()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = semanticColors.floatingDockContainer,
            border = BorderStroke(1.dp, semanticColors.floatingDockBorder),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) semanticColors.floatingDockActivePill else Color.Transparent,
                        animationSpec = tween(220),
                        label = "dockPillBg"
                    )
                    val contentTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF9EA6C4),
                        animationSpec = tween(220),
                        label = "dockTint"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Surface(
                        color = pillColor,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .then(
                                if (isSelected) Modifier.weight(1.75f) else Modifier.weight(1f)
                            )
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .semantics {
                                contentDescription = tab.label
                                selected = isSelected
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onTabSelected(tab) }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .animateContentSize(animationSpec = tween(200))
                                .padding(horizontal = if (isSelected) 10.dp else 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null,
                                tint = contentTint,
                                modifier = Modifier.size(if (isSelected) 19.dp else 22.dp)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
