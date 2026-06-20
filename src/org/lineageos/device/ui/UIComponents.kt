package org.lineageos.device.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun VisualCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "scale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
                content = illustration,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustrationColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(illustrationColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SwitchFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    illustrationColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (checked) illustrationColor else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) iconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun TriggersIllustration() {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary),
            )
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.tertiary),
            )
        }
    }
}

@Composable
fun LEDsIllustration() {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val infiniteTransition = rememberInfiniteTransition(label = "ledGlow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha"
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = glowAlpha))
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary.copy(alpha = glowAlpha))
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(alpha = glowAlpha))
            )
        }
    }
}

val GithubIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "Github",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = androidx.compose.ui.graphics.vector.PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
                curveTo(2f, 16.418f, 4.865f, 20.166f, 8.835f, 21.488f)
                curveTo(9.335f, 21.58f, 9.518f, 21.27f, 9.518f, 21.01f)
                curveTo(9.518f, 20.52f, 9.509f, 19.535f, 9.504f, 18.236f)
                curveTo(6.722f, 18.84f, 6.135f, 16.895f, 6.135f, 16.895f)
                curveTo(5.68f, 15.74f, 5.023f, 15.433f, 5.023f, 15.433f)
                curveTo(4.113f, 14.81f, 5.092f, 14.823f, 5.092f, 14.823f)
                curveTo(6.098f, 14.894f, 6.628f, 15.856f, 6.628f, 15.856f)
                curveTo(7.522f, 17.39f, 8.972f, 16.946f, 9.544f, 16.685f)
                curveTo(9.635f, 16.035f, 9.896f, 15.592f, 10.185f, 15.34f)
                curveTo(7.964f, 15.087f, 5.628f, 14.23f, 5.628f, 10.472f)
                curveTo(5.628f, 9.402f, 6.012f, 8.525f, 6.643f, 7.828f)
                curveTo(6.541f, 7.576f, 6.204f, 6.578f, 6.74f, 5.234f)
                curveTo(6.74f, 5.234f, 7.568f, 4.97f, 9.502f, 6.278f)
                curveTo(10.29f, 6.059f, 11.144f, 5.95f, 12f, 5.946f)
                curveTo(12.856f, 5.95f, 13.71f, 6.059f, 14.499f, 6.278f)
                curveTo(16.432f, 4.97f, 17.26f, 5.234f, 17.26f, 5.234f)
                curveTo(17.797f, 6.578f, 17.46f, 7.576f, 17.358f, 7.828f)
                curveTo(17.99f, 8.525f, 18.372f, 9.402f, 18.372f, 10.472f)
                curveTo(18.372f, 14.24f, 16.033f, 15.084f, 13.805f, 15.33f)
                curveTo(14.168f, 15.642f, 14.492f, 16.257f, 14.492f, 17.202f)
                curveTo(14.492f, 18.558f, 14.48f, 19.65f, 14.48f, 21.01f)
                curveTo(14.48f, 21.273f, 14.662f, 21.586f, 15.17f, 21.488f)
                curveTo(19.136f, 20.163f, 22f, 16.417f, 22f, 12f)
                curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
                close()
            }
        }.build()
    }

@Composable
fun MadeWithLoveFooter() {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "heartBeat")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Made with ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Love",
                tint = Color(0xFFE91E63),
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
            )
            Text(
                text = " by AYIKxD",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AYIKxD"))
                context.startActivity(intent)
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = GithubIcon,
                    contentDescription = "GitHub",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
