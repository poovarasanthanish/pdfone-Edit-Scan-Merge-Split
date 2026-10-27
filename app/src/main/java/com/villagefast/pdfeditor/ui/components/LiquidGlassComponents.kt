package com.villagefast.pdfeditor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import kotlin.math.cos
import kotlin.math.sin

/**
 * CompositionLocal to provide [LayerBackdrop] to components within a [LiquidBackground].
 */
val LocalBackdropState = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * A container that provides a dynamic, animated liquid background.
 * It uses a [LayerBackdrop] to capture the background content and allows child components
 * like [GlassCard] to apply glassmorphic effects.
 *
 * @param modifier The modifier to be applied to the background.
 * @param content The content to be displayed over the animated background.
 */
@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backdropState = rememberLayerBackdrop()
    val isDark = LocalIsDarkTheme.current
    
    val infiniteTransition = rememberInfiniteTransition(label = stringResource(R.string.label_background))
    val timeState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = stringResource(R.string.label_time)
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Recording layer
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdropState)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val time = timeState.value
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                drawRect(color = if (isDark) Color(0xFF0D081F) else Color(0xFFF5F3FF))

                val blobColors = if (isDark) {
                    listOf(
                        Color(0xFF311B92), // Midnight Purple
                        Color(0xFF673AB7), // Deep Purple
                        Color(0xFF3F51B5), // Indigo
                        Color(0xFF9C27B0)  // Purple 500
                    )
                } else {
                    listOf(
                        Color(0xFFEDE7F6), // Lavender 50
                        Color(0xFFD1C4E9), // Lavender 100
                        Color(0xFFF8BBD0), // Pink 100
                        Color(0xFFE1BEE7)  // Purple 100
                    )
                }

                blobColors.forEachIndexed { index, color ->
                    val x = canvasWidth / 2 + (canvasWidth / 3) * sin(time + index * 1.5f)
                    val y = canvasHeight / 2 + (canvasHeight / 4) * cos(time * 0.8f + index * 2.1f)
                    val radius = (canvasWidth / 1.5f) * (0.6f + 0.2f * sin(time * 0.4f + index))
                    
                    // Main Blob
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = if (isDark) 0.5f else 0.7f), Color.Transparent),
                            center = Offset(x, y),
                            radius = radius
                        ),
                        center = Offset(x, y),
                        radius = radius
                    )

                    // 3D Highlight
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(x - radius/3, y - radius/3),
                            radius = radius/2
                        ),
                        center = Offset(x - radius/3, y - radius/3),
                        radius = radius/2
                    )
                }
            }
        }
        
        CompositionLocalProvider(LocalBackdropState provides backdropState) {
            content()
        }
    }
}

/**
 * A card component with a glassmorphic effect.
 * It applies blur and vibrancy effects based on the underlying [LiquidBackground].
 *
 * @param modifier The modifier to be applied to the card.
 * @param shape The shape of the card.
 * @param blurRadius The radius of the blur effect.
 * @param content The content to be displayed inside the card.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Float = 30f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val backdropState = LocalBackdropState.current
    val isDark = LocalIsDarkTheme.current

    if (backdropState == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(contentPadding)) { content() }
        }
        return
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawBackdrop(
                backdrop = backdropState,
                shape = { shape },
                effects = {
                    blur(80f)
                    vibrancy()
                }
            )
            .background(
                if (isDark) Color(0xFF191336).copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.3f)
            )
            .border(
                0.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

/**
 * A center-aligned top app bar with a glassmorphic effect.
 *
 * @param title The title to be displayed in the app bar.
 * @param modifier The modifier to be applied to the app bar.
 * @param navigationIcon The icon to be displayed at the start of the app bar.
 * @param actions The actions to be displayed at the end of the app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backdropState = LocalBackdropState.current
    val isDark = LocalIsDarkTheme.current

    CenterAlignedTopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .then(
                if (backdropState != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdropState,
                        shape = { RoundedCornerShape(0.dp) },
                        effects = {
                            blur(60f)
                            vibrancy()
                            lens(
                                refractionHeight = 15f,
                                refractionAmount = 0.3f,
                                chromaticAberration = true
                            )
                        }
                    )
                } else Modifier
            )
            .background((if (isDark) Color.Black else Color.White).copy(alpha = 0.15f))
    )
}

/**
 * A scaffold that automatically applies a [LiquidBackground].
 * Sets the container color to [Color.Transparent] to show the animated background.
 *
 * @param topBar The top bar of the scaffold.
 * @param bottomBar The bottom bar of the scaffold.
 * @param floatingActionButton The floating action button of the scaffold.
 * @param content The main content of the scaffold.
 */
@Composable
fun LiquidScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    showBanner: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    LiquidBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = topBar,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    bottomBar()
                    if (showBanner) {
                        AdBanner()
                    }
                }
            },
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            content = content
        )
    }
}
