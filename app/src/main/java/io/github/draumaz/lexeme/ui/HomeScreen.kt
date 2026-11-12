package io.github.draumaz.lexeme.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.draumaz.lexeme.ui.components.morphBackground
import io.github.draumaz.lexeme.ui.components.glass
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.theme.Wordmark
import kotlinx.coroutines.launch

private val Samples = listOf("serendipity", "water", "Hund", "水", "книга", "amour", "saudade", "猫")

@Composable
fun HomeScreen(
    recent: List<String>,
    randomBusy: Boolean,
    onOpen: (String) -> Unit,
    onRandom: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val seen = remember { HashSet<Any>() }
    val haptics = LocalHapticFeedback.current
    // No search bar up top here, so the content centres in the space above the search button.
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .fillMaxWidth()
            .padding(top = top + 24.dp, bottom = bottom + 124.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroMark(Modifier.bouncyEnter("hero", seen, 0))
        Spacer(Modifier.height(16.dp))
        Text(
            "Lexeme",
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = Wordmark, fontWeight = FontWeight.Light),
            modifier = Modifier.bouncyEnter("title", seen, 1),
        )
        Spacer(Modifier.height(16.dp))
        val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onRandom()
            },
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.height(ButtonDefaults.MediumContainerHeight).bouncyEnter("random", seen, 2).testTag("random_button"),
        ) {
            AnimatedContent(
                randomBusy,
                transitionSpec = {
                    scaleIn(fastSpec) togetherWith scaleOut(fastSpec)
                },
                label = "randomIcon",
            ) { busy ->
                if (busy) LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Rounded.Shuffle, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Surprise me", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(36.dp))

        if (recent.isNotEmpty()) {
            SectionLabel(Icons.Rounded.History, "Recent", Modifier.bouncyEnter("recentLabel", seen, 4)) {
                TextButton(onClick = onClearHistory, modifier = Modifier.testTag("clear_history")) { Text("Clear") }
            }
            WordChips(recent, seen, prefix = "recent", onOpen = onOpen)
        } else {
            SectionLabel(Icons.Rounded.TipsAndUpdates, "Try", Modifier.bouncyEnter("tryLabel", seen, 4)) {}
            WordChips(Samples, seen, prefix = "try", onOpen = onOpen)
        }
    }
    SettingsButton(
        onClick = onOpenSettings,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = top + 12.dp, end = 20.dp),
    )
    }
}

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized)
        Spacer(Modifier.width(4.dp))
        trailing()
    }
}

@Composable
private fun WordChips(words: List<String>, seen: MutableSet<Any>, prefix: String, onOpen: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        words.forEachIndexed { index, word -> key(word) {
            val source = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpen(word)
                },
                shape = RoundedCornerShape(if (index % 3 == 0) 20.dp else 14.dp),
                color = if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (index % 2 == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                interactionSource = source,
                modifier = Modifier
                    .pressScale(source, 0.88f)
                    .bouncyEnter("$prefix-$word", seen, 5 + index)
                    .testTag("${prefix}_chip_$index"),
            ) {
                Text(
                    word,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        } }
    }
}

/**
 * The app's mark: an expressive shape that keeps morphing through the shape set and slowly
 * turning, with the book icon riding on top. Tapping it gives a bouncy squish.
 */
@Composable
private fun HeroMark(modifier: Modifier = Modifier) {
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie12Sided, MaterialShapes.Flower, MaterialShapes.Clover4Leaf,
            MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided, MaterialShapes.Sunny,
        )
    }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val loop = rememberInfiniteTransition(label = "hero")
    val phase by loop.animateFloat(
        0f, shapes.size.toFloat(),
        infiniteRepeatable(tween(shapes.size * 2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    val squish = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme

    // Phase and spin are read only while drawing, so the endless morph never recomposes.
    Box(
        modifier
            .size(140.dp)
            .graphicsLayer { scaleX = squish.value; scaleY = squish.value }
            .clickable(interactionSource = null, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    squish.snapTo(0.78f)
                    squish.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f))
                }
            }
            .testTag("hero"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .morphBackground(
                    colors.primaryContainer,
                    morph = { morphs[phase.toInt().coerceIn(0, shapes.lastIndex)] },
                    // Hold each shape, then morph over the last 40% of its slot.
                    progress = {
                        val index = phase.toInt().coerceIn(0, shapes.lastIndex)
                        FastOutSlowInEasing.transform(((phase - index - 0.6f) / 0.4f).coerceIn(0f, 1f))
                    },
                    rotation = { spin },
                )
        )
        Icon(Icons.Rounded.AutoStories, null, Modifier.size(68.dp), tint = colors.onPrimaryContainer)
    }
}

/**
 * The landing page's search button, after Nickel's add button: a slowly turning scalloped
 * cookie that squishes when pressed and kicks round with a spring when tapped.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val idle = rememberInfiniteTransition(label = "fabSpin")
    val idleRotation by idle.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "fabIdle")
    val kick = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Box(
        modifier
            .size(SearchFabSize)
            .pressScale(interaction, 0.88f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                scope.launch { kick.animateTo(kick.value + 120f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                onClick()
            }
            .semantics { contentDescription = "Search" }
            .testTag("search_fab"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = idleRotation + kick.value }
                // Frosted glass, as in Nickel: mostly blur, with a wash of the (dynamic)
                // primaryContainer so the scallop's edge still reads over plain text. Before
                // Android 17 QPR2 it keeps the see-through tonal fill it always had.
                .glass(
                    shape = shape,
                    tint = colors.primaryContainer.copy(alpha = SearchFabGlassTint),
                    fallback = colors.primaryContainer.copy(alpha = 0.88f),
                )
        )
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(SearchFabSize * SearchFabIconScale))
    }
}

/**
 * The search scallop's size: Nickel's add button exactly (Expressive's medium FAB), which also
 * sits where Nickel's Scaffold puts its FAB, centred 16dp above the navigation bar.
 */
val SearchFabSize = 80.dp

/** The magnifier as a fraction of [SearchFabSize], as Nickel's plus is of its button. */
private const val SearchFabIconScale = 0.44f

/**
 * How much primaryContainer the search scallop's glass keeps: Nickel's add-button strength,
 * so the screen's main action holds its own over the page scrolling beneath it.
 */
private const val SearchFabGlassTint = 0.5f
