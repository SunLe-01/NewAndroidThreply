package com.arche.threply.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.billing.BillingManager
import com.arche.threply.billing.PricingOption
import com.arche.threply.ui.theme.ThreplyColors
import com.arche.threply.ui.theme.threplyOutlinedBorder
import com.arche.threply.ui.theme.threplyOutlinedButtonColors
import com.arche.threply.ui.theme.threplyPalette
import com.arche.threply.ui.theme.threplyPrimaryButtonColors

/**
 * Paywall screen with pricing options.
 * Equivalent to iOS PaywallView.
 */
private enum class PaywallStage {
    Plans,
    Checkout
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaywallScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = threplyPalette()
    val billingManager = remember { BillingManager.getInstance(context) }
    var selectedOptionId by remember { mutableStateOf(PricingOption.defaultOptions[1].id) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    var currentStage by remember { mutableStateOf(PaywallStage.Plans) }

    val isLoadingProducts by billingManager.isLoadingProducts.collectAsState()
    val isProcessingPurchase by billingManager.isProcessingPurchase.collectAsState()
    val lastErrorMessage by billingManager.lastErrorMessage.collectAsState()
    val entitledIds by billingManager.entitledProductIds.collectAsState()

    val selectedOption = PricingOption.defaultOptions.find { it.id == selectedOptionId }
        ?: PricingOption.defaultOptions[0]
    val isSelectedEntitled = entitledIds.contains(selectedOption.productId)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.backdropStart,
                        palette.backdropMiddle,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Header ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStage == PaywallStage.Plans) {
                    TextButton(onClick = {
                        billingManager.restorePurchases()
                    }) {
                        Text("Restore", color = palette.textPrimary, fontSize = 14.sp)
                    }
                } else {
                    IconButton(onClick = { currentStage = PaywallStage.Plans }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = palette.textSecondary,
                        )
                    }
                }

                Text(
                    text = if (currentStage == PaywallStage.Plans) "Threply Pro" else "确认支付",
                    fontSize = 14.sp,
                    color = palette.textSecondary,
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = "关闭",
                        tint = palette.textSecondary,
                    )
                }
            }

            AnimatedContent(
                targetState = currentStage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "paywallStage"
            ) { stage ->
                when (stage) {
                    PaywallStage.Plans -> {
                        PlanSelectionContent(
                            pagerState = pagerState,
                            isLoadingProducts = isLoadingProducts,
                            isProcessingPurchase = isProcessingPurchase,
                            isSelectedEntitled = isSelectedEntitled,
                            selectedOptionId = selectedOptionId,
                            entitledIds = entitledIds,
                            onSelectOption = { selectedOptionId = it },
                            onContinue = { currentStage = PaywallStage.Checkout },
                            displayPrice = { billingManager.displayPrice(it) }
                        )
                    }
                    PaywallStage.Checkout -> {
                        CheckoutStageContent(
                            selectedOption = selectedOption,
                            selectedPrice = billingManager.displayPrice(selectedOption),
                            onSubmit = {
                                val activity = context.findPaywallActivity()
                                if (activity != null) {
                                    billingManager.purchase(activity, selectedOption)
                                }
                            },
                            onBack = { currentStage = PaywallStage.Plans }
                        )
                    }
                }
            }
        }
    }

    // Auto-dismiss after successful purchase
    LaunchedEffect(entitledIds) {
        if (entitledIds.contains(selectedOption.productId)) {
            com.arche.threply.data.PrefsManager.setProEntitled(context, true)
            onDismiss()
        }
    }

    // Error dialog
    if (lastErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { billingManager.clearError() },
            title = { Text("购买失败") },
            text = { Text(lastErrorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { billingManager.clearError() }) {
                    Text("好")
                }
            }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanSelectionContent(
    pagerState: androidx.compose.foundation.pager.PagerState,
    isLoadingProducts: Boolean,
    isProcessingPurchase: Boolean,
    isSelectedEntitled: Boolean,
    selectedOptionId: String,
    entitledIds: Set<String>,
    onSelectOption: (String) -> Unit,
    onContinue: () -> Unit,
    displayPrice: (PricingOption) -> String
) {
    val palette = threplyPalette()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ─── Hero Carousel ───
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) { page ->
            val shape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(palette.glassSurfaceElevated)
                    .border(1.dp, palette.glassBorderMedium, shape)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "不限飞行次数",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "支持云端 OCR 与三条智能建议，专注力与效率齐飞。",
                            fontSize = 14.sp,
                            color = palette.textSecondary,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FlightTakeoff,
                            null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "预留素材 ${page + 1}",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) palette.textPrimary
                            else palette.glassBorderMedium
                        )
                )
            }
        }

        Text(
            text = "Unlimited Flights",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
        )

        Text(
            text = "Threply 会员解锁全量次数、三条候选、深度历史上下文与快捷指令一键链路。",
            fontSize = 14.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (isLoadingProducts) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.textPrimary,
                )
                Text("正在加载价格...", fontSize = 12.sp, color = palette.textSecondary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PricingOption.defaultOptions.forEach { option ->
                PricingOptionCard(
                    option = option,
                    priceText = displayPrice(option),
                    isSelected = option.id == selectedOptionId,
                    isEntitled = entitledIds.contains(option.productId),
                    onClick = { onSelectOption(option.id) }
                )
            }
        }

        val ctaScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
            label = "paywallContinueScale"
        )
        val ctaShape = RoundedCornerShape(16.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .scale(ctaScale)
                .shadow(14.dp, ctaShape, ambientColor = palette.shadowColor)
                .clip(ctaShape)
                .background(palette.glassSurfaceElevated)
                .border(1.dp, palette.glassBorderMedium, ctaShape)
                .clickable(
                    enabled = !isProcessingPurchase && !isLoadingProducts && !isSelectedEntitled,
                    onClick = onContinue
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSelectedEntitled) "已解锁" else "继续",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Privacy | Terms", fontSize = 12.sp, color = palette.textTertiary)
            Text("可随时取消订阅", fontSize = 12.sp, color = palette.textTertiary)
        }
    }
}

