package com.arche.threply.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arche.threply.data.PrefsManager
import com.arche.threply.ime.ImeSetupHelper
import com.arche.threply.ime.model.ImeAiMode
import com.arche.threply.ime.trigger.SharedTriggerStore
import com.arche.threply.screenshot.ScreenshotMonitorService
import com.arche.threply.screenshot.ScreenshotNotificationHelper
import com.arche.threply.ui.components.GlassCard
import com.arche.threply.ui.components.GlassAmbientSample
import com.arche.threply.ui.components.HomeBackdropField
import com.arche.threply.ui.components.RadialGradientBackground
import com.arche.threply.ui.components.rememberHomeBackdropField
import com.arche.threply.ui.components.sampleGlassAmbient
import com.arche.threply.ui.login.LoginScreen
import com.arche.threply.ui.paywall.PaywallScreen
import com.arche.threply.ui.profile.ProfileScreen
import com.arche.threply.ui.theme.ThreplyColors
import com.arche.threply.ui.theme.threplyOutlinedBorder
import com.arche.threply.ui.theme.threplyOutlinedButtonColors
import com.arche.threply.ui.theme.threplyPalette
import com.arche.threply.ui.theme.threplyPrimaryButtonColors
import com.arche.threply.ui.theme.threplySwitchColors
import com.arche.threply.ui.theme.threplyTextFieldColors

