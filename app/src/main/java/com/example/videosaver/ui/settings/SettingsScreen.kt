package com.example.videosaver.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.res.painterResource
import com.example.videosaver.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videosaver.data.CookieManager
import com.example.videosaver.theme.*
import com.example.videosaver.ui.components.AmberButton
import com.example.videosaver.ui.components.GlassCard
import com.example.videosaver.ui.download.DownloadViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory(LocalContext.current)),
) {
    val context = LocalContext.current
    val cookieManager = remember { CookieManager(context) }
    
    val prefs = remember {
        context.getSharedPreferences("videosaver_prefs", android.content.Context.MODE_PRIVATE)
    }
    var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden_files", false)) }
    var themePref by remember { mutableStateOf(prefs.getString("theme_pref", "system") ?: "system") }

    var isUpdating    by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var hasCookies    by remember { mutableStateOf(cookieManager.hasCookies) }
    var cookieCount   by remember { mutableStateOf(cookieManager.cookieCount) }
    var cookieImportedAt by remember { mutableStateOf(cookieManager.importedAt()) }
    var cookieError   by remember { mutableStateOf<String?>(null) }
    var cookieSuccess by remember { mutableStateOf<String?>(null) }

    // File picker for cookies.txt
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@rememberLauncherForActivityResult
            // Validate and import
            val lines = content.lines()
            val validLines = lines.filter { l ->
                l.isBlank() || l.startsWith("#") || l.split("\t").size >= 7
            }
            if (validLines.count { it.isNotBlank() && !it.startsWith("#") } == 0) {
                cookieError = "Format invalide — exportez en format Netscape depuis votre navigateur."
                return@rememberLauncherForActivityResult
            }
            val cookieFile = cookieManager.cookieFile
            cookieFile.parentFile?.mkdirs()
            cookieFile.writeText(validLines.joinToString("\n"))
            val now = System.currentTimeMillis()
            context.getSharedPreferences("videosaver_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putLong("cookies_imported_at", now).apply()
            hasCookies       = true
            cookieCount      = validLines.count { it.isNotBlank() && !it.startsWith("#") }
            cookieImportedAt = now
            cookieError      = null
            cookieSuccess    = "$cookieCount cookies importés avec succès ✓"
        } catch (e: Exception) {
            cookieError = "Erreur : ${e.message}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App Icon",
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Paramètres", style = MaterialTheme.typography.headlineLarge)
                Text("VideoSaver v1.0", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ── Moteur yt-dlp ──────────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Update, null, tint = Amber, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Moteur de téléchargement", style = MaterialTheme.typography.titleMedium)
                    Text("yt-dlp — supporte 1000+ sites", style = MaterialTheme.typography.bodySmall)
                    updateMessage?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(color = SuccessGreen))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AmberButton(
                text     = if (isUpdating) "Mise à jour en cours…" else "Mettre à jour yt-dlp",
                onClick  = { isUpdating = true; updateMessage = null },
                enabled  = !isUpdating,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp)) },
            )
        }

        // ── Cookies / Sessions ─────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = if (hasCookies) TealAccent.copy(0.08f) else AmberGlow) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Cookie, null, tint = if (hasCookies) TealAccent else TextSecondary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cookies de session", style = MaterialTheme.typography.titleMedium)
                        if (hasCookies) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = TealAccent.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text("ACTIF", style = MaterialTheme.typography.labelSmall.copy(color = TealAccent),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Permet de télécharger du contenu privé (Instagram privé, Twitter/X, etc.) " +
                        "en important vos cookies de navigateur.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    if (hasCookies && cookieImportedAt > 0) {
                        Spacer(Modifier.height(8.dp))
                        val date = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE)
                            .format(Date(cookieImportedAt))
                        Text(
                            "✓ $cookieCount cookies • Importés le $date",
                            style = MaterialTheme.typography.bodySmall.copy(color = TealAccent),
                        )
                    }

                    AnimatedVisibility(cookieError != null) {
                        Text(
                            cookieError ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    AnimatedVisibility(cookieSuccess != null) {
                        Text(
                            cookieSuccess ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = SuccessGreen),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // How to export guide
            Surface(color = GlassWhite, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Comment obtenir votre fichier cookies.txt :", style = MaterialTheme.typography.labelMedium.copy(color = Amber))
                    Text("1. Installez \"Get cookies.txt LOCALLY\" sur Chrome", style = MaterialTheme.typography.bodySmall)
                    Text("2. Connectez-vous à Instagram / X sur votre navigateur", style = MaterialTheme.typography.bodySmall)
                    Text("3. Cliquez sur l'extension → Export → Netscape format", style = MaterialTheme.typography.bodySmall)
                    Text("4. Transférez le fichier sur votre téléphone", style = MaterialTheme.typography.bodySmall)
                    Text("5. Importez-le ici ↓", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AmberButton(
                    text       = "Importer cookies.txt",
                    onClick    = { filePicker.launch(arrayOf("text/plain", "*/*")) },
                    modifier   = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)) },
                )
                if (hasCookies) {
                    OutlinedButton(
                        onClick = {
                            cookieManager.clearCookies()
                            hasCookies       = false
                            cookieCount      = 0
                            cookieImportedAt = 0L
                            cookieSuccess    = null
                            cookieError      = null
                        },
                        border  = BorderStroke(1.dp, ErrorRed.copy(0.5f)),
                        shape   = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Effacer", style = MaterialTheme.typography.labelMedium.copy(color = ErrorRed))
                    }
                }
            }
        }

        // ── Préférences ─────────────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Visibility, null, tint = Amber, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fichiers cachés", style = MaterialTheme.typography.titleMedium)
                    Text("Afficher les dossiers et fichiers cachés dans la navigation", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = showHiddenFiles,
                    onCheckedChange = { 
                        showHiddenFiles = it
                        prefs.edit().putBoolean("show_hidden_files", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Amber,
                        checkedTrackColor = Amber.copy(alpha = 0.5f),
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            Divider(color = GlassBorder)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, null, tint = Amber, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Thème de l'application", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = themePref == "light",
                            onClick = {
                                themePref = "light"
                                prefs.edit().putString("theme_pref", "light").apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = AmberGlow,
                                activeContentColor = Amber,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = TextSecondary,
                                activeBorderColor = AmberDim,
                                inactiveBorderColor = GlassBorder
                            )
                        ) {
                            Text("Clair")
                        }
                        SegmentedButton(
                            selected = themePref == "system",
                            onClick = {
                                themePref = "system"
                                prefs.edit().putString("theme_pref", "system").apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = AmberGlow,
                                activeContentColor = Amber,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = TextSecondary,
                                activeBorderColor = AmberDim,
                                inactiveBorderColor = GlassBorder
                            )
                        ) {
                            Text("Système")
                        }
                        SegmentedButton(
                            selected = themePref == "dark",
                            onClick = {
                                themePref = "dark"
                                prefs.edit().putString("theme_pref", "dark").apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = AmberGlow,
                                activeContentColor = Amber,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = TextSecondary,
                                activeBorderColor = AmberDim,
                                inactiveBorderColor = GlassBorder
                            )
                        ) {
                            Text("Sombre")
                        }
                    }
                }
            }
        }

        // ── Sites supportés ────────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Language, null, tint = Amber, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Sites supportés", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "🎬 YouTube"     to "Vidéos, Shorts, Playlists",
                        "🎵 TikTok"      to "Vidéos, Stories",
                        "📷 Instagram"   to "Reels, Posts (cookies requis si privé)",
                        "🔞 Redgifs"     to "Vidéos GIF",
                        "🐦 X (Twitter)" to "Vidéos, GIFs (cookies requis)",
                        "🎥 Vimeo"       to "Toutes résolutions",
                        "📺 Dailymotion" to "Vidéos HD",
                        "+ 1000 autres"  to "Twitch, Reddit, SoundCloud…",
                    ).forEach { (site, desc) ->
                        Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(site, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                            Text(" — $desc", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ── À propos ───────────────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = Amber, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("À propos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "VideoSaver est une application personnelle et privée. " +
                        "Aucune donnée n'est collectée ni envoyée à des serveurs externes. " +
                        "Tous les téléchargements s'effectuent directement depuis votre appareil.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Moteur : yt-dlp • FFmpeg • aria2c", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
