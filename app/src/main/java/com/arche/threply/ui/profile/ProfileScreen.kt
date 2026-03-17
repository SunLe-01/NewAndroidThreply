package com.arche.threply.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.data.BackendSessionStore
import com.arche.threply.data.PrefsManager
import com.arche.threply.ui.theme.ThreplyColors

/**
 * Profile settings screen.
 * Equivalent to iOS ProfileView.
 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf(PrefsManager.getUserDisplayName(context)) }
    var hapticsEnabled by remember { mutableStateOf(PrefsManager.isHapticsEnabled(context)) }
    var hapticStrength by remember { mutableStateOf(PrefsManager.getHapticStrength(context)) }
    var autoSentencePunctuation by remember { mutableStateOf(PrefsManager.isAutoSentencePunctuationEnabled(context)) }
    var backendBaseURL by remember { mutableStateOf(PrefsManager.getBackendBaseURL(context)) }
    var languagePreference by remember { mutableStateOf(PrefsManager.getLanguagePreference(context)) }
    var handedness by remember { mutableStateOf(PrefsManager.getHandedness(context)) }
    var showPersonaScreen by remember { mutableStateOf(false) }

    if (showPersonaScreen) {
        ProfilePersonaScreen(onBack = { showPersonaScreen = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Text(
            text = "个人中心",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // ─── User Info ───
        Card(
            colors = CardDefaults.cardColors(containerColor = ThreplyColors.glassSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = Color.Gray.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayName.take(1),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                Column {
                    Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("threply 用户", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── Preferences ───
        Text("偏好设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ThreplyColors.glassSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Haptics toggle
                SettingsToggleRow(
                    title = "触感反馈",
                    subtitle = "键盘与主界面触感反馈开关",
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        hapticsEnabled = it
                        PrefsManager.setHapticsEnabled(context, it)
                    }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                // Haptic strength slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("触感强度", fontSize = 15.sp, color = Color.White)
                        Text("${(hapticStrength * 100).toInt()}%", fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Slider(
                        value = hapticStrength,
                        onValueChange = {
                            hapticStrength = it
                            PrefsManager.setHapticStrength(context, it)
                        },
                        valueRange = 0f..1f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Text("调整键盘打字时震动强弱", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                // Auto sentence punctuation
                SettingsToggleRow(
                    title = "连按空格自动句号",
                    subtitle = "关闭后连续空格不会自动插入句号",
                    checked = autoSentencePunctuation,
                    onCheckedChange = {
                        autoSentencePunctuation = it
                        PrefsManager.setAutoSentencePunctuationEnabled(context, it)
                    }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                // Language picker
                SettingsPickerRow(
                    title = "语言设置",
                    options = listOf("system" to "跟随系统", "chinese" to "中文", "english" to "English"),
                    selected = languagePreference,
                    onSelected = {
                        languagePreference = it
                        PrefsManager.setLanguagePreference(context, it)
                    }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                // Handedness picker
                SettingsPickerRow(
                    title = "惯用手",
                    options = listOf("left" to "左手优先", "right" to "右手优先"),
                    selected = handedness,
                    onSelected = {
                        handedness = it
                        PrefsManager.setHandedness(context, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── AI Persona ───
        Text("AI 个性化", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ThreplyColors.glassSurface),
            modifier = Modifier.fillMaxWidth(),
            onClick = { showPersonaScreen = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("用户形象", fontSize = 15.sp, color = Color.White)
                    Text("管理兴趣、口头禅、语气偏好", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Text("›", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── Backend ───
        Text("后端服务", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ThreplyColors.glassSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = backendBaseURL,
                    onValueChange = {
                        backendBaseURL = it
                        PrefsManager.setBackendBaseURL(context, it)
                    },
                    label = { Text("后端地址") },
                    placeholder = { Text("例如 https://api.arche.pw/v1") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.7f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "键盘扩展和辅助功能会读取这个地址。修改后建议重新打开键盘。",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── Developer ───
        Text("开发者", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ThreplyColors.glassSurface),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // TODO: Navigate to developer menu
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("开发者模式", fontSize = 15.sp, color = Color.White)
                Text("›", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ─── Logout ───
        Button(
            onClick = {
                PrefsManager.setLoggedIn(context, false)
                PrefsManager.setUserDisplayName(context, "访客")
                PrefsManager.setGoogleUserId(context, "")
                PrefsManager.setGoogleUserDisplayName(context, "")
                BackendSessionStore.clearSession(context)
                onLogout()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red.copy(alpha = 0.15f),
                contentColor = Color.Red
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("退出登陆", fontSize = 15.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Reusable Setting Rows ───

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun SettingsPickerRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: options[0].second

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = Color.White)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selectedLabel, color = Color.White.copy(alpha = 0.7f))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
