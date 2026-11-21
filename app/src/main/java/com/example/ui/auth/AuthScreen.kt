package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.MaritimeAmber
import com.example.ui.theme.SeafoamMint
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private enum class AuthMethod {
    GOOGLE, PHONE_OTP
}

private enum class PhoneOtpStep {
    ENTER_PHONE, ENTER_CODE
}

private data class DialPreset(
    val countryLabel: String,
    val dialCode: String
)

private val DialPresets = listOf(
    DialPreset("IDN +62", "+62"),
    DialPreset("ECU +593", "+593"),
    DialPreset("MAR +212", "+212"),
    DialPreset("US +1", "+1")
)

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    scope.launch {
        try {
            val cred = credentialManager.getCredential(context as Activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

private fun sendPhoneOtp(
    activity: Activity,
    phoneNumberE164: String,
    resendToken: PhoneAuthProvider.ForceResendingToken?,
    scope: CoroutineScope,
    onCodeSent: (String, PhoneAuthProvider.ForceResendingToken) -> Unit,
    onAutoVerified: () -> Unit,
    onError: (String) -> Unit
) {
    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    Firebase.auth.signInWithCredential(credential).await()
                    onAutoVerified()
                } catch (e: Exception) {
                    Log.e("Auth", "Auto phone credential sign-in failed", e)
                    onError(e.localizedMessage ?: "Phone verification sign-in failed.")
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("Auth", "Phone verification failed", e)
            val msg = when {
                e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Phone Sign-In is not enabled yet in Firebase Console (Authentication > Sign-in method > Phone). Enable it or use Google Sign-In."
                else -> e.localizedMessage ?: "Failed to send SMS verification code."
            }
            onError(msg)
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(phoneNumberE164)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }
    PhoneAuthProvider.verifyPhoneNumber(builder.build())
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var authMethod by remember { mutableStateOf(AuthMethod.GOOGLE) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Phone OTP state
    var phoneStep by remember { mutableStateOf(PhoneOtpStep.ENTER_PHONE) }
    var dialPrefix by remember { mutableStateOf("+62") }
    var localDigits by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var resendCooldownSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    LaunchedEffect(resendCooldownSec) {
        if (resendCooldownSec > 0) {
            delay(1000L)
            resendCooldownSec -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepOceanNavy,
                        Color(0xFF0A2A40),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_ocean_hero),
                    contentDescription = "OceanAiUU deep-sea fishing vessel and bathymetric traceability chart",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC061C2C))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Surface(
                        color = SeafoamMint.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "GDST · FAO · ANTI-IUU LEDGER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7F7E8),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "OceanAiUU",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Text(
                        text = "Traceability from deep-sea haul to global export.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD7E8F3)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Operator Sign-In",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = authMethod == AuthMethod.GOOGLE,
                            onClick = {
                                authMethod = AuthMethod.GOOGLE
                                errorMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("auth_tab_google")
                        ) {
                            Text("Google Account")
                        }
                        SegmentedButton(
                            selected = authMethod == AuthMethod.PHONE_OTP,
                            onClick = {
                                authMethod = AuthMethod.PHONE_OTP
                                errorMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("auth_tab_phone_otp")
                        ) {
                            Text("Phone OTP")
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (authMethod == AuthMethod.GOOGLE) {
                        FeaturePillarRow(
                            icon = Icons.Default.CloudOff,
                            accent = MaritimeAmber,
                            title = "Offline-First At-Sea Logbook",
                            subtitle = "Record trips, crew hauls, and incidents without signal; auto-sync at port."
                        )
                        FeaturePillarRow(
                            icon = Icons.Default.GpsFixed,
                            accent = SeafoamMint,
                            title = "GPS Zone Alerts & Gemini Species ID",
                            subtitle = "Verify catch species from photos and flag restricted sanctuary zones."
                        )
                        FeaturePillarRow(
                            icon = Icons.Default.VerifiedUser,
                            accent = DeepOceanNavy,
                            title = "Ministry Certification & Buyer Lookup",
                            subtitle = "Audit weight variance, issue catch certs, and verify export lots."
                        )

                        Button(
                            onClick = {
                                errorMessage = null
                                isSigningIn = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isSigningIn = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isSigningIn = false
                                        errorMessage = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isSigningIn = false
                                    }
                                )
                            },
                            enabled = !isSigningIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("google_sign_in_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepOceanNavy,
                                contentColor = Color.White
                            )
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Anchor, contentDescription = null)
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    text = "Sign in with Google",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Phone Number + SMS OTP Flow
                        if (phoneStep == PhoneOtpStep.ENTER_PHONE) {
                            Text(
                                text = "Select country code and enter your mobile number for SMS OTP verification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DialPresets.forEach { preset ->
                                    FilterChip(
                                        selected = dialPrefix == preset.dialCode,
                                        onClick = { dialPrefix = preset.dialCode },
                                        label = { Text(preset.countryLabel) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = dialPrefix,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(4)
                                        dialPrefix = if (digits.isEmpty()) "+" else "+$digits"
                                    },
                                    label = { Text("Code") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.width(96.dp)
                                )
                                OutlinedTextField(
                                    value = localDigits,
                                    onValueChange = { raw ->
                                        localDigits = raw.filter { it.isDigit() }.take(14)
                                    },
                                    label = { Text("Mobile Number") },
                                    placeholder = { Text("81234567890") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("phone_number_input")
                                )
                            }

                            val fullPhone = "${dialPrefix.trim()}${localDigits.trimStart('0')}"
                            Button(
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        errorMessage = "Activity context unavailable for Phone Auth."
                                        return@Button
                                    }
                                    errorMessage = null
                                    isSigningIn = true
                                    sendPhoneOtp(
                                        activity = activity,
                                        phoneNumberE164 = fullPhone,
                                        resendToken = null,
                                        scope = scope,
                                        onCodeSent = { vId, token ->
                                            isSigningIn = false
                                            verificationId = vId
                                            resendToken = token
                                            resendCooldownSec = 60
                                            phoneStep = PhoneOtpStep.ENTER_CODE
                                        },
                                        onAutoVerified = {
                                            isSigningIn = false
                                            onAuthSuccess()
                                        },
                                        onError = { msg ->
                                            isSigningIn = false
                                            errorMessage = msg
                                        }
                                    )
                                },
                                enabled = !isSigningIn && localDigits.length >= 6 && dialPrefix.length >= 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("send_otp_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeepOceanNavy,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sms, contentDescription = null)
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        text = "Send OTP Code",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            val fullPhone = "${dialPrefix.trim()}${localDigits.trimStart('0')}"
                            Text(
                                text = "Enter the 6-digit SMS code sent to $fullPhone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { raw -> otpCode = raw.filter { it.isDigit() }.take(6) },
                                label = { Text("6-Digit OTP Code") },
                                placeholder = { Text("123456") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )

                            Button(
                                onClick = {
                                    val vId = verificationId
                                    if (vId.isNullOrBlank()) {
                                        errorMessage = "Verification session expired. Request a new OTP."
                                        return@Button
                                    }
                                    errorMessage = null
                                    isSigningIn = true
                                    scope.launch {
                                        try {
                                            val credential = PhoneAuthProvider.getCredential(vId, otpCode.trim())
                                            Firebase.auth.signInWithCredential(credential).await()
                                            isSigningIn = false
                                            onAuthSuccess()
                                        } catch (e: Exception) {
                                            Log.e("Auth", "OTP verification failed", e)
                                            isSigningIn = false
                                            errorMessage = e.localizedMessage ?: "Invalid or expired OTP code."
                                        }
                                    }
                                },
                                enabled = !isSigningIn && otpCode.length == 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("verify_otp_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeepOceanNavy,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Verify OTP & Sign In",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        phoneStep = PhoneOtpStep.ENTER_PHONE
                                        otpCode = ""
                                        errorMessage = null
                                    }
                                ) {
                                    Text("Change Number")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val activity = context as? Activity ?: return@OutlinedButton
                                        errorMessage = null
                                        isSigningIn = true
                                        sendPhoneOtp(
                                            activity = activity,
                                            phoneNumberE164 = fullPhone,
                                            resendToken = resendToken,
                                            scope = scope,
                                            onCodeSent = { vId, token ->
                                                isSigningIn = false
                                                verificationId = vId
                                                resendToken = token
                                                resendCooldownSec = 60
                                            },
                                            onAutoVerified = {
                                                isSigningIn = false
                                                onAuthSuccess()
                                            },
                                            onError = { msg ->
                                                isSigningIn = false
                                                errorMessage = msg
                                            }
                                        )
                                    },
                                    enabled = !isSigningIn && resendCooldownSec == 0
                                ) {
                                    Text(
                                        if (resendCooldownSec > 0) "Resend (${resendCooldownSec}s)" else "Resend OTP"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePillarRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
