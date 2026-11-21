package io.github.draumaz.lexeme.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import kotlin.math.roundToInt

private const val IgnoredLeading = "'’‘\"“-–*"

private val Latin = ('A'..'Z').map(Char::toString)

/** Russian's initials, less the letters almost nothing starts with (Ъ Ы Ь); Ё sorts with Е. */
private val Cyrillic = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЭЮЯ".map(Char::toString)

private val Greek = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ".map(Char::toString)

/**
 * The alphabet a list of titles is sorted in, judged from their first letters: Latin, Cyrillic
 * or Greek. Null when it's none of those (Chinese categories sort by radical, say), since a
 * letter index would only mislead there.
 */
fun alphabetFor(titles: List<String>): List<String>? {
    val scripts = titles.asSequence().take(200)
        .mapNotNull { t -> t.firstOrNull(Char::isLetter)?.let { Character.UnicodeScript.of(it.code) } }
        .groupingBy { it }.eachCount()
    return when (scripts.maxByOrNull { it.value }?.key) {
        Character.UnicodeScript.LATIN -> Latin
        Character.UnicodeScript.CYRILLIC -> Cyrillic
        Character.UnicodeScript.GREEK -> Greek
        else -> null
    }
}

/**
 * Where [title] files in [alphabet]: its first letter, unaccented and upper-cased (so "'m",
 * "Ärger" and "ёж" file under M, A and Е), as an index; -1 if that letter isn't in it.
 * Leading apostrophes, quotes, hyphens and a reconstruction's asterisk are passed over, as
 * Wiktionary's sorting does; a title opening on a digit or symbol ("1% milk", "$ex") files
 * under no letter, since it sorts ahead of them all.
 */
fun letterIndex(title: String, alphabet: List<String>): Int {
    val first = title.trimStart { it in IgnoredLeading }.firstOrNull()?.takeIf(Char::isLetter) ?: return -1
    val bare = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
    val letter = if (bare == 'Ё') 'Е' else bare
    return alphabet.indexOf(letter.toString())
}

/**
 * The quick-scroll sidebar: the alphabet down the end edge of a long list in a frosted pill.
 * Touching or dragging along it picks letters, each with a haptic tick, while a scalloped
 * bubble beside the finger shows the letter in large type. [onScrub] fires for every letter
 * passed over (for live scrolling); [onRelease] with the letter the finger lifted on.
 * [current] is the letter the list is showing, which the pill highlights while it's idle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlphabetScrollbar(
    alphabet: List<String>,
    current: Int,
    onScrub: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var active by remember { mutableStateOf(false) }
    var picked by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableStateOf(0f) }
    val latestScrub by rememberUpdatedState(onScrub)
    val latestRelease by rememberUpdatedState(onRelease)

    // The pill widens under the finger, like a handle being grabbed.
    val trackWidth by animateDpAsState(
        if (active) 40.dp else 28.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "trackWidth",
    )

    BoxWithConstraints(modifier.fillMaxHeight().width(BubbleSize + 12.dp + 44.dp)) {
        val heightPx = constraints.maxHeight.toFloat()
        val slot = heightPx / alphabet.size
        fun indexAt(y: Float) = (y / slot).toInt().coerceIn(0, alphabet.lastIndex)

        // The letters. A wide, invisible touch strip around the pill makes it easy to grab,
        // and is kept clear of the system's edge-swipe back gesture.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(44.dp)
                .systemGestureExclusion()
                .semantics { contentDescription = "Quick scroll by letter" }
                .testTag("quick_scroll")
                .pointerInput(alphabet) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        active = true
                        fingerY = down.position.y
                        var index = indexAt(down.position.y)
                        picked = index
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        latestScrub(index)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            fingerY = change.position.y
                            val now = indexAt(change.position.y)
                            if (now != index) {
                                index = now
                                picked = now
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                latestScrub(now)
                            }
                        }
                        active = false
                        latestRelease(index)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(vertical = 4.dp)
                    .width(trackWidth)
                    .fillMaxHeight()
                    .glass(RoundedCornerShape(50), tint = colors.surfaceContainerHigh.copy(alpha = 0.6f), fallback = colors.surfaceContainerHigh.copy(alpha = 0.9f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Letters share the height evenly; on a short screen every other one is a dot,
                // though each still answers to the touch.
                val roomy = with(density) { slot.toDp() } >= 15.dp
                alphabet.forEachIndexed { i, letter ->
                    val isPicked = active && i == picked
                    val isCurrent = !active && i == current
                    val tint by animateColorAsState(
                        when {
                            isPicked -> colors.onPrimary
                            isCurrent -> colors.primary
                            else -> colors.onSurfaceVariant
                        },
                        label = "letterTint",
                    )
                    val grow by animateFloatAsState(
                        if (isPicked) 1.25f else 1f,
                        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        label = "letterGrow",
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (isPicked) Box(Modifier.size(22.dp).background(colors.primary, CircleShape))
                        Text(
                            if (roomy || i % 2 == 0) letter else "•",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isPicked || isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = tint,
                            modifier = Modifier.graphicsLayer { scaleX = grow; scaleY = grow },
                        )
                    }
                }
            }
        }

        // The bubble: a turning scallop of primaryContainer beside the finger, the letter big.
        val bubblePx = with(density) { BubbleSize.toPx() }
        val bubbleY by animateFloatAsState(
            (fingerY - bubblePx / 2).coerceIn(0f, (heightPx - bubblePx).coerceAtLeast(0f)),
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessHigh),
            label = "bubbleY",
        )
        val spin by animateFloatAsState(
            picked * 40f,
            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
            label = "bubbleSpin",
        )
        AnimatedVisibility(
            visible = active && picked >= 0,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.3f, transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
            exit = scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.3f, transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(),
            modifier = Modifier.offset { IntOffset(0, bubbleY.roundToInt()) },
        ) {
            Box(Modifier.size(BubbleSize), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(BubbleSize)
                        .graphicsLayer { rotationZ = spin }
                        .background(colors.primaryContainer, MaterialShapes.Cookie9Sided.toShape())
                )
                AnimatedContent(
                    targetState = picked,
                    transitionSpec = {
                        val down = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) { if (down) it / 2 else -it / 2 } + fadeIn())
                            .togetherWith(slideOutVertically { if (down) -it / 2 else it / 2 } + fadeOut())
                    },
                    label = "bubbleLetter",
                ) { i ->
                    Text(
                        alphabet.getOrElse(i) { "" },
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = colors.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

private val BubbleSize = 56.dp

/** Lists shorter than this many pages scroll fine by hand; no sidebar. */
const val QuickScrollMinPages = 100
