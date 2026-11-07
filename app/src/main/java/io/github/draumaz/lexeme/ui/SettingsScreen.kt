package io.github.draumaz.lexeme.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.graphics.shapes.Morph
import io.github.draumaz.lexeme.ui.components.MorphShape
import io.github.draumaz.lexeme.ui.components.pressScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.draumaz.lexeme.BuildConfig
import io.github.draumaz.lexeme.R
import io.github.draumaz.lexeme.data.SettingsStore
import io.github.draumaz.lexeme.data.ThemeMode
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.bouncyEnter
import io.github.draumaz.lexeme.ui.components.segmentShape
import kotlinx.coroutines.launch

private const val AUTHOR_URL = "https://github.com/draumaz"
private const val REPO_URL = "https://github.com/draumaz/lexeme"
private const val WIKTIONARY_URL = "https://en.wiktionary.org"

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    recentCount: Int,
    onClearHistory: () -> Unit,
    openExternal: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val seen = remember { HashSet<Any>() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = LocalHapticFeedback.current
    val monetAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(top = top + 12.dp, bottom = bottom + 24.dp)
            .padding(horizontal = 16.dp)
            .testTag("settings"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.testTag("settings_back"),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(16.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMediumEmphasized)
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("Appearance", Modifier.bouncyEnter("appearance", seen, 0))
        SettingRow(
            icon = Icons.Rounded.Palette,
            badge = 0,
            title = "Material You colors",
            subtitle = if (monetAvailable) "Match your wallpaper" else "Needs Android 12 or newer",
            first = true, last = false,
            enabled = monetAvailable,
            onClick = {
                haptics.performHapticFeedback(if (dynamicColor) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                settings.setDynamicColor(!dynamicColor)
            },
            role = Role.Switch,
            modifier = Modifier.bouncyEnter("monet", seen, 1).testTag("setting_monet"),
        ) {
            Switch(
                checked = dynamicColor && monetAvailable,
                onCheckedChange = null,
                enabled = monetAvailable,
                thumbContent = if (dynamicColor && monetAvailable) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
        SegmentSurface(first = false, last = true, modifier = Modifier.bouncyEnter("theme", seen, 2)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RowIcon(Icons.Rounded.BrightnessAuto, badge = 1)
                    Spacer(Modifier.width(16.dp))
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("setting_theme")) {
                    modes.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                settings.setThemeMode(mode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(themeMode == mode) {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.System -> Icons.Rounded.BrightnessAuto
                                            ThemeMode.Light -> Icons.Rounded.LightMode
                                            ThemeMode.Dark -> Icons.Rounded.DarkMode
                                        },
                                        null,
                                        Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                }
                            },
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}"),
                        ) { Text(mode.name) }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("History", Modifier.bouncyEnter("history", seen, 3))
        SettingRow(
            icon = Icons.Rounded.DeleteSweep,
            badge = 2,
            title = "Clear recent lookups",
            subtitle = when (recentCount) {
                0 -> "Nothing to clear"
                1 -> "1 word on the home screen"
                else -> "$recentCount words on the home screen"
            },
            first = true, last = true,
            enabled = recentCount > 0,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClearHistory()
            },
            modifier = Modifier.bouncyEnter("clear", seen, 4).testTag("setting_clear_history"),
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("About", Modifier.bouncyEnter("about", seen, 5))
        SegmentSurface(first = true, last = false, onClick = { openExternal(AUTHOR_URL) }, modifier = Modifier.bouncyEnter("author", seen, 6).testTag("about_author")) {
            RowContent(
                leading = {
                    Image(
                        painterResource(R.drawable.draumaz),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                },
                title = "draumaz",
                subtitle = "Developer",
                trailing = { OpenIcon() },
            )
        }
        SettingRow(
            icon = Icons.Rounded.Code,
            badge = 3,
            title = "Lexeme ${BuildConfig.VERSION_NAME}",
            subtitle = "Source code on GitHub",
            first = false, last = false,
            onClick = { openExternal(REPO_URL) },
            modifier = Modifier.bouncyEnter("repo", seen, 7).testTag("about_repo"),
        ) { OpenIcon() }
        SettingRow(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            badge = 4,
            title = "Wiktionary",
            subtitle = "Entries are from Wiktionary, under CC BY-SA 4.0",
            first = false, last = true,
            onClick = { openExternal(WIKTIONARY_URL) },
            modifier = Modifier.bouncyEnter("wiktionary", seen, 8).testTag("about_wiktionary"),
        ) { OpenIcon() }

        Spacer(Modifier.height(28.dp))
        Text(
            "Lexeme ${BuildConfig.VERSION_NAME} · Google Sans Flex and Noto Serif under the SIL Open Font License",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("settings_footer"),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

/** One segment of a grouped list: round ends on the group's outside, tight corners between rows. */
@Composable
private fun SegmentSurface(
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = segmentShape(first, last, outer = 24.dp, inner = 6.dp)
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.padding(bottom = 3.dp)) {
        if (onClick != null) {
            Surface(onClick = onClick, enabled = enabled, shape = shape, color = color, modifier = Modifier.fillMaxWidth()) { content() }
        } else {
            Surface(shape = shape, color = color, modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    badge: Int,
    title: String,
    subtitle: String,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SegmentSurface(first, last, modifier, enabled, role, onClick) {
        RowContent(
            leading = { RowIcon(icon, badge) },
            title = title,
            subtitle = subtitle,
            trailing = trailing,
            dimmed = !enabled,
        )
    }
}

@Composable
private fun RowContent(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)?,
    dimmed: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .graphicsLayer { alpha = if (dimmed) 0.5f else 1f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** An icon on an expressive shape; each row gets a different one. */
@Composable
private fun RowIcon(icon: ImageVector, badge: Int) {
    val shapes = remember { listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Flower) }
    ShapeBadge(shapes[badge % shapes.size], MaterialTheme.colorScheme.primaryContainer, size = 40.dp) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun OpenIcon() {
    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
}

/**
 * The home screen's settings button: a small scallop that keeps turning and breathes between
 * two cookie shapes, kicking round with a spring when tapped.
 */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "settingsLoop")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "settingsSpin")
    val morphProgress by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "settingsMorph",
    )
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided) }
    val kick = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(44.dp)
            .pressScale(interaction, 0.86f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                scope.launch { kick.animateTo(kick.value + 120f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                onClick()
            }
            .semantics { contentDescription = "Settings" }
            .testTag("settings_button"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = spin + kick.value }
                .background(colors.secondaryContainer, MorphShape(morph, morphProgress))
        )
        Icon(
            Icons.Rounded.Settings,
            contentDescription = null,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -kick.value * 0.5f },
        )
    }
}
