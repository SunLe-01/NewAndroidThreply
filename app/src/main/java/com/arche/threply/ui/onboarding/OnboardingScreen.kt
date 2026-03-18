package com.arche.threply.ui.onboarding

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arche.threply.ime.ImeSetupHelper
import com.arche.threply.data.PrefsManager
import com.arche.threply.data.profile.UserProfile
import com.arche.threply.data.profile.UserProfileStore
import com.arche.threply.ui.components.GlassPanel
import com.arche.threply.ui.components.GlassPrimaryButton
import com.arche.threply.ui.components.LiquidGlassBackdrop
import com.arche.threply.ui.theme.ThreplyColors
import com.arche.threply.ui.theme.threplyPalette
import com.arche.threply.ui.theme.threplyPrimaryButtonColors
import com.arche.threply.util.HapticsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Onboarding Steps ───

enum class OnboardingStep(val icon: ImageVector, val title: String) {
    PERMISSIONS(Icons.Filled.Keyboard, "键盘与权限"),
    SHORTCUTS(Icons.Filled.AutoAwesome, "添加快捷指令"),
    SKILLS(Icons.Filled.Stars, "使用 Skills"),
    PERSONA(Icons.Filled.Person, "个性化"),
    DONE(Icons.Filled.Check, "完成");
}

