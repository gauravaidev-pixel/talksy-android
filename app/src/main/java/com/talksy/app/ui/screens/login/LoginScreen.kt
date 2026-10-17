package com.talksy.app.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talksy.app.R
import com.talksy.app.ui.theme.Cyan
import com.talksy.app.ui.theme.Indigo
import com.talksy.app.ui.theme.IndigoDark

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1621), Color(0xFF17344F), Indigo))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Talksy",
                style = MaterialTheme.typography.headlineMedium,
                color = androidx.compose.ui.graphics.Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Fast, simple and secure messaging",
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { vm.signInWithGoogle(context) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    contentColor = IndigoDark,
                ),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Indigo)
                } else {
                    Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
