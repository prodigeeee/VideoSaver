package com.example.videosaver

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import com.example.videosaver.theme.*
import com.example.videosaver.ui.home.HomeScreen
import com.example.videosaver.ui.library.LibraryScreen
import com.example.videosaver.ui.settings.SettingsScreen
import androidx.compose.ui.text.style.TextOverflow

private data class NavItem(
    val key: Any,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
)

@Composable
fun isInPipMode(): Boolean {
    val activity = LocalContext.current as? androidx.activity.ComponentActivity ?: return false
    var pipMode by remember { mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) activity.isInPictureInPictureMode else false) }
    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            pipMode = info.isInPictureInPictureMode
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }
    return pipMode
}

@Composable
fun MainNavigation() {
    var currentRoute by remember { mutableStateOf<Any>(Home) }

    val navItems = listOf(
        NavItem(Home,     Icons.Rounded.Download,     label = "Télécharger"),
        NavItem(Library,  Icons.Rounded.VideoLibrary, label = "Fichiers"),
        NavItem(Settings, Icons.Rounded.Settings,     label = "Réglages"),
    )

    val inPip = isInPipMode()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (!inPip) {
                    PremiumBottomBar(
                        items        = navItems,
                        currentRoute = currentRoute,
                        onSelect     = { currentRoute = it },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState  = currentRoute,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "screen_transition",
                ) { route ->
                    when (route) {
                        is Home     -> HomeScreen()
                        is Library  -> LibraryScreen()
                        is Settings -> SettingsScreen()
                        else        -> HomeScreen()
                    }
                }
            }
        }
    }
}



// ─── Premium Bottom Bar ───────────────────────────────────────────────────────

@Composable
private fun PremiumBottomBar(
    items: List<NavItem>,
    currentRoute: Any,
    onSelect: (Any) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Top amber glow line
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Amber.copy(0.35f), Amber.copy(0.35f), Color.Transparent)
                    ),
                    topLeft = Offset(0f, 0f),
                    size    = Size(size.width, 1.dp.toPx()),
                )
            }
            .background(Background.copy(alpha = 0.97f))
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute::class == item.key::class
                BottomNavItem(item = item, selected = selected, onClick = { onSelect(item.key) })
            }
        }
    }
}

@Composable
private fun BottomNavItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val iconTint   by animateColorAsState(if (selected) Amber else TextSecondary, label = "icon_tint")
    val labelColor by animateColorAsState(if (selected) Amber else Color.Transparent, label = "label_color")
    val bgColor    by animateColorAsState(if (selected) AmberGlow else Color.Transparent, label = "bg_color")

    Surface(
        onClick  = onClick,
        color    = Color.Transparent,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    imageVector      = item.icon,
                    contentDescription = item.label,
                    tint             = iconTint,
                    modifier         = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.label,
                style    = MaterialTheme.typography.labelSmall.copy(color = labelColor),
                maxLines = 1,
            )
        }
    }
}
