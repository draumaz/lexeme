package io.github.draumaz.lexeme.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.bouncyEnter

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    canGoBack: Boolean,
    onBack: () -> Unit,
    suggestions: Suggestions,
    recent: List<String>,
    modifier: Modifier = Modifier,
    /** Docked at the bottom (the landing page): suggestions open upward, best match nearest the field. */
    fromBottom: Boolean = false,
    /** Applied to the field itself, outermost; the docked bar passes its shared-bounds morph here. */
    fieldModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val corner by animateDpAsState(if (focused) 20.dp else 30.dp, spatial, label = "searchCorner")
    val elevation by animateDpAsState(if (focused) 8.dp else 0.dp, spatial, label = "searchElevation")
    val container by animateColorAsState(
        if (focused) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "searchColor",
    )
    // Typing shows live suggestions; an empty focused field offers recent words instead.
    val showingRecent = query.isBlank()
    val fresh = suggestions.query == query.trim()
    val items = if (showingRecent) recent.take(6) else if (fresh) suggestions.items.take(8) else emptyList()
    // The row Enter will open gets highlighted, so pressing search is never a surprise.
    val picked = if (showingRecent) null else suggestions.pickFor(query)

    // The field keeps its own selection, so filling in a suggestion can put the caret at the
    // end. The query flow lags typing by a frame, so it only overwrites the field while the
    // field isn't being typed in (opening a page clears it; the other bar's typing shows here).
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    fun setText(text: String) {
        field = TextFieldValue(text, TextRange(text.length))
        onQueryChange(text)
    }
    LaunchedEffect(query, focused) {
        if (!focused && field.text != query) field = TextFieldValue(query, TextRange(query.length))
    }

    val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    @Composable
    fun Field() {
        Surface(
            shape = RoundedCornerShape(corner),
            color = container,
            modifier = fieldModifier
                .fillMaxWidth()
                .height(60.dp)
                .shadow(elevation, RoundedCornerShape(corner)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                AnimatedContent(
                    targetState = canGoBack || focused,
                    transitionSpec = {
                        (scaleIn(fastSpec) + fadeIn()) togetherWith
                            (scaleOut(fastSpec) + fadeOut())
                    },
                    label = "leading",
                ) { back ->
                    if (back) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            ShapeBadge(MaterialShapes.Cookie9Sided, colors.primary, size = 34.dp) {
                                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = colors.onPrimary)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            val changed = it.text != field.text
                            field = it
                            if (changed) onQueryChange(it.text)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit(picked ?: field.text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { onFocusChange(it.isFocused) }
                            .testTag("search_field"),
                        decorationBox = { inner ->
                            if (field.text.isEmpty()) {
                                Text(
                                    "Search Wiktionary",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                AnimatedVisibility(
                    field.text.isNotEmpty(),
                    enter = scaleIn(fastSpec) + fadeIn(),
                    exit = scaleOut(fastSpec) + fadeOut(),
                ) {
                    IconButton(onClick = { setText("") }, modifier = Modifier.testTag("clear_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            }
        }

    }

    @Composable
    fun ColumnScope.Dropdown() {
        val edge = if (fromBottom) Alignment.Bottom else Alignment.Top
        AnimatedVisibility(
            visible = focused && items.isNotEmpty(),
            enter = expandVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), expandFrom = edge) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium), shrinkTowards = edge) + fadeOut(),
            // Docked at the bottom, the list takes whatever room the keyboard leaves above the field.
            modifier = if (fromBottom) Modifier.weight(1f, fill = false) else Modifier,
        ) {
            val seen = remember(showingRecent) { HashSet<Any>() }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(28.dp)).testTag("suggestions"),
            ) {
                // Scrolls when the keyboard leaves less room than the list needs.
                Column(
                    Modifier
                        .then(if (fromBottom) Modifier else Modifier.heightIn(max = 420.dp))
                        .verticalScroll(rememberScrollState(), reverseScrolling = fromBottom)
                        .padding(vertical = 8.dp)
                ) {
                    val ordered = if (fromBottom) items.withIndex().reversed() else items.withIndex().toList()
                    ordered.forEach { (index, item) -> key(item) {
                        val highlighted = item == picked
                        val rowColor by animateColorAsState(
                            if (highlighted) colors.secondaryContainer else colors.surfaceContainerHigh,
                            MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "rowColor",
                        )
                        Surface(
                            onClick = { onSubmit(item) },
                            color = rowColor,
                            contentColor = if (highlighted) colors.onSecondaryContainer else colors.onSurface,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .bouncyEnter(item, seen, index)
                                .testTag(if (highlighted) "suggestion_${index}_picked" else "suggestion_$index"),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when {
                                        showingRecent -> Icons.Rounded.History
                                        highlighted -> Icons.AutoMirrored.Rounded.KeyboardReturn
                                        else -> Icons.Rounded.Search
                                    },
                                    contentDescription = if (highlighted) "Enter opens this" else null,
                                    tint = if (highlighted) colors.onSecondaryContainer else colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    item,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { setText(item) }, modifier = Modifier.size(32.dp)) {
                                    Icon(if (fromBottom) Icons.Rounded.SouthWest else Icons.Rounded.NorthWest, contentDescription = "Fill in $item", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } }
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp, if (fromBottom) Alignment.Bottom else Alignment.Top)) {
        if (fromBottom) {
            Dropdown()
            Field()
        } else {
            Field()
            Dropdown()
        }
    }
}
