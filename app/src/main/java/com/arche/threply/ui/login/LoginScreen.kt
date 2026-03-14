package com.arche.threply.ui.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.arche.threply.BuildConfig
import com.arche.threply.data.BackendAuthApi
import com.arche.threply.data.BackendSessionStore
import com.arche.threply.data.PrefsManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * Login screen with Google Sign-In and phone login skeleton.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (displayName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var showPhoneLogin by remember { mutableStateOf(false) }

    if (showPhoneLogin) {
        PhoneLoginContent(
            onBack = { showPhoneLogin = false },
            onLoginSuccess = { phoneNumber, response ->
                val displayName = response.user.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: "用户${phoneNumber.takeLast(4)}"

                completeLoginState(
                    context = context,
                    displayName = displayName,
                    providerUserId = "phone:$phoneNumber",
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    plan = response.user.plan
                )
                onLoginSuccess(displayName)
                onDismiss()
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(68.dp)
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "登陆你的 Threply 账号",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "选择 Google 账户或手机号登陆，稍后即可管理偏好设置。",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                isSigningIn = true
                signInError = null
                scope.launch {
                    try {
                        val result = runGoogleLoginFramework(context)
                        completeLoginState(
                            context = context,
                            displayName = result.displayName,
                            providerUserId = result.providerUserId,
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken,
                            plan = result.plan
                        )

                        onLoginSuccess(result.displayName)
                        onDismiss()
                    } catch (e: GetCredentialException) {
                        signInError = "Google 登录失败：${e.message ?: "请稍后重试"}"
                    } catch (e: Exception) {
                        signInError = e.message ?: "登录失败，请稍后重试"
                    } finally {
                        isSigningIn = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isSigningIn
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "使用 Google 账号登陆",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                signInError = null
                showPhoneLogin = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isSigningIn
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Phone, null, modifier = Modifier.size(18.dp))
                    Text("使用手机号登陆", fontSize = 15.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(16.dp))
            }
        }

        if (isSigningIn) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "正在验证 Google 账号...",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        if (signInError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = signInError!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "登录后即可使用 AI 输入建议与会员功能。",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

private data class GoogleLoginResult(
    val displayName: String,
    val providerUserId: String,
    val accessToken: String,
    val refreshToken: String,
    val plan: String?
)

private suspend fun runGoogleLoginFramework(context: Context): GoogleLoginResult {
    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
    if (webClientId.isEmpty() || webClientId.contains("REPLACE_WITH")) {
        throw IllegalStateException("Google 登录尚未配置，请在 build.gradle 中设置 GOOGLE_WEB_CLIENT_ID")
    }

    val activity = context.findActivity()
        ?: throw IllegalStateException("无法启动 Google 登录：未找到 Activity 上下文")

    val credentialManager = CredentialManager.create(context)
    val googleOption = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleOption)
        .build()

    val result = credentialManager.getCredential(activity, request)
    val googleCredential = when (val credential = result.credential) {
        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data)
            } else {
                null
            }
        }
        else -> null
    } ?: throw IllegalStateException("未获取到有效的 Google 登录凭据")

    val displayName = googleCredential.displayName
        ?.takeIf { it.isNotBlank() }
        ?: "Google 用户"

    val response = BackendAuthApi.loginWithGoogle(
        baseUrl = BackendSessionStore.configuredBaseURL(context),
        idToken = googleCredential.idToken,
        googleUserId = googleCredential.id,
        displayName = displayName
    )

    return GoogleLoginResult(
        displayName = response.user.displayName
            ?.takeIf { it.isNotBlank() }
            ?: displayName,
        providerUserId = googleCredential.id,
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        plan = response.user.plan
    )
}

private fun completeLoginState(
    context: Context,
    displayName: String,
    providerUserId: String,
    accessToken: String,
    refreshToken: String,
    plan: String?
) {
    BackendSessionStore.saveSession(
        context = context,
        accessToken = accessToken,
        refreshToken = refreshToken,
        plan = plan
    )
    PrefsManager.setLoggedIn(context, true)
    PrefsManager.setUserDisplayName(context, displayName)
    PrefsManager.setGoogleUserId(context, providerUserId)
    PrefsManager.setGoogleUserDisplayName(context, displayName)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