/**
 * Main home screen with cards.
 * Equivalent to iOS HomeView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onShowOnboarding: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = threplyPalette()
    val scrollState = rememberScrollState()
    val backdropField = rememberHomeBackdropField()
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val coolTint = if (palette.isDark) Color(0xFF87C9FF) else Color(0xFFDCECF5)
    val warmTint = if (palette.isDark) Color(0xFFE6C27D) else Color(0xFFF3E7D7)
    val apiTint = coolTint.copy(alpha = if (palette.isDark) 0.24f else 0.28f)
    val screenshotTint = coolTint.copy(alpha = if (palette.isDark) 0.16f else 0.18f)
    val chatScanTint = warmTint.copy(alpha = if (palette.isDark) 0.18f else 0.18f)
    val keyboardTint = warmTint.copy(alpha = if (palette.isDark) 0.22f else 0.22f)
    val schemaTint = warmTint.copy(alpha = if (palette.isDark) 0.18f else 0.18f)
    val accessibilityTint = warmTint.copy(alpha = if (palette.isDark) 0.14f else 0.14f)
    val triggerTint = coolTint.copy(alpha = if (palette.isDark) 0.14f else 0.14f)
    val onboardingTint = warmTint.copy(alpha = if (palette.isDark) 0.14f else 0.14f)
    val proTint = warmTint.copy(alpha = if (palette.isDark) 0.26f else 0.26f)
    var showLogin by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(PrefsManager.isLoggedIn(context)) }
    var userDisplayName by remember { mutableStateOf(PrefsManager.getUserDisplayName(context)) }
    var imeRimeSchema by remember { mutableStateOf(PrefsManager.getImeRimeSchema(context)) }
    var imeStatus by remember { mutableStateOf(ImeSetupHelper.status(context)) }

    val schemaOptions = remember {
        listOf(
            "rime_ice" to "雾凇拼音（推荐）",
            "luna_pinyin" to "朙月拼音（基础）",
            "double_pinyin" to "自然码双拼"
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeStatus = ImeSetupHelper.status(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        RadialGradientBackground(
            scrollOffset = scrollState.value,
            backdropField = backdropField,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ─── Header ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Logo text placeholder (since we don't have the image asset)
                    Text(
                        text = "Threply",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "AI认知驱动的下一代输入法",
                        fontSize = 14.sp,
                        color = palette.textSecondary,
                    )
                }

                // Avatar button
                IconButton(
                    onClick = {
                        if (isLoggedIn) showProfile = true
                        else showLogin = true
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(palette.avatarSurface)
                            .border(1.dp, palette.avatarBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoggedIn) {
                            Text(
                                text = userDisplayName.take(1),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = "登录",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // ─── CMode Card ───
            CModeCard()

            // ─── DeepSeek API Key ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = apiTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                DeepSeekApiKeyCard(ambient = it)
            }

            // ─── Screenshot Monitor ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = screenshotTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                ScreenshotMonitorCard(ambient = it)
            }

            // ─── Chat Scan Accessibility ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = chatScanTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                ChatScanAccessibilityCard(ambient = it)
            }

            // ─── Keyboard & Permissions ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = keyboardTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                GlassCard(
                    title = "键盘与权限",
                    tintColor = it.topTint,
                    bottomTintColor = it.bottomTint,
                    edgeTintColor = it.edgeTint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetupStatusRow(
                            title = "Threply 键盘已启用",
                            enabled = imeStatus.isEnabled
                        )
                        SetupStatusRow(
                            title = "Threply 已设为当前输入法",
                            enabled = imeStatus.isSelected
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Photo, null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                            Text("允许存储访问，便于截图裁切", fontSize = 14.sp, color = palette.textPrimary)
                        }
                        Text(
                            text = when {
                                !imeStatus.isEnabled -> "先启用键盘，再切换到 Threply。"
                                !imeStatus.isSelected -> "键盘已启用，但当前默认输入法还不是 Threply。"
                                else -> "键盘状态已就绪，可以直接到任意输入框使用。"
                            },
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!imeStatus.isEnabled) {
                                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                        context.startActivity(intent)
                                    } else {
                                        ImeSetupHelper.showInputMethodPicker(context)
                                    }
                                },
                                colors = threplyPrimaryButtonColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    when {
                                        !imeStatus.isEnabled -> "启用键盘"
                                        !imeStatus.isSelected -> "切换到 Threply"
                                        else -> "重新选择输入法"
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = threplyOutlinedButtonColors(),
                                border = threplyOutlinedBorder(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("系统设置")
                            }
                        }
                    }
                }
            }

            // ─── Rime Schema ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = schemaTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                GlassCard(
                    title = "拼音词库方案",
                    tintColor = it.topTint,
                    bottomTintColor = it.bottomTint,
                    edgeTintColor = it.edgeTint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "切换 Rime 拼音方案（切换后重新打开键盘输入框生效）",
                            fontSize = 13.sp,
                            color = palette.textSecondary,
                        )

                        schemaOptions.forEach { (schemaId, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        imeRimeSchema = schemaId
                                        PrefsManager.setImeRimeSchema(context, schemaId)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = imeRimeSchema == schemaId,
                                    onClick = {
                                        imeRimeSchema = schemaId
                                        PrefsManager.setImeRimeSchema(context, schemaId)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = palette.textTertiary,
                                    )
                                )
                                Column {
                                    Text(text = label, fontSize = 14.sp, color = palette.textPrimary)
                                    Text(
                                        text = schemaId,
                                        fontSize = 11.sp,
                                        color = palette.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Shortcuts / Accessibility ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = accessibilityTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                GlassCard(
                    title = "辅助功能",
                    tintColor = it.topTint,
                    bottomTintColor = it.bottomTint,
                    edgeTintColor = it.edgeTint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                            Text("通过辅助功能实现智能回复自动触发", fontSize = 14.sp, color = palette.textPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = threplyOutlinedButtonColors(),
                            border = threplyOutlinedBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("辅助功能设置")
                                Icon(Icons.Filled.AddCircleOutline, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ─── AI Trigger Bridge ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = triggerTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                GlassCard(
                    title = "AI 触发桥接",
                    tintColor = it.topTint,
                    bottomTintColor = it.bottomTint,
                    edgeTintColor = it.edgeTint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "将当前草稿推送给 Threply 键盘，在输入场景中直接生成 AI 建议。",
                            fontSize = 14.sp,
                            color = palette.textPrimary,
                        )

                        Button(
                            onClick = {
                                val seed = "请帮我礼貌回复：我已收到，晚点给你详细反馈"
                                SharedTriggerStore.pushTrigger(
                                    context = context,
                                    draft = seed,
                                    source = "home_debug",
                                    mode = ImeAiMode.fromRaw(PrefsManager.getImeAiMode(context))
                                )
                            },
                            colors = threplyPrimaryButtonColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("推送示例触发")
                                Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = "提示：切到 Threply 键盘后会自动消费触发并生成建议。",
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                        )
                    }
                }
            }

            // ─── Re-onboarding ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = onboardingTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                GlassCard(
                    title = "重新观看引导",
                    tintColor = it.topTint,
                    bottomTintColor = it.bottomTint,
                    edgeTintColor = it.edgeTint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "查看完整引导，快速熟悉三步设置与试用流程。",
                            fontSize = 14.sp,
                            color = palette.textPrimary,
                        )
                        OutlinedButton(
                            onClick = onShowOnboarding,
                            colors = threplyOutlinedButtonColors(),
                            border = threplyOutlinedBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("重新进入引导")
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ─── Pro Plan ───
            HomeAmbientSection(
                backdropField = backdropField,
                biasColor = proTint,
                scrollOffset = scrollState.value,
                rootSize = rootSize,
            ) {
                GlassCard(
                    title = "threply Pro",
                    tintColor = it.topTint,
                    bottomTintColor = it.bottomTint,
                    edgeTintColor = it.edgeTint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "解锁不限次数的智能回复",
                            fontSize = 14.sp,
                            color = palette.textSecondary,
                        )

                        Button(
                            onClick = { showPaywall = true },
                            colors = threplyPrimaryButtonColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("查看会员方案")
                                Icon(Icons.Filled.CreditCard, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Bottom Sheets ───
    if (showLogin) {
        ModalBottomSheet(
            onDismissRequest = { showLogin = false },
            containerColor = palette.bottomSheetSurface,
        ) {
            LoginScreen(
                onLoginSuccess = { name ->
                    isLoggedIn = true
                    userDisplayName = name
                    showLogin = false
                },
                onDismiss = { showLogin = false }
            )
        }
    }

    if (showProfile) {
        ModalBottomSheet(
            onDismissRequest = { showProfile = false },
            containerColor = palette.bottomSheetSurface,
        ) {
            ProfileScreen(
                onLogout = {
                    isLoggedIn = false
                    userDisplayName = "访客"
                    showProfile = false
                },
                onDismiss = { showProfile = false }
            )
        }
    }

    if (showPaywall) {
        ModalBottomSheet(
            onDismissRequest = { showPaywall = false },
            containerColor = palette.bottomSheetSurface,
        ) {
            PaywallScreen(onDismiss = { showPaywall = false })
        }
    }
}

@Composable
private fun HomeAmbientSection(
    backdropField: HomeBackdropField,
    biasColor: Color,
    scrollOffset: Int,
    rootSize: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(GlassAmbientSample) -> Unit
) {
    val palette = threplyPalette()
    var sectionPosition by remember { mutableStateOf(Offset.Zero) }
    var sectionSize by remember { mutableStateOf(IntSize.Zero) }
    val ambientSample = remember(
        backdropField,
        biasColor,
        scrollOffset,
        rootSize,
        sectionPosition,
        sectionSize,
        palette,
    ) {
        if (
            rootSize.width == 0 ||
            rootSize.height == 0 ||
            sectionSize.width == 0 ||
            sectionSize.height == 0
        ) {
            GlassAmbientSample(
                topTint = biasColor.copy(alpha = biasColor.alpha * 0.72f),
                bottomTint = biasColor.copy(alpha = biasColor.alpha * 0.56f),
                edgeTint = biasColor.copy(alpha = biasColor.alpha * 0.94f),
                auraTint = biasColor.copy(alpha = biasColor.alpha * 0.38f),
            )
        } else {
            backdropField.sampleGlassAmbient(
                cardCenter = Offset(
                    x = sectionPosition.x + sectionSize.width / 2f,
                    y = sectionPosition.y + sectionSize.height / 2f,
                ),
                cardWidth = sectionSize.width.toFloat(),
                cardHeight = sectionSize.height.toFloat(),
                rootWidth = rootSize.width.toFloat(),
                rootHeight = rootSize.height.toFloat(),
                scrollOffset = scrollOffset,
                palette = palette,
                biasColor = biasColor,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                sectionPosition = coordinates.positionInRoot()
                sectionSize = coordinates.size
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (!palette.isDark) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientSample.auraTint.copy(alpha = ambientSample.auraTint.alpha * 0.96f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.28f, size.height * 0.20f),
                        radius = size.maxDimension * 0.86f,
                    ),
                    radius = size.maxDimension * 0.86f,
                    center = Offset(size.width * 0.28f, size.height * 0.20f),
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientSample.bottomTint.copy(alpha = ambientSample.bottomTint.alpha * 0.82f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.82f, size.height * 0.88f),
                        radius = size.maxDimension * 0.72f,
                    ),
                    radius = size.maxDimension * 0.72f,
                    center = Offset(size.width * 0.82f, size.height * 0.88f),
                )

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.glassHighlightTop.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.32f,
                    ),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                )
            }
        }

        content(ambientSample)
    }
}

@Composable
private fun DeepSeekApiKeyCard(ambient: GlassAmbientSample) {
    val context = LocalContext.current
    val palette = threplyPalette()
    var apiKey by remember { mutableStateOf(PrefsManager.getDeepSeekApiKey(context)) }
    var visible by remember { mutableStateOf(false) }

    GlassCard(
        title = "DeepSeek API",
        tintColor = ambient.topTint,
        bottomTintColor = ambient.bottomTint,
        edgeTintColor = ambient.edgeTint,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "填入你的 DeepSeek API Key 后，AI 功能将直连 DeepSeek，无需后端服务。",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    PrefsManager.setDeepSeekApiKey(context, it)
                },
                placeholder = { Text("sk-...", color = palette.textDim) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "隐藏" else "显示",
                            tint = palette.textSecondary,
                        )
                    }
                },
                colors = threplyTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            val status = if (apiKey.isNotBlank()) "已配置 — AI 将直连 DeepSeek" else "未配置 — 使用默认后端"
            Text(
                text = status,
                fontSize = 12.sp,
                color = if (apiKey.isNotBlank()) ThreplyColors.green else palette.textTertiary,
            )
        }
    }
}

@Composable
private fun ScreenshotMonitorCard(ambient: GlassAmbientSample) {
    val context = LocalContext.current
    val palette = threplyPalette()
    var enabled by remember { mutableStateOf(PrefsManager.isScreenshotMonitorEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            enabled = true
            PrefsManager.setScreenshotMonitorEnabled(context, true)
            ScreenshotNotificationHelper.ensureChannels(context)
            ScreenshotMonitorService.start(context)
        }
    }

    GlassCard(
        title = "截图智能回复",
        tintColor = ambient.topTint,
        bottomTintColor = ambient.bottomTint,
        edgeTintColor = ambient.edgeTint,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "开启后，截屏时自动识别聊天内容并生成 AI 回复建议。",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "监控中" else "已关闭",
                    fontSize = 14.sp,
                    color = if (enabled) ThreplyColors.green else palette.textTertiary,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val perms = buildList {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(android.Manifest.permission.READ_MEDIA_IMAGES)
                                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            }
                            val needRequest = perms.any {
                                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (needRequest) {
                                permissionLauncher.launch(perms.toTypedArray())
                            } else {
                                enabled = true
                                PrefsManager.setScreenshotMonitorEnabled(context, true)
                                ScreenshotNotificationHelper.ensureChannels(context)
                                ScreenshotMonitorService.start(context)
                            }
                        } else {
                            enabled = false
                            PrefsManager.setScreenshotMonitorEnabled(context, false)
                            ScreenshotMonitorService.stop(context)
                        }
                    },
                    colors = threplySwitchColors()
                )
            }
        }
    }
}

@Composable
private fun ChatScanAccessibilityCard(ambient: GlassAmbientSample) {
    val context = LocalContext.current
    val palette = threplyPalette()

    fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }

    var enabled by remember { mutableStateOf(isAccessibilityEnabled()) }

    GlassCard(
        title = "聊天扫描（无障碍）",
        tintColor = ambient.topTint,
        bottomTintColor = ambient.bottomTint,
        edgeTintColor = ambient.edgeTint,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "开启无障碍权限后，键盘中的「扫描」按钮可自动截屏识别聊天内容，区分对方和自己的消息，并生成 AI 回复建议。",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "已开启" else "未开启",
                    fontSize = 14.sp,
                    color = if (enabled) ThreplyColors.green else palette.textTertiary,
                )

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    colors = threplyPrimaryButtonColors(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("前往开启")
                }
            }
        }
    }
}

@Composable
private fun SetupStatusRow(title: String, enabled: Boolean) {
    val palette = threplyPalette()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (enabled) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (enabled) ThreplyColors.green else palette.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Text(title, fontSize = 14.sp, color = palette.textPrimary)
    }
}
