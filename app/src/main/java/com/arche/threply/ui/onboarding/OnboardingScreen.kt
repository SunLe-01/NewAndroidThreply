package com.arche.threply.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arche.threply.ime.ImeSetupHelper
import com.arche.threply.ui.components.GlassPanel
import com.arche.threply.ui.components.GlassPrimaryButton
import com.arche.threply.ui.components.LiquidGlassBackdrop
import com.arche.threply.ui.theme.ThreplyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Onboarding Steps ───

enum class OnboardingStep(val icon: ImageVector, val title: String) {
    PERMISSIONS(Icons.Filled.Keyboard, "键盘与权限"),
    SHORTCUTS(Icons.Filled.AutoAwesome, "添加快捷指令"),
    SKILLS(Icons.Filled.Stars, "使用 Skills"),
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
    val steps = OnboardingStep.entries

    val currentStep = steps[currentIndex.coerceIn(0, steps.lastIndex)]

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeStatus = ImeSetupHelper.status(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                            .widthIn(max = 340.dp)
                    )
                }
            }

            // Page content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        if (forward) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
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
                        OnboardingStep.DONE -> WelcomePage()
                    }
                }
            }

            // Bottom CTA
            when (currentStep) {
                OnboardingStep.PERMISSIONS -> {
                    if (imeStatus.isEnabled && imeStatus.isSelected) {
                        GlassPrimaryButton(
                            text = "继续",
                            onClick = {
                                completedIndex = maxOf(completedIndex, currentIndex)
                                currentIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
                            },
                            trailingIcon = {
                                Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                        context.startActivity(intent)
                                    } else {
                                        ImeSetupHelper.showInputMethodPicker(context)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    Icon(Icons.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            )
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("系统设置")
                            }
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
                            Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                OnboardingStep.SKILLS -> {
                    GlassPrimaryButton(
                        text = "完成",
                        onClick = {
                            completedIndex = OnboardingStep.DONE.ordinal
                            currentIndex = OnboardingStep.DONE.ordinal
                            // Trigger celebration after delay
                            scope.launch {
                                delay(360)
                                isCelebrating = true
                            }
                        },
                        trailingIcon = {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                OnboardingStep.DONE -> {
                    GlassPrimaryButton(
                        text = "进入 Threply",
                        onClick = onFinish,
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
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
    val shape = RoundedCornerShape(22.dp)
    val animatedProgress by animateFloatAsState(
        targetValue = currentIndex.toFloat() / (steps.size - 1).coerceAtLeast(1).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f))
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
            val iconSize = 40.dp
            val totalWidth = maxWidth
            val segmentWidth = totalWidth / steps.size
            val progressWidth = iconSize + segmentWidth * currentIndex

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(progressWidth)
                    .align(Alignment.CenterStart)
                    .padding(start = ((segmentWidth - iconSize) / 2).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
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
                                .background(Color.White.copy(alpha = if (isActive) 0.28f else 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.95f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    } else {
                        Icon(
                            step.icon,
                            contentDescription = step.title,
                            tint = Color.White.copy(alpha = if (isActive) 0.92f else 0.55f),
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
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "celebrationScale"
    )

    Box(
        modifier = modifier
            .size(74.dp)
            .scale(scale)
            .shadow(26.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.15f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "完成",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ─── Pages ───

@Composable
private fun PermissionsPage(imeStatus: ImeSetupHelper.Status) {
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
            Text(
                text = when {
                    !imeStatus.isEnabled -> "先在系统设置里启用 Threply 键盘。"
                    !imeStatus.isSelected -> "启用后，再把当前输入法切换到 Threply。"
                    else -> "状态已就绪，可以继续下一步。"
                },
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
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
                    tint = if (didOpenShortcut) ThreplyColors.green else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (didOpenShortcut) "已打开设置页面" else "打开设置后即可继续",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SkillsPage() {
    GlassPanel(
        title = "使用 Skills",
        subtitle = "（暂时）"
    ) {
        Text(
            text = "Skills 将用于快速套用不同的回复风格。",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun WelcomePage() {
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
            color = Color.White.copy(alpha = 0.82f)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Threply",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "一切准备就绪。",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.75f)
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
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isOn) ThreplyColors.green.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isOn) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isOn) ThreplyColors.green else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}


