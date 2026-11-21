package io.github.draumaz.lexeme.ui.blocks

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import io.github.draumaz.lexeme.data.Block
import io.github.draumaz.lexeme.data.Image
import io.github.draumaz.lexeme.data.USER_AGENT
import io.github.draumaz.lexeme.ui.components.ShapeBadge
import io.github.draumaz.lexeme.ui.components.pressScale
import io.github.draumaz.lexeme.ui.components.rememberSpin
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

/** How tall photos stand in a gallery, so pictures never push the definitions off the first screen. */
private val PhotoHeight = 150.dp
/** Wide photos (panoramas, diagrams) stop here and come out shorter instead. */
private val MaxPhotoWidth = 280.dp
/** Cards never get so narrow that a caption can't breathe. */
private val MinCardWidth = 132.dp

/** A photo's inline width: one shared height, so a tall photo narrows rather than towering. */
private fun photoWidth(image: Image) = minOf(MaxPhotoWidth, PhotoHeight * image.aspect)

private val Image.aspect: Float
    get() = if (width > 0 && height > 0) width.toFloat() / height else 1f

/** Small glyphs and symbols, shown at roughly their natural size rather than blown up. */
private val Image.small: Boolean get() = width in 1 until 180

/** Photos, as opposed to line art and glyphs, which cropping would spoil. */
private val Image.photo: Boolean get() = !small && !invertInDark

/**
 * Several photos become a Material 3 multi-browse carousel: one large picture with the next
 * peeking beside it, morphing as you swipe, each captioned beneath and opening large on a tap.
 * A lone picture, or any line art or glyphs, keeps the strip of [ImageCard]s instead, since
 * the carousel crops.
 */
@Composable
fun ImageGallery(images: List<Image>) {
    if (images.size >= 2 && images.all { it.photo }) PhotoCarousel(images) else CardStrip(images)
}

/** Inline photo carousel height: big enough to see, small enough to leave the definitions in view. */
private val CarouselHeight = 190.dp

/** Room under each carousel photo for its caption: one line, with breathing space. */
private val CaptionSlot = 36.dp

/**
 * How far the carousel reaches past the text on each side: the padding of the card it sits in,
 * so pictures swipe out to the card's edges and slide under them, while at rest the first one
 * still lines up with the text.
 */
private val CarouselBleed = 18.dp

/** Widens this element by [bleed] each side, past its parent's padding. */
private fun Modifier.bleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) return@layout measurable.measure(constraints).let { layout(it.width, it.height) { it.place(0, 0) } }
    val extra = bleed.roundToPx()
    val wide = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