@Composable
private fun CheckoutStageContent(
    selectedOption: PricingOption,
    selectedPrice: String,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    val palette = threplyPalette()
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "确认你选择的方案与支付方式。当前先接通页面框架，后续再替换成真实 Google Play 购买。",
            fontSize = 14.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            color = palette.glassSurfaceElevated,
            shape = cardShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("订单信息", color = palette.textSecondary, fontSize = 13.sp)
                Text(selectedOption.name, color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(selectedPrice, color = palette.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = selectedOption.subtitle,
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Surface(
            color = palette.glassSurface,
            shape = cardShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.secondaryButtonContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Google Play 支付", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("演示模式，不发起真实扣款", color = palette.textSecondary, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = palette.glassBorderSoft)

                Text("你将看到的真实流程", color = palette.textSecondary, fontSize = 13.sp)
                Text("1. 拉起 Google Play 购买弹层", color = palette.textPrimary, fontSize = 14.sp)
                Text("2. 确认订阅周期与付款方式", color = palette.textPrimary, fontSize = 14.sp)
                Text("3. 支付结果回传主 App 与键盘侧状态", color = palette.textPrimary, fontSize = 14.sp)
            }
        }

        Surface(
            color = palette.positiveSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "当前版本只补支付页面框架，不会真实购买，也不会把你标记为 Pro。",
                color = palette.positiveContent,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        Button(
            onClick = onSubmit,
            colors = threplyPrimaryButtonColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("确认支付", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onBack,
            colors = threplyOutlinedButtonColors(),
            border = threplyOutlinedBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("返回修改方案")
        }
    }
}

// ─── Pricing Option Card ───

@Composable
private fun PricingOptionCard(
    option: PricingOption,
    priceText: String,
    isSelected: Boolean,
    isEntitled: Boolean,
    onClick: () -> Unit
) {
    val palette = threplyPalette()
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "cardScale"
    )

    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .width(160.dp)
            .scale(scale)
            .shadow(14.dp, shape, ambientColor = palette.shadowColor)
            .clip(shape)
            .background(palette.glassSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ThreplyColors.accent else palette.glassBorderMedium,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Badge
        if (isEntitled) {
            Surface(
                color = ThreplyColors.green,
                shape = RoundedCornerShape(50),
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = "已解锁",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryButtonContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        } else if (option.badgeText != null) {
            Surface(
                color = option.badgeColor,
                shape = RoundedCornerShape(50),
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = option.badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryButtonContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Text(
            text = option.name,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )

        Text(
            text = priceText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
        )

        Text(
            text = option.subtitle,
            fontSize = 12.sp,
            color = palette.textSecondary,
        )
    }
}

private tailrec fun Context.findPaywallActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPaywallActivity()
    else -> null
}
