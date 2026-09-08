package com.sih.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.sih.ui.components.PrimaryButton
import com.sih.util.BiometricAuthManager
import android.widget.Toast

import com.sih.util.Localization

import androidx.compose.runtime.rememberCoroutineScope
import com.sih.repository.InspectionRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    selectedLanguage: String,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAuthManager = remember(activity) { activity?.let { BiometricAuthManager(it) } }
    val scope = rememberCoroutineScope()

    var officerId by remember { mutableStateOf("admin@lmcs.gov.in") }
    var password by remember { mutableStateOf("admin1234") }
    var isLoggingIn by remember { mutableStateOf(false) }

    val performLogin: () -> Unit = {
        if (officerId.isBlank()) {
            Toast.makeText(context, "Please enter Officer ID / Email", Toast.LENGTH_SHORT).show()
        } else if (password.isBlank()) {
            Toast.makeText(context, "Please enter Password", Toast.LENGTH_SHORT).show()
        } else if (!isLoggingIn) {
            isLoggingIn = true
            val emailInput = when {
                officerId.contains("@") -> officerId.trim()
                else -> "inspector_${officerId.trim()}@lmcs.gov.in"
            }
            val passInput = password.trim()

            scope.launch {
                try {
                    val result = InspectionRepository.login(emailInput, passInput)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Welcome, ${result.getOrNull()?.name ?: "Officer"}", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    } else {
                        Toast.makeText(context, "Login failed: Invalid credentials", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Authentication error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoggingIn = false
                }
            }
        }
    }

    val handleBiometricLogin: () -> Unit = remember(biometricAuthManager, onLoginSuccess) {
        {
            biometricAuthManager?.let { manager ->
                if (manager.canAuthenticate()) {
                    manager.authenticate(
                        onSuccess = { 
                            Toast.makeText(context, "Biometric authentication verified", Toast.LENGTH_SHORT).show()
                            performLogin() 
                        },
                        onError = { _, message ->
                            Toast.makeText(context, "Biometric Error: $message", Toast.LENGTH_SHORT).show()
                        },
                        onFailed = {
                            Toast.makeText(context, "Biometric not recognized. Please use password.", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Toast.makeText(context, "Biometrics not enrolled on device. Use password login.", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(context, "Biometric service unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = Localization.getString("app_name", selectedLanguage),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = Localization.getString("legal_metrology", selectedLanguage),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedTextField(
            value = officerId,
            onValueChange = { officerId = it },
            label = { Text("Officer ID / Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password/PIN") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PrimaryButton(
            text = Localization.getDualString("login", selectedLanguage),
            onClick = performLogin
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = handleBiometricLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Biometric Login")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Government of India\nMinistry of Consumer Affairs",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        LoginScreen("English", onLoginSuccess = {})
    }
}