private fun PhotoCarousel(images: List<Image>) {
    val colors = MaterialTheme.colorScheme
    val viewer = LocalImageViewer.current
    val haptics = LocalHapticFeedback.current
    val state = rememberCarouselState { images.size }
    val captioned = images.any { it.caption != null }
    Column(Modifier.fillMaxWidth().testTag("image_carousel")) {
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 240.dp,
            itemSpacing = 8.dp,
            // A fling moves several pictures, not one, so long galleries go quicker.
            flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = CarouselBleed),
            modifier = Modifier.fillMaxWidth().bleed(CarouselBleed).height(CarouselHeight + if (captioned) CaptionSlot else 0.dp),
        ) { i ->
            val image = images[i]
            val key = remember { Any() }
            val bounds = remember { SourceBounds() }
            Column(
                Modifier
                    .fillMaxSize()
                    .maskClip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceContainerHighest)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewer?.open(image, key, bounds.rect)
                    }
                    .testTag("carousel_image_$i"),
            ) {
                // The photo's slot keeps its size while the photo is off in the viewer. Where it
                // sits on screen is just the part the carousel's mask shows, which is what the
                // picture flies out of and lands back into.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { c ->
                            val full = Rect(c.positionInRoot(), c.size.toSize())
                            val mask = carouselItemDrawInfo.maskRect
                            val shown = if (mask.width > 0f) Rect(full.left + mask.left, full.top, full.left + mask.right, full.bottom) else full
                            bounds.track(shown.intersect(c.boundsInRoot()), key, viewer)
                        },
                ) {
                    if (viewer?.hiddenKey != key) FlyingImage(image, Modifier.fillMaxSize(), CarouselPhotoShape)
                }
                // Every card keeps the same caption slot, so the photos line up whether or not
                // they have one. The caption follows the card's visible part rather than the
                // full-size picture under it, so it starts at the card's edge and shortens as
                // the card narrows; large and medium cards keep theirs, small peeks fade it.
                if (captioned) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(CaptionSlot)
                            .layout { measurable, constraints ->
                                val mask = carouselItemDrawInfo.maskRect
                                val visible = if (mask.width > 0f) mask.width.toInt().coerceAtMost(constraints.maxWidth) else constraints.maxWidth
                                val placeable = measurable.measure(constraints.copy(minWidth = visible, maxWidth = visible))
                                layout(constraints.maxWidth, placeable.height) { placeable.place(mask.left.toInt(), 0) }
                            }
                            .graphicsLayer {
                                val info = carouselItemDrawInfo
                                alpha = if (info.maxSize <= 0f) 1f else ((info.size / info.maxSize - 0.4f) / 0.15f).coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        image.caption?.let {
                            RichTextView(
                                it,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pictures as a strip of expressive cards: each its own rounded container with the caption
 * tucked inside, all one height, scrolling sideways when they run past the page rather than
 * stacking up above the definitions. A card squishes when pressed and opens the picture large.
 */
@Composable
private fun CardStrip(images: List<Image>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("image_gallery"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        images.forEachIndexed { index, image -> ImageCard(image, index) }
    }
}

@Composable
private fun ImageCard(image: Image, index: Int) {
    val colors = MaterialTheme.colorScheme
    val viewer = LocalImageViewer.current
    // This card's own key, so the same picture shown twice on a page flies from the one tapped.
    val key = remember { Any() }
    // Opening scrolls the card fully into view behind the viewer, so the picture flies home
    // into a slot it can be seen in, rather than landing half outside the strip and snapping.
    val bringIntoView = remember { BringIntoViewRequester() }
    val open = viewer?.key == key
    LaunchedEffect(open) { if (open) bringIntoView.bringIntoView() }
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Pressing rounds the card off further, the expressive shape morph, as well as squishing it.
    val corner by animateDpAsState(
        if (pressed) 36.dp else 26.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "imageCardCorner",
    )
    val pictureWidth = if (image.small) image.width.dp * 1.25f else photoWidth(image)
    val bounds = remember { SourceBounds() }
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            viewer?.open(image, key, bounds.rect)
        },
        shape = RoundedCornerShape(corner),
        // Alternate tones down the strip, so neighbouring cards read as separate pictures.
        color = if (index % 2 == 0) colors.surfaceContainerHigh else colors.surfaceContainerHighest,
        interactionSource = source,
        modifier = Modifier
            .pressScale(source, 0.95f)
            .width(maxOf(pictureWidth + 16.dp, MinCardWidth))
            .bringIntoViewRequester(bringIntoView)
            .testTag("image_card_$index"),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The picture's slot keeps its size while the picture itself is off in the viewer.
            Box(
                Modifier
                    .width(pictureWidth)
                    .aspectRatio(image.aspect)
                    .onGloballyPositioned { bounds.track(it.boundsInRoot(), key, viewer) },
            ) {
                val shape = RoundedCornerShape((corner - 8.dp).coerceAtLeast(12.dp))
                if (viewer?.hiddenKey != key) FlyingImage(image, Modifier.fillMaxSize(), shape)
            }
            image.caption?.let {
                RichTextView(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Which picture is open full size, the key of the slot it flew out of, and where that slot is
 * on screen. The slot stays empty ([hiddenKey]) while the picture is open and on its way home.
 */
@Stable
class ImageViewerState {
    var image by mutableStateOf<Image?>(null)
        private set
    var key by mutableStateOf<Any?>(null)
        private set
    var hiddenKey by mutableStateOf<Any?>(null)
        private set
    /** The open picture's slot, in root coordinates; kept current if the page moves under it. */
    var source by mutableStateOf(Rect.Zero)
        internal set

    fun open(image: Image, key: Any, from: Rect) {
        this.image = image
        this.key = key
        hiddenKey = key
        source = from
    }

    fun close() {
        key = null
    }

    /** The picture is back in its slot. */
    internal fun landed() {
        hiddenKey = null
        image = null
    }
}

val LocalImageViewer = staticCompositionLocalOf<ImageViewerState?> { null }

/** Where a picture's slot is on screen, updated as it's laid out; read when it's tapped. */
private class SourceBounds {
    var rect = Rect.Zero
        private set

    fun track(rect: Rect, key: Any, viewer: ImageViewerState?) {
        this.rect = rect
        // While its picture is out, the viewer follows the slot, so the picture lands where it now is.
        if (viewer != null && viewer.hiddenKey == key) viewer.source = rect
    }
}

/**
 * The flight: a smooth spring that settles without overshooting, so a picture flying home
 * lands in its slot rather than bouncing past its edges.
 */
private val PictureFlight = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/** A carousel photo's corners: rounded on top, square where it meets its caption strip. */
private val CarouselPhotoShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * A picture as it flies: cropped to fill whatever size it's given and clipped to [shape] at
 * that size, so it stays rounded and whole at every step of a flight. Decoded once at full
 * size, so resizing mid-flight never refetches it.
 */
@Composable
private fun FlyingImage(image: Image, modifier: Modifier, shape: RoundedCornerShape) {
    val context = LocalContext.current
    val dark = LexemeTheme.dark
    val invert = image.invertInDark && dark
    val tile = when {
        invert -> MaterialTheme.colorScheme.surfaceContainerHighest
        dark -> Color(0xFFF1F0F4)
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    var loading by remember(image.url) { mutableStateOf(true) }
    // Each new place a picture is drawn (the viewer, its slot again) reports loading for a
    // moment even when it comes straight from memory; the indicator waits, so it only shows
    // for a genuinely slow picture rather than flashing on every tap.
    var slow by remember(image.url) { mutableStateOf(false) }
    LaunchedEffect(image.url) {
        kotlinx.coroutines.delay(SlowLoad)
        slow = true
    }
    Box(modifier.clip(shape).background(tile), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = remember(image.url) { ImageRequest.Builder(context).data(image.url).size(CoilSize.ORIGINAL).build() },
            contentDescription = image.caption?.text ?: image.label,
            contentScale = ContentScale.Crop,
            colorFilter = if (invert) ColorFilter.colorMatrix(InvertMatrix) else null,
            onState = { loading = it is AsyncImagePainter.State.Loading },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading && slow) LoadingIndicator(Modifier.size(40.dp))
    }
}

/** How long a picture may take before its loading indicator shows. */
private const val SlowLoad = 300L

/**
 * The open picture, large over a scrim. It flies out of its slot and back into it when closed
 * (tap anywhere or go back): one picture, redrawn at every step between the slot's place on
 * screen and its place here, rounded throughout, with the scrim fading in step. Lives at the
 * root so it covers both panes.
 */
@Composable
fun ImageViewerHost(viewer: ImageViewerState) {
    // Gone entirely once the picture has landed, so nothing sits over the page eating taps.
    val image = viewer.image ?: return
    val open = viewer.key != null
    BackHandler(enabled = open) { viewer.close() }
    val progress = remember { Animatable(0f) }
    var target by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(open) {
        if (open) {
            progress.animateTo(1f, PictureFlight)
        } else {
            progress.animateTo(0f, PictureFlight)
            viewer.landed()
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = null, indication = null, enabled = open) { viewer.close() }
                .testTag("image_viewer_scrim"),
        )
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Where the picture lands; the picture itself is drawn on top, wherever its flight has it.
            Box(
                Modifier
                    .widthIn(max = 720.dp)
                    .heightIn(max = 560.dp)
                    .aspectRatio(image.aspect)
                    .onGloballyPositioned { target = it.boundsInRoot() }
                    .clickable(interactionSource = null, indication = null, enabled = open) { viewer.close() }
                    .testTag("image_viewer"),
            )
            AnimatedVisibility(
                open && image.caption != null,
                enter = fadeIn(tween(200, delayMillis = 120)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(100)),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 14.dp).widthIn(max = 720.dp),
                ) {
                    image.caption?.let {
                        RichTextView(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.layout { measurable, constraints ->
                val r = lerp(viewer.source, target ?: viewer.source, progress.value)
                val placeable = measurable.measure(
                    Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((r.left - origin.x).roundToInt(), (r.top - origin.y).roundToInt())
                }
            },
        ) {
            FlyingImage(image, Modifier.fillMaxSize(), RoundedCornerShape(28.dp))
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
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(context, url.toUri(), mapOf("User-Agent" to USER_AGENT))
            mp.setOnPreparedListener {
                if (player !== mp) return@setOnPreparedListener
                preparing = null
                playing = url
                it.start()
            }
            mp.setOnCompletionListener {
                if (player === mp) stop()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) stop()
                true
            }
            mp.prepareAsync()
        }.onFailure {
            if (player === mp) stop() else mp.release()
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
    // Only an active button spins; idle ones don't keep the frame clock running.
    val rotation = if (playing || preparing) rememberSpin(2400, pausable = false) else null
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
                rotation = { rotation?.value ?: 0f },
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
