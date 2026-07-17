package com.example.videosaver.ui.main

// This file is kept for compilation compatibility with the template.
// The actual main screen is now com.example.videosaver.ui.home.HomeScreen.

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.example.videosaver.ui.home.HomeScreen

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    HomeScreen(modifier = modifier)
}
