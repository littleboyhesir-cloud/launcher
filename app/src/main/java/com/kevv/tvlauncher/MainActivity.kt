package com.kevv.tvlauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------- Data ----------

data class AppItem(
    val label: String,
    val pkg: String,
    val launch: Intent,
    val image: ImageBitmap,
    val isBanner: Boolean,
    val c1: Color,
    val c2: Color,
)

private val palette = listOf(
    Color(0xFF5B8DEF) to Color(0xFF2E3FA8),
    Color(0xFFFF7A59) to Color(0xFFC2335C),
    Color(0xFF34D399) to Color(0xFF0E7C66),
    Color(0xFFA78BFA) to Color(0xFF5B34C9),
    Color(0xFFFBBF24) to Color(0xFFD9701A),
    Color(0xFF38BDF8) to Color(0xFF1B6FB5),
)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val seen = HashSet<String>()
    val out = ArrayList<AppItem>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(cat)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val banner = runCatching { ri.activityInfo.loadBanner(pm) }.getOrNull()
            val drawable = banner ?: ri.loadIcon(pm)
            val bmp = runCatching {
                if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(160, 160)
            }.getOrNull() ?: continue
            val (c1, c2) = palette[abs(pkg.hashCode()) % palette.size]
            out.add(
                AppItem(
                    label = ri.loadLabel(pm).toString(),
                    pkg = pkg,
                    launch = launch,
                    image = bmp.asImageBitmap(),
                    isBanner = banner != null,
                    c1 = c1,
                    c2 = c2,
                )
            )
        }
    }
    return out.sortedBy { it.label.lowercase() }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun hidden(): Set<String> = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
    fun setHidden(s: Set<String>) = sp.edit().putStringSet("hidden", HashSet(s)).apply()
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherRoot(tick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

// ---------- Screens ----------

@Composable
fun LauncherRoot(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var hidden by remember { mutableStateOf(prefs.hidden()) }
    var all by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var manage by remember { mutableStateOf(false) }

    LaunchedEffect(tick) { all = withContext(Dispatchers.IO) { loadApps(ctx) } }

    fun toggle(a: AppItem) {
        hidden = if (a.pkg in hidden) hidden - a.pkg else hidden + a.pkg
        prefs.setHidden(hidden)
    }

    if (manage) {
        ManageScreen(all, hidden, onToggle = { toggle(it) }, onClose = { manage = false })
    } else {
        HomeScreen(
            apps = all.filter { it.pkg !in hidden },
            onOpen = { runCatching { ctx.startActivity(it.launch) } },
            onHide = {
                toggle(it)
                Toast.makeText(ctx, "${it.label} hidden. Bring it back from Manage apps.", Toast.LENGTH_LONG).show()
            },
            onManage = { manage = true },
            onSettings = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }
}

@Composable
fun HomeScreen(
    apps: List<AppItem>,
    onOpen: (AppItem) -> Unit,
    onHide: (AppItem) -> Unit,
    onManage: () -> Unit,
    onSettings: () -> Unit,
) {
    var focusedPkg by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    val shown = apps.firstOrNull { it.pkg == focusedPkg } ?: apps.firstOrNull()

    LaunchedEffect(apps.size) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2A49), Color(0xFF0A0F1E), Color.Black)))
            .background(
                Brush.radialGradient(
                    listOf(Color(0x553B6BFF), Color.Transparent),
                    center = Offset(1400f, 200f),
                    radius = 1100f,
                )
            )
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp, bottom = 40.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Home", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Clock()
            }

            Hero(shown)

            Spacer(Modifier.weight(1f))

            Text(
                "Apps",
                color = Color(0xCCFFFFFF),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 64.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 64.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                    AppCard(
                        app = app,
                        requester = if (i == 0) firstFocus else null,
                        onFocus = { focusedPkg = app.pkg },
                        onOpen = { onOpen(app) },
                        onHide = { onHide(app) },
                    )
                }
            }

            Row(
                Modifier.padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Tile("Settings", onSettings)
                Tile("Manage apps", onManage)
            }
        }
    }
}

@Composable
fun Hero(app: AppItem?) {
    Crossfade(targetState = app?.label, label = "hero") { label ->
        Column(
            Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 64.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (label != null) {
                Text(
                    label,
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Press OK to open   |   Hold OK to hide",
                    color = Color(0x99FFFFFF),
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    val fmt = remember { SimpleDateFormat("EEE d MMM   h:mm a", Locale.getDefault()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    Text(fmt.format(now), color = Color(0xCCFFFFFF), fontSize = 22.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppItem,
    requester: FocusRequester?,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onHide: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, label = "scale")
    val shape = RoundedCornerShape(22.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(240.dp)) {
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(240.dp, 135.dp)
                .shadow(if (focused) 28.dp else 4.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(listOf(app.c1, app.c2)))
                .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .combinedClickable(onClick = onOpen, onLongClick = onHide),
            contentAlignment = Alignment.Center,
        ) {
            if (app.isBanner) {
                Image(app.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Image(app.image, null, Modifier.size(84.dp), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (focused) 1f else 0f),
        )
    }
}

@Composable
fun Tile(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tile")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(220.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(if (focused) Color.White else Color(0x33FFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ManageScreen(
    all: List<AppItem>,
    hidden: Set<String>,
    onToggle: (AppItem) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1E))
            .padding(horizontal = 96.dp, vertical = 40.dp)
    ) {
        Text("Manage apps", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(
            "Press OK on an app to show or hide it. Press Back when you are done.",
            color = Color(0x99FFFFFF),
            fontSize = 18.sp,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
        ) {
            items(all, key = { it.pkg }) { app ->
                ManageRow(app, app.pkg !in hidden) { onToggle(app) }
            }
        }
    }
}

@Composable
fun ManageRow(app: AppItem, shown: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color(0x44FFFFFF) else Color(0x1AFFFFFF))
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(app.image, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(20.dp))
        Text(app.label, color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
        Text(
            if (shown) "Shown" else "Hidden",
            color = if (shown) Color(0xFF34D399) else Color(0xFFFF7A59),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
