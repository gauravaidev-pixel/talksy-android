package com.talksy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
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
import com.talksy.app.ui.theme.LocalChatColors
import com.talksy.app.util.Initials

private val AvatarGradients = listOf(
    listOf(Color(0xFF5EB5F7), Color(0xFF3D9BE0)),
    listOf(Color(0xFF4FC977), Color(0xFF2FA35A)),
    listOf(Color(0xFF7C5CFF), Color(0xFF5B3FD6)),
    listOf(Color(0xFFF0A93B), Color(0xFFD9861F)),
    listOf(Color(0xFFFF6B81), Color(0xFFD64560)),
)

/** Circular avatar. [ring] draws a story-style gradient ring around it. */
@Composable
fun Avatar(
    name: String,
    picture: String? = null,
    size: Dp = 48.dp,
    online: Boolean = false,
    ring: Boolean = false,
    ringSeen: Boolean = false,
) {
    val idx = (name.hashCode().let { if (it < 0) -it else it }) % AvatarGradients.size
    val accent = LocalChatColors.current.accent
    val t3 = LocalChatColors.current.t3

    val ringBrush = when {
        !ring -> null
        ringSeen -> Brush.linearGradient(listOf(t3, t3))
        else -> Brush.linearGradient(listOf(accent, Color(0xFF7C5CFF), accent))
    }

    Box(contentAlignment = Alignment.Center) {
        if (ringBrush != null) {
            Box(
                Modifier.size(size + 6.dp).clip(CircleShape).background(ringBrush)
            )
        }
        val inner = if (ring) size else size
        Box(
            Modifier
                .size(inner)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (!picture.isNullOrBlank()) {
                AsyncImage(
                    model = picture,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(AvatarGradients[idx])),
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
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(size / 3.2f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF4FC977))
                    .border(size / 26f, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/** The read double-tick inside outgoing bubbles. */
@Composable
fun DoubleTick(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.DoneAll,
        contentDescription = "Sent",
        tint = tint,
        modifier = modifier.size(15.dp),
    )
}

/** Small round unread counter. */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val badge = LocalChatColors.current.badge
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(badge)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Rounded search field used at the top of the lists. */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val input = LocalChatColors.current.inputBg
    Surface(
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = input,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = LocalChatColors.current.t2,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = LocalChatColors.current.t3, style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                },
            )
        }
    }
}

/** Filter chip (All / Groups / ...). */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalChatColors.current.accent
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickableNoRipple(onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) accent else LocalChatColors.current.t2,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

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
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
