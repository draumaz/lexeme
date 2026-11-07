package io.github.draumaz.lexeme.ui.blocks

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.Image
import io.github.draumaz.lexeme.data.USER_AGENT
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.theme.LexemeTheme

/** Flips black line art to white for dark mode, keeping alpha. */
private val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )
)

/**
 * One image on a tile. Line art marked invertible sits on the surface and flips in dark mode;
 * everything else gets a pale paper tile so transparent PNGs stay legible in either theme.
 *
 * Built from plain layouts only (no subcomposition), because table cells measure their
 * content intrinsically and SubcomposeLayout can't answer intrinsic queries.
 */
@Composable
fun WikiImage(
    image: Image,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
) {
    val dark = LexemeTheme.dark
    val invert = image.invertInDark && dark
    val tile = when {
        invert -> MaterialTheme.colorScheme.surfaceContainerHighest
        dark -> Color(0xFFF1F0F4)
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val aspect = if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1f
    var loading by remember(image.url) { mutableStateOf(true) }
    Box(
        modifier
            .aspectRatio(aspect)
            .clip(shape)
            .background(tile),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = image.caption?.text ?: image.label,
            contentScale = ContentScale.Fit,
            colorFilter = if (invert) ColorFilter.colorMatrix(InvertMatrix) else null,
            onState = { loading = it is AsyncImagePainter.State.Loading },
            modifier = Modifier.matchParentSize(),
        )
        if (loading) LoadingIndicator(Modifier.size(40.dp))
    }
}

/** Photos fill the card (up to a comfortable width); small glyphs keep roughly their natural size. */
private fun Modifier.imageWidth(image: Image): Modifier =
    if (image.width >= 180) widthIn(max = 360.dp).fillMaxWidth() else width(image.width.dp * 1.25f)

@Composable
fun ImageGallery(images: List<Image>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        images.forEach { image ->
            Column(Modifier.imageWidth(image), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WikiImage(image, Modifier.fillMaxWidth())
                image.caption?.let {
                    RichTextView(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * The stroke-order card's body: animations big and centered, where the eye lands first,
 * then any static step-by-step diagrams full width beneath.
 */
@Composable
fun StrokeOrderStrip(images: List<Image>) {
    val animated = images.filter { it.animated }
    val diagrams = images.filterNot { it.animated }
    Column(
        Modifier.fillMaxWidth().testTag("stroke_order"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (animated.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                animated.forEach {
                    WikiImage(
                        it,
                        Modifier.width(if (animated.size == 1) 184.dp else 136.dp),
                        shape = RoundedCornerShape(32.dp),
                    )
                }
            }
        }
        diagrams.forEach { image ->
            val wide = image.width > image.height * 2
            WikiImage(
                image,
                if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(max = 184.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
fun StrokeOrderCard(images: List<Image>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(MaterialShapes.Flower, MaterialTheme.colorScheme.tertiaryContainer, size = 36.dp) {
                    Icon(Icons.Rounded.Brush, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Text("Stroke order", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            StrokeOrderStrip(images)
        }
    }
}

/** One shared player: starting a clip stops whatever was playing. */
@Stable
class AudioController(private val context: Context) {
    var playing by mutableStateOf<String?>(null)
        private set
    var preparing by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(url: String) {
        if (playing == url || preparing == url) stop() else play(url)
    }

    private fun play(url: String) {
        stop()
        preparing = url
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            runCatching {
                setDataSource(context, url.toUri(), mapOf("User-Agent" to USER_AGENT))
            }.onFailure { this@AudioController.stop(); return }
            setOnPreparedListener {
                preparing = null
                playing = url
                it.start()
            }
            setOnCompletionListener { this@AudioController.stop() }
            setOnErrorListener { _, _, _ -> this@AudioController.stop(); true }
            prepareAsync()
        }
    }

    fun stop() {
        player?.release()
        player = null
        playing = null
        preparing = null
    }
}

val LocalAudio = staticCompositionLocalOf<AudioController?> { null }

@Composable
fun AudioButton(block: Block.Audio) {
    val audio = LocalAudio.current ?: return
    val haptics = LocalHapticFeedback.current
    val playing = audio.playing == block.url
    val preparing = audio.preparing == block.url
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "rot")
    val source = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            audio.toggle(block.url)
        },
        shape = RoundedCornerShape(50),
        color = if (playing) colors.primary else colors.secondaryContainer,
        contentColor = if (playing) colors.onPrimary else colors.onSecondaryContainer,
        interactionSource = source,
        modifier = Modifier
            .pressScale(source, 0.9f)
            .testTag("audio_button")
            .semantics { contentDescription = "Play ${block.label}" },
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(
                shape = if (playing || preparing) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                color = if (playing) colors.onPrimary else colors.primary,
                size = 30.dp,
                rotation = { if (playing || preparing) rotation else 0f },
            ) {
                AnimatedContent(
                    targetState = playing to preparing,
                    transitionSpec = {
                        scaleIn(fastSpec) togetherWith
                            scaleOut(fastSpec)
                    },
                    label = "audioIcon",
                ) { (isPlaying, isPreparing) ->
                    when {
                        isPreparing -> LoadingIndicator(Modifier.size(22.dp), color = colors.onPrimary)
                        isPlaying -> Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp), tint = colors.primary)
                        else -> Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = colors.onPrimary)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(block.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
