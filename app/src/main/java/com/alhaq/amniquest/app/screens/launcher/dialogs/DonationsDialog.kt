package com.alhaq.amniquest.app.screens.launcher.dialogs

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.alhaq.amniquest.BuildConfig

@Composable
fun DonationsDialog(onDismiss: ()-> Unit) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {

                Text(
                    text = "Hi, We need help",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "AmniQuest is an open-source, privacy-first productivity tool dedicated to helping people build mindful habits and reclaim screen time. " +
                            "Your support helps us keep development active, maintain on-device AI tools, and deliver regular improvements.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )

                OutlinedButton(
                    onClick = {
                        val url = "https://digipaws.life/donate"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = url.toUri()
                        }
                        context.startActivity(intent)
                        onDismiss()
                    },
                ) {
                    Text("Donate :)")
                }
                Text("Never Ask Again", modifier = Modifier.clickable {
                    onDismiss()
                })
            }
        }
    }
}