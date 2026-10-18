package io.github.ivanbabiichuk.countryquest.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.ivanbabiichuk.countryquest.feature.home.HomeRouteContent
import io.github.ivanbabiichuk.countryquest.feature.quiz.QuizRouteContent
import io.github.ivanbabiichuk.countryquest.feature.results.ResultsRouteContent
import io.github.ivanbabiichuk.countryquest.feature.statistics.StatisticsRouteContent

@Composable
fun CountryQuestNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ){
        composable <HomeRoute> {
            HomeRouteContent(
                onStartQuiz = { mode ->
                    navController.navigate(QuizRoute(mode)){
                        launchSingleTop = true
                    }
                },
                onOpenStatistics = {
                    navController.navigate(StatisticsRoute){
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<QuizRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<QuizRoute>()

            QuizRouteContent(
                mode = route.mode,
                onFinishQuiz = { gameId ->
                    navController.navigate(ResultsRoute(gameId = gameId)) {
                        popUpTo<QuizRoute> {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onExitQuiz = {
                    navController.popBackStack<HomeRoute>(inclusive = false)
                }
            )
        }

        composable<ResultsRoute> {
            ResultsRouteContent(
                onPlayAgain = { mode ->
                    navController.navigate(QuizRoute(mode)) {
                        popUpTo<HomeRoute> {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                onGoHome = {
                    navController.popBackStack<HomeRoute>(inclusive = false)
                }
            )
        }

        composable<StatisticsRoute> {
            StatisticsRouteContent(
                onGoBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}