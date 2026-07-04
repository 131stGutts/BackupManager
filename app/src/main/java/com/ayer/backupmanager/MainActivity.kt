package com.ayer.backupmanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ayer.backupmanager.ui.RuleEditScreen
import com.ayer.backupmanager.ui.RuleListScreen
import com.ayer.backupmanager.ui.RuleLogsScreen
import com.ayer.backupmanager.ui.RuleViewModel
import com.ayer.backupmanager.ui.theme.BackupManagerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RuleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("BackupManager", "MainActivity onCreate started")
        try {
            setContent {
                BackupManagerTheme {
                    val navController = rememberNavController()
                    val rules by viewModel.allRules.collectAsState()

                    NavHost(navController = navController, startDestination = "list") {
                        composable("list") {
                            RuleListScreen(
                                viewModel = viewModel,
                                onAddRule = { navController.navigate("edit") },
                                onEditRule = { rule -> navController.navigate("edit/${rule.id}") },
                                onViewLogs = { rule -> navController.navigate("logs/${rule.id}") }
                            )
                        }
                        composable("logs/{ruleId}") { backStackEntry ->
                            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull() ?: 0L
                            RuleLogsScreen(
                                viewModel = viewModel,
                                ruleId = ruleId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("edit") {
                            RuleEditScreen(
                                viewModel = viewModel,
                                rule = null,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("edit/{ruleId}") { backStackEntry ->
                            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull()
                            val rule = rules.find { it.id == ruleId }
                            RuleEditScreen(
                                viewModel = viewModel,
                                rule = rule,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
            Log.d("BackupManager", "MainActivity setContent finished")
        } catch (e: Exception) {
            Log.e("BackupManager", "Crash in MainActivity onCreate", e)
        }
    }
}
