package io.github.draumaz.lexeme.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.ui.components.ShapeBadge

/**
 * Find on page: the bar's query and position, shared between the bar (which steps through
 * matches) and the entry being read (which counts them, lights them up and scrolls to them).
 */
@Stable
class FindState {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    /** How many matches the open page holds; set by the page. */
    var total by mutableIntStateOf(0)
    /** The match being looked at, counting from the top of the page. */
    var current by mutableIntStateOf(0)
    /** Bumps on every step, so stepping back onto the same match still scrolls to it. */
    var steps by mutableIntStateOf(0)
    /** The bar's top edge in root pixels, so matches land above it rather than under it. */
    var barTop by mutableFloatStateOf(Float.NaN)

    /** The text actually searched for: blank queries find nothing. */
    val needle: String get() = query.takeIf { it.isNotBlank() }.orEmpty()

    fun next() = step(1)
    fun previous() = step(-1)

    private fun step(by: Int) {
        if (total == 0) return
        current = Math.floorMod(current + by, total)
        steps++
    }

    /** A new page: the old query means nothing there. */
    fun reset() {
        open = false
        query = ""
        total = 0
        current = 0
    }
}

/**
 * The find bar, docked above the keyboard: the field, "3 of 12", and steps up and down the page.
 * [modifier] goes on the bar itself, outermost, so it can carry the morph out of its button.
 */
@Composable
fun FindBar(
    find: FindState,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var focused by androidx.compose.runtime.remember { mutableStateOf(false) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val corner by animateDpAsState(if (focused) 20.dp else 30.dp, spatial, label = "findCorner")
    val elevation by animateDpAsState(if (focused) 8.dp else 3.dp, spatial, label = "findElevation")
    val container by animateColorAsState(
        if (focused) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "findColor",
    )
    val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    fun step(forward: Boolean) {
        if (find.total == 0) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (forward) find.next() else find.previous()
    }

    Surface(
        shape = RoundedCornerShape(corner),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(elevation, RoundedCornerShape(corner))
            .testTag("find_bar"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                ShapeBadge(MaterialShapes.Cookie9Sided, colors.tertiary, size = 34.dp) {
                    Icon(Icons.Rounded.FindInPage, null, Modifier.size(18.dp), tint = colors.onTertiary)
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = find.query,
                    onValueChange = { find.query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.tertiary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                    // Enter steps on, keeping the keyboard up, like find in a browser.
                    keyboardActions = KeyboardActions(onSearch = { step(forward = true) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .testTag("find_field"),
                    decorationBox = { inner ->
                        if (find.query.isEmpty()) {
                            Text("Find on page", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        inner()
                    },
                )
            }
            AnimatedVisibility(
                find.needle.isNotEmpty(),
                enter = scaleIn(fastSpec) + fadeIn(),
                exit = scaleOut(fastSpec) + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The count rolls like the language picker's: up when stepping down the page.
                    AnimatedContent(
                        targetState = find.total to find.current,
                        transitionSpec = {
                            val down = targetState.second >= initialState.second
                            (slideInVertically { if (down) it else -it } + fadeIn())
                                .togetherWith(slideOutVertically { if (down) -it else it } + fadeOut())
                        },
                        label = "findCount",
                    ) { (total, current) ->
                        Text(
                            if (total == 0) "None" else "${current + 1} of $total",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (total == 0) colors.error else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp).testTag("find_count"),
                        )
                    }
                    IconButton(onClick = { step(forward = false) }, enabled = find.total > 0, modifier = Modifier.testTag("find_previous")) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(onClick = { step(forward = true) }, enabled = find.total > 0, modifier = Modifier.testTag("find_next")) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("find_close")) {
                Icon(Icons.Rounded.Close, contentDescription = "Close find")
            }
        }
    }
}
