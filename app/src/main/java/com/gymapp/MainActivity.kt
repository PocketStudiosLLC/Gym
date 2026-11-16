package com.gymapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymapp.data.ThemeMode
import com.gymapp.ui.analytics.AnalyticsScreen
import com.gymapp.ui.day.DayScreen
import com.gymapp.ui.exercise.ExerciseEditScreen
import com.gymapp.ui.exercise.ExercisePickerScreen
import com.gymapp.ui.routine.RoutineEditScreen
import com.gymapp.ui.routine.StartRoutineScreen
import com.gymapp.ui.settings.SettingsScreen
import com.gymapp.ui.theme.GymTheme
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Read the theme before the first frame so the app never flashes the wrong colors.
        val initial = runBlocking { container.settings.current() }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initial)
            LaunchedEffect(settings.mode) {
                val style = if (settings.mode == ThemeMode.DARK) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GymTheme(settings.mode, settings.color) {
                NotificationPermissionPrompt()
                AppNavigation()
            }
        }
    }
}

/** Asks for notification permission (Android 13+) the first time the stopwatch runs. */
@Composable
private fun NotificationPermissionPrompt() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val stopwatch by context.container.stopwatch.state.collectAsStateWithLifecycle()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(stopwatch != null) {
        if (stopwatch != null && !asked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

object Routes {
    const val DAY = "day"
    fun picker(date: LocalDate) = "picker/${date.toEpochDay()}"
    fun newExercise(date: LocalDate, name: String = "") =
        "exercise/new?date=${date.toEpochDay()}&name=${android.net.Uri.encode(name)}"
    fun editExercise(id: Long) = "exercise/edit/$id"
    fun analytics(date: LocalDate) = "analytics/${date.toEpochDay()}"
    const val SETTINGS = "settings"
    const val ADD_MEAL = "food/meal/new"
    const val FOOD_ANALYTICS = "food/analytics"
    fun startRoutine(date: LocalDate) = "routines/${date.toEpochDay()}"
    const val NEW_ROUTINE = "routine/new"
    const val NEW_EXERCISE_FOR_ROUTINE = "routine/exercise/new"
    fun editRoutine(id: Long) = "routine/edit/$id"
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DAY) {
        composable(Routes.DAY) {
            DayScreen(
                onStartExercise = { nav.navigate(Routes.picker(it)) },
                onNewExercise = { nav.navigate(Routes.newExercise(it)) },
                onStartRoutine = { nav.navigate(Routes.startRoutine(it)) },
                onEditExercise = { nav.navigate(Routes.editExercise(it)) },
                onAnalytics = { nav.navigate(Routes.analytics(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onAddMeal = { nav.navigate(Routes.ADD_MEAL) },
                onFoodAnalytics = { nav.navigate(Routes.FOOD_ANALYTICS) },
            )
        }
        composable(
            "picker/{date}",
            arguments = listOf(navArgument("date") { type = NavType.LongType }),
        ) { back ->
            val date = LocalDate.ofEpochDay(back.arguments!!.getLong("date"))
            ExercisePickerScreen(
                date = date,
                onBack = { nav.popBackStack() },
                onAdded = { nav.popBackStack(Routes.DAY, inclusive = false) },
                onCreateNew = { name -> nav.navigate(Routes.newExercise(date, name)) },
                onEdit = { nav.navigate(Routes.editExercise(it)) },
            )
        }
        composable(
            "exercise/new?date={date}&name={name}",
            arguments = listOf(
                navArgument("date") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { back ->
            ExerciseEditScreen(
                exerciseId = null,
                date = LocalDate.ofEpochDay(back.arguments!!.getLong("date")),
                initialName = back.arguments!!.getString("name").orEmpty(),
                onDone = { addedToDay ->
                    if (addedToDay) nav.popBackStack(Routes.DAY, inclusive = false) else nav.popBackStack()
                },
            )
        }
        composable(
            "exercise/edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { back ->
            ExerciseEditScreen(
                exerciseId = back.arguments!!.getLong("id"),
                date = null,
                initialName = "",
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            "analytics/{date}",
            arguments = listOf(navArgument("date") { type = NavType.LongType }),
        ) { back ->
            AnalyticsScreen(
                initialDate = LocalDate.ofEpochDay(back.arguments!!.getLong("date")),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "routines/{date}",
            arguments = listOf(navArgument("date") { type = NavType.LongType }),
        ) { back ->
            StartRoutineScreen(
                date = LocalDate.ofEpochDay(back.arguments!!.getLong("date")),
                onBack = { nav.popBackStack() },
                onStarted = { nav.popBackStack(Routes.DAY, inclusive = false) },
                onNewRoutine = { nav.navigate(Routes.NEW_ROUTINE) },
                onEditRoutine = { nav.navigate(Routes.editRoutine(it)) },
            )
        }
        composable(Routes.NEW_ROUTINE) { back ->
            RoutineEditor(nav, back, routineId = null)
        }
        composable(
            "routine/edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { back ->
            RoutineEditor(nav, back, routineId = back.arguments!!.getLong("id"))
        }
        composable(Routes.ADD_MEAL) {
            com.gymapp.ui.food.AddMealScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.FOOD_ANALYTICS) {
            com.gymapp.ui.food.FoodAnalyticsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.NEW_EXERCISE_FOR_ROUTINE) {
            ExerciseEditScreen(
                exerciseId = null,
                date = null,
                initialName = "",
                onCreated = { id -> nav.previousBackStackEntry?.savedStateHandle?.set(NEW_EXERCISE_RESULT, id) },
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onNewRoutine = { nav.navigate(Routes.NEW_ROUTINE) },
                onEditRoutine = { nav.navigate(Routes.editRoutine(it)) },
            )
        }
    }
}

private const val NEW_EXERCISE_RESULT = "newExerciseId"

/** Routine editor, wired so "Add new exercise" creates an exercise and appends it to the routine. */
@Composable
private fun RoutineEditor(
    nav: androidx.navigation.NavHostController,
    back: androidx.navigation.NavBackStackEntry,
    routineId: Long?,
) {
    val handle = back.savedStateHandle
    val newId by handle.getStateFlow<Long?>(NEW_EXERCISE_RESULT, null).collectAsState()
    RoutineEditScreen(
        routineId = routineId,
        onDone = { nav.popBackStack() },
        onNewExercise = { nav.navigate(Routes.NEW_EXERCISE_FOR_ROUTINE) },
        newExerciseId = newId,
        onNewExerciseAdded = { handle[NEW_EXERCISE_RESULT] = null },
    )
}
