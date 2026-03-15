package com.arche.threply.ime.compose

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Language option for translation.
 */
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String
)

/**
 * Translate panel with source/target language selection.
 * Supports 8+ languages: Chinese, English, Japanese, Korean, Spanish, French, German, Auto-detect.
 */
@Composable
fun TranslatePanel(
    sourceLanguage: String,
    targetLanguage: String,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onTranslate: (String, String) -> Unit,
    isLoading: Boolean,
    streamingText: String
) {
    val languages = listOf(
        LanguageOption("auto", "Auto-detect", "自动检测"),
        LanguageOption("zh", "Chinese", "中文"),
        LanguageOption("en", "English", "英文"),
        LanguageOption("ja", "Japanese", "日文"),
        LanguageOption("ko", "Korean", "韩文"),
        LanguageOption("es", "Spanish", "西班牙文"),
        LanguageOption("fr", "French", "法文"),
        LanguageOption("de", "German", "德文")
    )

    var showSourceDropdown by remember { mutableStateOf(false) }
    var showTargetDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F1F3))
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "翻译",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Language selectors row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source language selector
            Box(modifier = Modifier.weight(1f)) {
                LanguageSelector(
                    label = "源语言",
                    selectedCode = sourceLanguage,
                    languages = languages,
                    isOpen = showSourceDropdown,
                    onToggle = { showSourceDropdown = !showSourceDropdown },
                    onSelect = { code ->
                        onSourceLanguageChange(code)
                        showSourceDropdown = false
                    }
                )
            }

            // Swap button
            Text(
                text = "⇄",
                fontSize = 18.sp,
                color = Color(0xFF666666),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        // Swap languages (but not if source is auto-detect)
                        if (sourceLanguage != "auto") {
                            val temp = sourceLanguage
                            onSourceLanguageChange(targetLanguage)
                            onTargetLanguageChange(temp)
                        }
                    }
                    .padding(8.dp)
            )

            // Target language selector
            Box(modifier = Modifier.weight(1f)) {
                LanguageSelector(
                    label = "目标语言",
                    selectedCode = targetLanguage,
                    languages = languages.filter { it.code != "auto" }, // Auto-detect not allowed for target
                    isOpen = showTargetDropdown,
                    onToggle = { showTargetDropdown = !showTargetDropdown },
                    onSelect = { code ->
                        onTargetLanguageChange(code)
                        showTargetDropdown = false
                    }
                )
            }
        }

        // Translate button
        Button(
            label = if (isLoading) "翻译中..." else "翻译",
            enabled = !isLoading,
            onClick = { onTranslate(sourceLanguage, targetLanguage) },
            modifier = Modifier.fillMaxWidth()
        )

        // Streaming preview
        if (streamingText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = streamingText,
                fontSize = 12.sp,
                color = Color(0xFF555555),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFFFF), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    label: String,
    selectedCode: String,
    languages: List<LanguageOption>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit
) {
    val selectedLanguage = languages.firstOrNull { it.code == selectedCode }
    val displayText = selectedLanguage?.nativeName ?: label

    Column {
        // Selector button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFFFFF))
                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                .clickable { onToggle() }
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayText,
                fontSize = 13.sp,
                color = Color(0xFF333333),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isOpen) "▲" else "▼",
                fontSize = 10.sp,
                color = Color(0xFF999999)
            )
        }

        // Dropdown menu
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFFFF), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(languages) { lang ->
                    LanguageOptionItem(
                        option = lang,
                        isSelected = lang.code == selectedCode,
                        onClick = { onSelect(lang.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionItem(
    option: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFFE3EBF8) else Color(0xFFFFFFFF)
    val textColor = if (isSelected) Color(0xFF3B6DAA) else Color(0xFF333333)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.nativeName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                text = option.displayName,
                fontSize = 11.sp,
                color = Color(0xFF999999)
            )
        }
        if (isSelected) {
            Text(
                text = "✓",
                fontSize = 14.sp,
                color = Color(0xFF3B6DAA),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Button(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (enabled) Color(0xFF4A90E2) else Color(0xFFCCCCCC)
    val textColor = if (enabled) Color.White else Color(0xFF999999)

    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
