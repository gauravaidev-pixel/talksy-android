package com.talksy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.talksy.app.util.Initials

private val AvatarGradients = listOf(
    listOf(Color(0xFF2AABEE), Color(0xFF1C8FC7)),
    listOf(Color(0xFF31C48D), Color(0xFF0E9F6E)),
    listOf(Color(0xFF7C5CFC), Color(0xFF5B3FD6)),
    listOf(Color(0xFFF5A623), Color(0xFFE07C1F)),
    listOf(Color(0xFFEF4B6B), Color(0xFFC7364F)),
)

/** Circular avatar: the picture if present, else initials on a soft gradient. */
@Composable
fun Avatar(
    name: String,
    picture: String? = null,
    size: Dp = 48.dp,
    online: Boolean = false,
) {
    val idx = (name.hashCode().let { if (it < 0) -it else it }) % AvatarGradients.size
    Box {
        if (!picture.isNullOrBlank()) {
            AsyncImage(
                model = picture,
                contentDescription = name,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(AvatarGradients[idx])),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Initials.of(name),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value / 2.7f).sp,
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(size / 3.2f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF31C48D))
                    .border(size / 24f, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/** The little "read" double tick used inside outgoing bubbles. */
@Composable
fun DoubleTick(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.DoneAll,
        contentDescription = "Sent",
        tint = tint,
        modifier = modifier.size(14.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
