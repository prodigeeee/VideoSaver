package com.example.videosaver

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable data object Home     : NavKey
@Serializable data object Library  : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Main     : NavKey // template compat

