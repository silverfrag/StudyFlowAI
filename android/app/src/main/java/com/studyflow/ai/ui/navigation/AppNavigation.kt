package com.studyflow.ai.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.ui.screens.assistant.AssistantScreen
import com.studyflow.ai.ui.screens.courses.CoursesScreen
import com.studyflow.ai.ui.screens.documents.CourseDetailScreen
import com.studyflow.ai.ui.screens.home.HomeScreen
import com.studyflow.ai.ui.screens.progress.ProgressScreen
import com.studyflow.ai.ui.screens.quiz.QuizScreen
import com.studyflow.ai.ui.screens.result.ResultScreen
import com.studyflow.ai.ui.screens.viewer.DocumentViewerScreen

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

sealed class BottomNavTab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : BottomNavTab("home", "Trang chủ", Icons.Outlined.Home)
    data object Courses : BottomNavTab("courses", "Môn học", Icons.AutoMirrored.Outlined.MenuBook)
    data object Assistant : BottomNavTab("assistant", "AI", Icons.Outlined.SmartToy)
    data object Progress : BottomNavTab("progress", "Tiến độ", Icons.AutoMirrored.Outlined.TrendingUp)
}

@Composable
fun StudyFlowApp() {
    val navController = rememberNavController()
    val tabs = listOf(
        BottomNavTab.Home,
        BottomNavTab.Courses,
        BottomNavTab.Assistant,
        BottomNavTab.Progress
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Lưu tạm kết quả Quiz vừa nộp để hiển thị ở màn hình Result
    var latestQuizResult by remember { mutableStateOf<QuizSubmitResponse?>(null) }
    var latestQuizCourseId by remember { mutableStateOf<String?>(null) }
    var preferredAssistantCourseId by remember { mutableStateOf<String?>(null) }

    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            onClick = {
                                if (tab == BottomNavTab.Assistant) {
                                    preferredAssistantCourseId = null
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
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavTab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavTab.Home.route) {
                HomeScreen(
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
                        latestQuizCourseId = courseId
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
                        preferredAssistantCourseId = cid
                        navController.navigate(BottomNavTab.Assistant.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onStartQuiz = { quizId ->
                        latestQuizCourseId = courseId
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
                        latestQuizResult = result
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

