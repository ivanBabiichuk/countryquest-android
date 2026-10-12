package io.github.ivanbabiichuk.countryquest.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.ivanbabiichuk.countryquest.feature.home.HomeScreen
import io.github.ivanbabiichuk.countryquest.feature.quiz.QuizScreen
import io.github.ivanbabiichuk.countryquest.feature.results.ResultsScreen
import io.github.ivanbabiichuk.countryquest.feature.statistics.StatisticsScreen

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
            HomeScreen(
                onStartQuiz = {
                    navController.navigate(QuizRoute){
                        launchSingleTop = true
                    }
                },
                onOpenStatistics = {
                    navController.navigate(StatisticsRoute){
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<QuizRoute> {
            QuizScreen(
                onFinishQuiz = {
                    navController.navigate(ResultsRoute) {
                        popUpTo<QuizRoute> {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<ResultsRoute> {
            ResultsScreen(
                onPlayAgain = {
                    navController.navigate(QuizRoute) {
                        popUpTo<HomeRoute>()
                        launchSingleTop = true
                    }
                },
                onGoHome = {
                    navController.popBackStack<HomeRoute>(
                        inclusive = false,
                    )
                },
            )
        }

        composable<StatisticsRoute> {
            StatisticsScreen(
                onGoBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}