package com.talksy.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.talksy.app.ui.theme.extras
import com.talksy.app.util.Initials

/** Telegram-style gradient pairs, picked deterministically from the name/id. */
private val AvatarGradients = listOf(
    listOf(Color(0xFFFF885E), Color(0xFFFF516A)),
    listOf(Color(0xFFFFCD6A), Color(0xFFFFA85C)),
    listOf(Color(0xFF82B1FF), Color(0xFF665FFF)),
    listOf(Color(0xFFA0DE7E), Color(0xFF54CB68)),
    listOf(Color(0xFF53EDD6), Color(0xFF28C9B7)),
    listOf(Color(0xFF72D5FD), Color(0xFF2A9EF1)),
    listOf(Color(0xFFE0A2F3), Color(0xFFD669ED)),
)

private val SenderColors = listOf(
    Color(0xFFE17076), Color(0xFFEDA86C), Color(0xFFA695E7), Color(0xFF7BC862),
    Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE),
)

private fun positiveHash(s: String): Int = s.hashCode().let { if (it == Int.MIN_VALUE) 0 else if (it < 0) -it else it }

/** A readable per-user colour for sender names inside group chats. */
fun senderColor(id: String): Color = SenderColors[positiveHash(id) % SenderColors.size]

/** Circular avatar: picture if present, else initials (or an icon) on a gradient. */
@Composable
fun Avatar(
    name: String,
    picture: String? = null,
    size: Dp = 48.dp,
    online: Boolean = false,
    icon: ImageVector? = null,
) {
    val gradient = AvatarGradients[positiveHash(name) % AvatarGradients.size]
    Box {
        if (!picture.isNullOrBlank()) {
            AsyncImage(
                model = picture,
                contentDescription = name,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
                } else {
                    Text(
                        text = Initials.of(name),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (size.value / 2.5f).sp,
                    )
                }
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(size / 3.6f)
                    .align(Alignment.BottomEnd)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.extras.online),
            )
        }
    }
}

/** Round unread-count pill, grey when the chat is muted. */
@Composable
fun UnreadBadge(count: Int, muted: Boolean = false) {
    if (count <= 0) return
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (muted) MaterialTheme.extras.badgeMuted else MaterialTheme.extras.badge)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
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
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
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
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp),
    )
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


/** Shimmering placeholder rows (avatar + two text lines). Feels much faster than a spinner. */
@Composable
fun SkeletonList(rows: Int = 8, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "alpha",
    )
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f + 0.06f * alpha)
    Column(modifier.fillMaxSize()) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(block))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(block))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(block))
                }
            }
        }
    }
}
