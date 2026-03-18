package com.arche.threply.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.data.PrefsManager
import com.arche.threply.data.profile.ReplyHistoryStore
import com.arche.threply.data.profile.UserProfile
import com.arche.threply.data.profile.UserProfileStore
import com.arche.threply.ui.theme.threplyCardColors
import com.arche.threply.ui.theme.threplyDestructiveButtonColors
import com.arche.threply.ui.theme.threplyPalette
import com.arche.threply.ui.theme.threplyPrimaryButtonColors
import com.arche.threply.ui.theme.threplySwitchColors
import com.arche.threply.ui.theme.threplyTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfilePersonaScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val palette = threplyPalette()
    val scope = rememberCoroutineScope()
    var profileEnabled by remember { mutableStateOf(PrefsManager.isProfileEnabled(context)) }
    var profile by remember { mutableStateOf(UserProfileStore.get(context)) }
    var historyCount by remember { mutableIntStateOf(ReplyHistoryStore.count(context)) }
    var isInferring by remember { mutableStateOf(false) }
    var inferResult by remember { mutableStateOf<String?>(null) }
    var newSeed by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI 个性化", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            TextButton(onClick = onBack) {
                Text("返回", color = palette.textSecondary)
            }
        }

        // Toggle
        Card(
            colors = threplyCardColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用画像个性化", fontSize = 15.sp, color = palette.textPrimary)
                    Text("AI 回复会参考你的表达习惯", fontSize = 12.sp, color = palette.textTertiary)
                }
                Switch(
                    checked = profileEnabled,
                    onCheckedChange = {
                        profileEnabled = it
                        PrefsManager.setProfileEnabled(context, it)
                    },
                    colors = threplySwitchColors()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Profile view
        Text("当前画像", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textTertiary, modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = threplyCardColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (profile.isEmpty) {
                    Text("尚未生成画像，请先使用 AI 回复功能积累数据后点击下方「重新生成」。", fontSize = 14.sp, color = palette.textTertiary)
                } else {
                    if (profile.personalityTags.isNotEmpty()) {
                        Text("性格风格", fontSize = 12.sp, color = palette.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FlowChips(items = profile.personalityTags)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (profile.interests.isNotEmpty()) {
                        Text("兴趣偏好", fontSize = 12.sp, color = palette.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FlowChips(items = profile.interests)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (profile.catchphrases.isNotEmpty()) {
                        Text("常用表达", fontSize = 12.sp, color = palette.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FlowChips(items = profile.catchphrases)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (profile.toneDescription.isNotBlank()) {
                        Text("语气风格", fontSize = 12.sp, color = palette.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        Text(profile.toneDescription, fontSize = 14.sp, color = palette.textPrimary)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (profile.avoidRules.isNotEmpty()) {
                        Text("避免项", fontSize = 12.sp, color = palette.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FlowChips(items = profile.avoidRules, chipColor = Color.Red.copy(alpha = 0.15f))
                        Spacer(Modifier.height(12.dp))
                    }
                    if (profile.favoriteThings.isNotEmpty()) {
                        Text("喜欢的事物", fontSize = 12.sp, color = palette.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FlowChips(items = profile.favoriteThings)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (profile.lastInferredAt > 0) {
                        Spacer(Modifier.height(8.dp))
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(profile.lastInferredAt))
                        Text("上次更新：$dateStr", fontSize = 11.sp, color = palette.textDim)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Manual seeds
        Text("手动设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textTertiary, modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = threplyCardColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("添加你的兴趣、口头禅或性格描述", fontSize = 12.sp, color = palette.textTertiary)
                Spacer(Modifier.height(8.dp))
                if (profile.manualSeeds.isNotEmpty()) {
                    FlowChipsRemovable(
                        items = profile.manualSeeds,
                        onRemove = { item ->
                            val updated = profile.copy(manualSeeds = profile.manualSeeds - item)
                            UserProfileStore.save(context, updated)
                            profile = updated
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSeed,
                        onValueChange = { newSeed = it },
                        placeholder = { Text("例如：简洁、#编程、哈哈", fontSize = 13.sp) },
                        singleLine = true,
                        colors = threplyTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newSeed.isNotBlank()) {
                            val updated = profile.copy(manualSeeds = profile.manualSeeds + newSeed.trim())
                            UserProfileStore.save(context, updated)
                            profile = updated
                            newSeed = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, "添加", tint = palette.textPrimary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Actions & stats
        Text("操作", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textTertiary, modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = threplyCardColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("已收集回复：${historyCount} 条", fontSize = 14.sp, color = palette.textPrimary)
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        isInferring = true
                        inferResult = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                UserProfileStore.inferFromHistory(context)
                            }
                            isInferring = false
                            if (result != null) {
                                profile = result
                                inferResult = "画像已更新"
                            } else {
                                inferResult = "推断失败（需要至少 3 条回复历史且配置 DeepSeek API Key）"
                            }
                        }
                    },
                    colors = threplyPrimaryButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isInferring
                ) {
                    if (isInferring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = palette.primaryButtonContent
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isInferring) "正在分析..." else "重新生成画像", fontSize = 14.sp)
                }

                if (inferResult != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(inferResult!!, fontSize = 12.sp, color = palette.textSecondary)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        UserProfileStore.clear(context)
                        ReplyHistoryStore.clear(context)
                        profile = UserProfile()
                        historyCount = 0
                        inferResult = "已清空所有数据"
                    },
                    colors = threplyDestructiveButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清空画像和历史", fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Helper composables ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(items: List<String>, chipColor: Color? = null) {
    val palette = threplyPalette()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Surface(shape = RoundedCornerShape(16.dp), color = chipColor ?: palette.chipSurface) {
                Text(item, fontSize = 13.sp, color = palette.textPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChipsRemovable(items: List<String>, onRemove: (String) -> Unit) {
    val palette = threplyPalette()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Surface(shape = RoundedCornerShape(16.dp), color = palette.chipSurface) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Text(item, fontSize = 13.sp, color = palette.textPrimary)
                    IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Close, "删除", tint = palette.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