/**
 * Full onboarding flow with 4 steps.
 * Equivalent to iOS OnboardingView.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var completedIndex by remember { mutableIntStateOf(-1) }
    var isCelebrating by remember { mutableStateOf(false) }
    var didOpenShortcut by remember { mutableStateOf(false) }
    var imeStatus by remember { mutableStateOf(ImeSetupHelper.status(context)) }
    var imeStatusRefreshTick by remember { mutableIntStateOf(0) }
    val steps = OnboardingStep.entries

    val currentStep = steps[currentIndex.coerceIn(0, steps.lastIndex)]
    val pageHorizontalInset = 2.dp

    fun refreshImeStatus(): ImeSetupHelper.Status {
        return ImeSetupHelper.status(context).also { refreshed ->
            imeStatus = refreshed
            imeStatusRefreshTick++
        }
    }

    fun openImeSettingsWithHint(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        ImeSetupHelper.openInputMethodSettings(context)
    }

    fun promptImeSelection(message: String = "请在系统设置里选择 Threply 为默认键盘") {
        val showedPicker = ImeSetupHelper.showInputMethodPicker(context)
        if (!showedPicker) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            ImeSetupHelper.openInputMethodSettings(context)
        } else {
            Toast.makeText(context, "请在弹窗里选择 Threply", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeStatus = ImeSetupHelper.status(context)
                imeStatusRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentStep, imeStatusRefreshTick) {
        if (currentStep != OnboardingStep.PERMISSIONS || !imeStatus.isEnabled || imeStatus.isSelected) return@LaunchedEffect
        repeat(6) {
            delay(350)
            val refreshed = ImeSetupHelper.status(context)
            if (refreshed != imeStatus) {
                imeStatus = refreshed
            }
            if (refreshed.isSelected) return@LaunchedEffect
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidGlassBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Progress header
            AnimatedContent(
                targetState = isCelebrating,
                label = "progressHeader"
            ) { celebrating ->
                if (celebrating) {
                    CelebrationCircle(
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    OnboardingProgressPill(
                        steps = steps.toList(),
                        currentIndex = currentIndex,
                        completedIndex = completedIndex,
                        onSelectIndex = { target ->
                            val maxAllowed = maxOf(currentIndex, completedIndex)
                            if (target <= maxAllowed && target != currentIndex) {
                                currentIndex = target
                            }
                        },
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .fillMaxWidth()
                            .padding(horizontal = pageHorizontalInset)
                    )
                }
            }

            // Page content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = pageHorizontalInset)
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val involvesPersona =
                            targetState == OnboardingStep.PERSONA || initialState == OnboardingStep.PERSONA
                        if (involvesPersona) {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        } else {
                            val forward = targetState.ordinal >= initialState.ordinal
                            if (forward) {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                            }
                        }
                    },
                    label = "pageContent"
                ) { step ->
                    when (step) {
                        OnboardingStep.PERMISSIONS -> PermissionsPage(
                            imeStatus = imeStatus
                        )
                        OnboardingStep.SHORTCUTS -> ShortcutsPage(
                            didOpenShortcut = didOpenShortcut,
                            onOpenShortcut = { didOpenShortcut = true }
                        )
                        OnboardingStep.SKILLS -> SkillsPage()
                        OnboardingStep.PERSONA -> PersonaPage()
                        OnboardingStep.DONE -> WelcomePage()
                    }
                }
            }

            // Bottom CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                when (currentStep) {
                    OnboardingStep.PERMISSIONS -> {
                        val palette = threplyPalette()
                        if (imeStatus.isEnabled && imeStatus.isSelected) {
                            GlassPrimaryButton(
                                text = "继续",
                                onClick = {
                                    completedIndex = maxOf(completedIndex, currentIndex)
                                    currentIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
                                },
                                trailingIcon = {
                                    Icon(Icons.Filled.ChevronRight, null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GlassPrimaryButton(
                                    text = if (!imeStatus.isEnabled) "启用键盘" else "选择 Threply",
                                    onClick = {
                                        if (!imeStatus.isEnabled) {
                                            openImeSettingsWithHint("请先在系统设置里启用 Threply 键盘")
                                        } else {
                                            promptImeSelection()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = {
                                        Icon(Icons.Filled.OpenInNew, null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                                    }
                                )
                                GlassPrimaryButton(
                                    text = if (!imeStatus.isEnabled) "我已开启" else "我已切换",
                                    onClick = {
                                        refreshImeStatus()
                                        completedIndex = maxOf(completedIndex, currentIndex)
                                        currentIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    OnboardingStep.SHORTCUTS -> {
                        GlassPrimaryButton(
                            text = "继续",
                            onClick = {
                                completedIndex = maxOf(completedIndex, currentIndex)
                                currentIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
                            },
                            enabled = didOpenShortcut,
                            trailingIcon = {
                                Icon(Icons.Filled.ChevronRight, null, tint = threplyPalette().textPrimary, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    OnboardingStep.SKILLS -> {
                        GlassPrimaryButton(
                            text = "继续",
                            onClick = {
                                completedIndex = maxOf(completedIndex, currentIndex)
                                currentIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
                            },
                            trailingIcon = {
                                Icon(Icons.Filled.ChevronRight, null, tint = threplyPalette().textPrimary, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    OnboardingStep.PERSONA -> {
                        PersonaBottomButtons(
                            onSkip = {
                                completedIndex = OnboardingStep.DONE.ordinal
                                currentIndex = OnboardingStep.DONE.ordinal
                                scope.launch {
                                    delay(360)
                                    isCelebrating = true
                                }
                            },
                            onFinish = {
                                completedIndex = OnboardingStep.DONE.ordinal
                                currentIndex = OnboardingStep.DONE.ordinal
                                scope.launch {
                                    delay(360)
                                    isCelebrating = true
                                }
                            }
                        )
                    }
                    OnboardingStep.DONE -> {
                        GlassPrimaryButton(
                            text = "进入 Threply",
                            onClick = onFinish,
                            trailingIcon = {
                                Icon(Icons.Filled.ArrowForward, null, tint = threplyPalette().textPrimary, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

// ─── Progress Pill ───

@Composable
private fun OnboardingProgressPill(
    steps: List<OnboardingStep>,
    currentIndex: Int,
    completedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = threplyPalette()
    val shape = RoundedCornerShape(22.dp)
    val animatedCoveredSteps by animateFloatAsState(
        targetValue = (currentIndex + 1).coerceAtLeast(1).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "coveredSteps"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(24.dp, shape, ambientColor = palette.shadowColor)
    ) {
        // Background track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.Transparent)
        )

        // Progress fill
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val segmentWidth = totalWidth / steps.size
            val progressWidth = segmentWidth * animatedCoveredSteps

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(progressWidth)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                palette.secondaryButtonContainer,
                                palette.glassSurface,
                            )
                        )
                    )
                    .border(1.dp, palette.glassBorderMedium, RoundedCornerShape(20.dp))
            )
        }

        // Step icons
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val isCompleted = index <= completedIndex
                val isActive = index <= currentIndex

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onSelectIndex(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        // Checkmark
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) palette.secondaryButtonContainer else palette.chipSurface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = palette.textPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    } else {
                        Icon(
                            step.icon,
                            contentDescription = step.title,
                            tint = if (isActive) palette.textPrimary else palette.textTertiary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CelebrationCircle(modifier: Modifier = Modifier) {
    val palette = threplyPalette()
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "celebrationScale"
    )

    Box(
        modifier = modifier
            .size(74.dp)
            .scale(scale)
            .shadow(26.dp, CircleShape, ambientColor = palette.shadowColor)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.secondaryButtonContainer,
                        palette.glassSurface,
                    )
                )
            )
            .border(1.dp, palette.glassBorderMedium, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "完成",
            tint = palette.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ─── Pages ───

@Composable
private fun PermissionsPage(imeStatus: ImeSetupHelper.Status) {
    val palette = threplyPalette()
    GlassPanel(
        title = "键盘与权限",
        subtitle = "在系统设置中启用 Threply 键盘，开启后即可继续。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionRow(
                title = "已启用 Threply 键盘",
                subtitle = "设置 > 系统 > 语言与输入法 > 键盘管理",
                isOn = imeStatus.isEnabled
            )
            PermissionRow(
                title = "设置为默认键盘",
                subtitle = "选择 Threply 为当前输入法",
                isOn = imeStatus.isSelected
            )
            if (imeStatus.isEnabled && !imeStatus.isSelected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.chipSurface)
                        .border(1.dp, palette.glassBorderSoft, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFD36A),
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "还差最后一步",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "点下方“选择 Threply”，在系统弹出的输入法选择器里选中 Threply。选好后返回这里，点“我已切换”。",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
            Text(
                text = when {
                    !imeStatus.isEnabled -> "先在系统设置里启用 Threply 键盘。"
                    !imeStatus.isSelected -> "如果系统没有弹出输入法选择器，请改用系统设置，再返回本页重新检查。"
                    else -> "状态已就绪，可以继续下一步。"
                },
                fontSize = 12.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ShortcutsPage(
    didOpenShortcut: Boolean,
    onOpenShortcut: () -> Unit
) {
    val context = LocalContext.current
    val palette = threplyPalette()

    GlassPanel(
        title = "辅助功能设置",
        subtitle = "Android 端通过辅助功能实现智能回复触发（iOS 端使用快捷指令）。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onOpenShortcut()
                    // Open accessibility settings
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                colors = threplyPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("前往辅助功能设置")
                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    if (didOpenShortcut) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (didOpenShortcut) ThreplyColors.green else palette.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (didOpenShortcut) "已打开设置页面" else "打开设置后即可继续",
                    fontSize = 14.sp,
                    color = palette.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun SkillsPage() {
    val palette = threplyPalette()
    GlassPanel(
        title = "使用 Skills",
        subtitle = "（暂时）"
    ) {
        Text(
            text = "Skills 将用于快速套用不同的回复风格。",
            fontSize = 15.sp,
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun WelcomePage() {
    val palette = threplyPalette()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "welcome to",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Threply",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "一切准备就绪。",
            fontSize = 14.sp,
            color = palette.textSecondary,
        )

        Spacer(Modifier.weight(1f))
    }
}

// ─── PermissionRow ───

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    isOn: Boolean
) {
    val palette = threplyPalette()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.chipSurface)
            .border(1.dp, palette.glassBorderSoft, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isOn) ThreplyColors.green.copy(alpha = 0.22f) else palette.secondaryButtonContainer)
                .border(1.dp, palette.glassBorderMedium, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isOn) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isOn) ThreplyColors.green else palette.textTertiary,
                modifier = Modifier.size(14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
        }
    }
}


// ─── Persona Onboarding Data ───

private data class PersonaCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val presets: List<String>,
)

private val personaCategories = listOf(
    PersonaCategory(
        title = "性格 / 表达风格",
        subtitle = "决定回复给人的气质、力度和社交距离",
        icon = Icons.Filled.AutoAwesome,
        accentColor = Color(0xFF6BBEFF),
        presets = listOf("直接", "温和", "幽默", "礼貌", "理性", "感性", "社恐", "外向", "冷静", "热情")
    ),
    PersonaCategory(
        title = "兴趣 / 偏好领域",
        subtitle = "让 AI 更快抓住你常聊、常看的主题",
        icon = Icons.Filled.Explore,
        accentColor = Color(0xFFFFB44D),
        presets = listOf("计算机", "汽车", "游戏", "二次元", "电影", "音乐", "摄影", "旅行", "健身", "美食", "数码")
    ),
    PersonaCategory(
        title = "常用语气 / 口头禅",
        subtitle = "保留你平时最顺手、最像你的说话方式",
        icon = Icons.Filled.Forum,
        accentColor = Color(0xFF63D7A6),
        presets = listOf("哈哈", "笑死", "收到", "OK", "行吧", "绝了", "麻烦啦", "辛苦啦", "稳", "懂了")
    ),
    PersonaCategory(
        title = "喜欢的事物",
        subtitle = "补充一点生活感，让画像不只停留在语气",
        icon = Icons.Filled.Favorite,
        accentColor = Color(0xFFFF7FB0),
        presets = listOf("咖啡", "猫", "狗", "键盘", "耳机", "机械表", "球鞋", "手办", "相机", "跑车")
    )
)

// ─── Persona Page ───

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaPage() {
    val palette = threplyPalette()
    val context = LocalContext.current
    val view = LocalView.current
    val selections = remember {
        mutableStateMapOf<Int, Set<String>>().apply {
            personaCategories.indices.forEach { put(it, emptySet()) }
        }
    }
    var persistedProfile by remember { mutableStateOf(UserProfile()) }
    var hasHydratedSelections by remember { mutableStateOf(false) }
    var hasSeenInitialSelectionSnapshot by remember { mutableStateOf(false) }
    val selectionSnapshot by remember {
        derivedStateOf {
            personaCategories.mapIndexed { catIndex, category ->
                category.presets.filter { tag -> tag in selections[catIndex].orEmpty() }
            }
        }
    }
    val selectedTags by remember {
        derivedStateOf { selectionSnapshot.flatten() }
    }
    val categorySelectionState by remember {
        derivedStateOf { selectionSnapshot.map { it.isNotEmpty() } }
    }
    val previewText by remember {
        derivedStateOf { buildPersonaPreview(selectionSnapshot) }
    }

    LaunchedEffect(Unit) {
        val existing = withContext(Dispatchers.IO) {
            UserProfileStore.get(context)
        }
        persistedProfile = existing
        selections[0] = existing.personalityTags.toSet()
        selections[1] = existing.interests.toSet()
        selections[2] = existing.catchphrases.toSet()
        selections[3] = existing.favoriteThings.toSet()
        hasHydratedSelections = true
    }

    // Persist only after the initial state has hydrated, so page entry stays light.
    LaunchedEffect(hasHydratedSelections, selectionSnapshot) {
        if (!hasHydratedSelections) return@LaunchedEffect
        if (!hasSeenInitialSelectionSnapshot) {
            hasSeenInitialSelectionSnapshot = true
            return@LaunchedEffect
        }

        val personality = selectionSnapshot.getOrElse(0) { emptyList() }
        val interests = selectionSnapshot.getOrElse(1) { emptyList() }
        val catchphrases = selectionSnapshot.getOrElse(2) { emptyList() }
        val favorites = selectionSnapshot.getOrElse(3) { emptyList() }

        val updated = persistedProfile.copy(
            personalityTags = personality,
            interests = interests,
            catchphrases = catchphrases,
            favoriteThings = favorites
        )
        persistedProfile = updated
        withContext(Dispatchers.IO) {
            UserProfileStore.save(context, updated)
            PrefsManager.setProfileEnabled(context, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Title area
        Text(
            text = "打造你的 AI 画像",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "选几个能代表你的标签，AI 回复会更贴近你的风格。后续也可以在个人中心继续修改。",
            fontSize = 14.sp,
            color = palette.textSecondary,
            lineHeight = 21.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        PersonaHeroCard(
            selectedCount = selectedTags.size,
            categorySelectionState = categorySelectionState,
            previewText = previewText,
            selectedTags = selectedTags
        )

        Spacer(Modifier.height(18.dp))

        personaCategories.forEachIndexed { catIndex, category ->
            val selected = selections[catIndex].orEmpty()
            val toggleTag: (String) -> Unit = { tag ->
                val current = selections[catIndex].orEmpty()
                val next = if (tag in current) current - tag else current + tag
                selections[catIndex] = next
                HapticsUtil.selection(view)
            }
            PersonaCategoryCard(
                category = category,
                selectedCount = selected.size
            ) {
                category.presets.forEach { tag ->
                    PersonaChip(
                        text = tag,
                        accentColor = category.accentColor,
                        isSelected = tag in selected,
                        onClick = { toggleTag(tag) }
                    )
                }
            }

            if (catIndex < personaCategories.lastIndex) {
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun buildPersonaPreview(selectionSnapshot: List<List<String>>): String {
    val allTags = selectionSnapshot.flatten()
    val personality = selectionSnapshot.getOrElse(0) { emptyList() }
    val interests = selectionSnapshot.getOrElse(1) { emptyList() }
    val catchphrases = selectionSnapshot.getOrElse(2) { emptyList() }
    val favorites = selectionSnapshot.getOrElse(3) { emptyList() }

    return when {
        allTags.isEmpty() -> "从四个维度挑几个标签，先让 AI 认识你的语气、兴趣和偏好。"
        personality.isNotEmpty() && interests.isNotEmpty() ->
            "AI 会更偏向用${personality.take(2).joinToString("、")}的方式，聊你关心的${interests.take(2).joinToString("、")}。"
        catchphrases.isNotEmpty() ->
            "回复里会轻量带上你常用的${catchphrases.take(2).joinToString("、")}这类表达。"
        favorites.isNotEmpty() ->
            "你喜欢的${favorites.take(2).joinToString("、")}会成为 AI 理解你风格的额外线索。"
        else -> "再挑几个标签，画像会更接近你平时真实的说话感觉。"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaHeroCard(
    selectedCount: Int,
    categorySelectionState: List<Boolean>,
    previewText: String,
    selectedTags: List<String>
) {
    val palette = threplyPalette()
    val transition = rememberInfiniteTransition(label = "personaHero")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "personaHeroDrift"
    )
    val shape = RoundedCornerShape(28.dp)
    val activeColors = personaCategories
        .filterIndexed { index, _ -> categorySelectionState.getOrElse(index) { false } }
        .map { it.accentColor }
        .ifEmpty { listOf(ThreplyColors.blue, ThreplyColors.orange, ThreplyColors.purple) }
    val previewChips = when {
        selectedTags.isEmpty() -> listOf("温和", "旅行", "收到")
        selectedTags.size > 7 -> selectedTags.take(7) + "+${selectedTags.size - 7}"
        else -> selectedTags
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.secondaryButtonContainer,
                        palette.chipSurface,
                        Color.Transparent
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.glassBorderStrong,
                        palette.glassBorderMedium,
                        palette.glassBorderSoft,
                        palette.glassBorderGlow,
                    )
                ),
                shape = shape
            )
            .padding(20.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val heroColors = when (activeColors.size) {
                1 -> listOf(activeColors[0], ThreplyColors.orange, ThreplyColors.purple)
                2 -> listOf(activeColors[0], activeColors[1], ThreplyColors.purple)
                else -> activeColors.take(3)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(heroColors[0].copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * (0.18f + 0.08f * drift), size.height * 0.20f),
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * (0.18f + 0.08f * drift), size.height * 0.20f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(heroColors[1].copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * (0.78f - 0.10f * drift), size.height * 0.24f),
                    radius = size.minDimension * 0.54f
                ),
                radius = size.minDimension * 0.54f,
                center = Offset(size.width * (0.78f - 0.10f * drift), size.height * 0.24f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(heroColors[2].copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.52f, size.height * (0.82f - 0.10f * drift)),
                    radius = size.minDimension * 0.58f
                ),
                radius = size.minDimension * 0.58f,
                center = Offset(size.width * 0.52f, size.height * (0.82f - 0.10f * drift))
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.secondaryButtonContainer,
                        border = BorderStroke(1.dp, palette.glassBorderMedium)
                    ) {
                        Text(
                            text = if (selectedCount == 0) "实时预览" else "画像正在成型",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.secondaryButtonContainer,
                    border = BorderStroke(1.dp, palette.glassBorderMedium)
                ) {
                    Text(
                        text = "已选 $selectedCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "点一点标签，下面会实时组合出你的个人风格预览。",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    lineHeight = 22.sp
                )
                AnimatedContent(
                    targetState = previewText,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically { -it / 4 })
                    },
                    label = "personaPreviewCopy"
                ) { currentText ->
                    Text(
                        text = currentText,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categorySelectionState.forEachIndexed { index, isActive ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    personaCategories[index].accentColor.copy(alpha = 0.9f)
                                } else {
                                    palette.glassBorderSoft
                                }
                            )
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                previewChips.forEachIndexed { index, tag ->
                    PersonaPreviewChip(
                        text = tag,
                        accentColor = activeColors.getOrElse(index) { palette.chipSurface },
                        isPlaceholder = selectedTags.isEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaPreviewChip(text: String, accentColor: Color, isPlaceholder: Boolean) {
    val palette = threplyPalette()
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isPlaceholder) {
            palette.chipSurface
        } else {
            accentColor.copy(alpha = 0.16f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaceholder) {
                palette.glassBorderSoft
            } else {
                accentColor.copy(alpha = 0.45f)
            }
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isPlaceholder) palette.textSecondary else palette.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaCategoryCard(
    category: PersonaCategory,
    selectedCount: Int,
    chips: @Composable FlowRowScope.() -> Unit
) {
    val palette = threplyPalette()
    val borderColor by animateColorAsState(
        targetValue = if (selectedCount > 0) {
            category.accentColor.copy(alpha = 0.42f)
        } else {
            palette.glassBorderSoft
        },
        animationSpec = tween(240),
        label = "personaCardBorder"
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        category.accentColor.copy(alpha = if (selectedCount > 0) 0.14f else 0.08f),
                        palette.chipSurface,
                        Color.Transparent
                    )
                )
            )
            .border(1.dp, borderColor, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(category.accentColor.copy(alpha = 0.18f))
                        .border(1.dp, category.accentColor.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = category.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = category.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = category.subtitle,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selectedCount > 0) {
                    category.accentColor.copy(alpha = 0.18f)
                } else {
                    palette.chipSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (selectedCount > 0) {
                        category.accentColor.copy(alpha = 0.35f)
                    } else {
                        palette.glassBorderSoft
                    }
                )
            ) {
                Text(
                    text = if (selectedCount > 0) "已选 $selectedCount" else "任选",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
            content = chips
        )
    }
}

@Composable
private fun PersonaChip(
    text: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = threplyPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 720f),
        label = "personaChipScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 0.dp,
        animationSpec = tween(220),
        label = "personaChipShadow"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            accentColor.copy(alpha = 0.78f)
        } else {
            palette.glassBorderMedium
        },
        animationSpec = tween(220),
        label = "personaChipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) palette.textPrimary else palette.textSecondary,
        animationSpec = tween(220),
        label = "personaChipText"
    )
    Row(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.28f),
                spotColor = accentColor.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(
                brush = if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.40f),
                            accentColor.copy(alpha = 0.18f),
                            palette.secondaryButtonContainer
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            palette.secondaryButtonContainer,
                            palette.chipSurface,
                        )
                    )
                }
            )
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(160)) + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(
            text = text,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun PersonaBottomButtons(onSkip: () -> Unit, onFinish: () -> Unit) {
    val palette = threplyPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            GlassPrimaryButton(
                text = "跳过",
                onClick = onSkip
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            GlassPrimaryButton(
                text = "完成",
                onClick = onFinish,
                trailingIcon = {
                    Icon(Icons.Filled.Check, null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}
