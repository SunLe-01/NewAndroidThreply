package com.arche.threply.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.arche.threply.data.BackendAuthApi
import com.arche.threply.data.BackendSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneLoginContent(
    onBack: () -> Unit,
    onLoginSuccess: (phoneNumber: String, response: BackendAuthApi.LoginResponse) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var isSendingCode by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    val normalizedPhone = phoneNumber.filter { it.isDigit() }
    val normalizedCode = code.filter { it.isDigit() }
    val canSendCode = normalizedPhone.length in 6..20 && countdown == 0 && !isSendingCode && !isVerifying
    val canVerify = normalizedPhone.length in 6..20 && normalizedCode.length == 6 && !isSendingCode && !isVerifying

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !isSendingCode && !isVerifying) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "手机号登录",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "输入手机号，我们将发送 6 位验证码。",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                phoneNumber = it.filter { c -> c.isDigit() }.take(20)
                errorMessage = null
            },
            label = { Text("手机号") },
            placeholder = { Text("请输入手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color.White.copy(alpha = 0.8f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSendingCode && !isVerifying
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter { c -> c.isDigit() }.take(6)
                    errorMessage = null
                },
                label = { Text("验证码") },
                placeholder = { Text("6位验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                    focusedBorderColor = Color.White.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = Color.White.copy(alpha = 0.8f),
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                ),
                modifier = Modifier.weight(1f),
                enabled = !isSendingCode && !isVerifying
            )

            Button(
                onClick = {
                    errorMessage = null
                    isSendingCode = true
                    scope.launch {
                        try {
                            BackendAuthApi.sendPhoneCode(
                                baseUrl = BackendSessionStore.configuredBaseURL(context),
                                phoneNumber = normalizedPhone
                            )
                            countdown = 60
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "发送验证码失败"
                        } finally {
                            isSendingCode = false
                        }
                    }
                },
                enabled = canSendCode,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White
                )
            ) {
                Text(
                    if (countdown > 0) "${countdown}s" else if (isSendingCode) "发送中" else "发送",
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                errorMessage = null
                isVerifying = true
                scope.launch {
                    try {
                        val response = BackendAuthApi.verifyPhoneCode(
                            baseUrl = BackendSessionStore.configuredBaseURL(context),
                            phoneNumber = normalizedPhone,
                            code = normalizedCode
                        )
                        onLoginSuccess(normalizedPhone, response)
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "登录失败"
                    } finally {
                        isVerifying = false
                    }
                }
            },
            enabled = canVerify,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black
                )
            } else {
                Text("验证并登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "验证码将通过短信发送到您的手机。",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
