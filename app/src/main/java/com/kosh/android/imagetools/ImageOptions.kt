package com.kosh.android.imagetools

enum class Corner { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT, CENTER, FREE }

const val WHITE = 0xFFFFFFFF.toInt()
const val BLACK = 0xFF000000.toInt()
const val TRANSPARENT = 0

/** Colours offered in the colour pickers. */
val SWATCHES: List<Int> = listOf(
    WHITE, BLACK, 0xFF9E9E9E.toInt(), 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(),
    0xFF43A047.toInt(), 0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(),
)

/** Every setting of every image tool; each tool reads only its own fields. Pixel sizes are for the full-size picture. */
data class ImageOptions(
    val format: OutFormat = OutFormat.PNG,
    val quality: Int = 85,
    val customName: String = "",
    // compress
    val compressByTarget: Boolean = false,
    val targetValue: String = "500",
    val targetMegabytes: Boolean = false,
    val exactSize: Boolean = false,
    // resize
    val resizeMode: ResizeMode = ResizeMode.EXACT,
    val width: String = "",
    val height: String = "",
    val percent: Int = 50,
    val lockAspect: Boolean = true,
    val widthLed: Boolean = true,
    val letterbox: Boolean = false,
    val letterboxColor: Int = WHITE,
    // crop
    val cropAspect: CropAspect = CropAspect.FREE,
    val crop: CropBox = CropBox.FULL,
    // rotate / flip
    val rotation: Int = 0,
    val mirror: Mirror = Mirror.NONE,
    // colour
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val hue: Int = 0,
    val exposure: Int = 0,
    // filters
    val effect: FilterEffect = FilterEffect.NONE,
    val filterBrightness: Int = 0,
    val filterContrast: Int = 0,
    // borders and padding
    val padAll: Boolean = true,
    val padding: Int = 40,
    val padTop: Int = 40,
    val padRight: Int = 40,
    val padBottom: Int = 40,
    val padLeft: Int = 40,
    val gradient: Boolean = false,
    val fillColor: Int = WHITE,
    val fillColor2: Int = BLACK,
    /** 0 = top to bottom, 1 = left to right, 2 = diagonal */
    val gradientDirection: Int = 0,
    val outerRadius: Int = 0,
    // rounded corners
    val radius: Int = 60,
    val fullRound: Boolean = false,
    // watermark
    val watermarkText: String = "",
    val watermarkPosition: Corner = Corner.BOTTOM_RIGHT,
    val watermarkSize: Int = 48,
    val watermarkOpacity: Int = 60,
    // stamp
    val stampPosition: Corner = Corner.BOTTOM_RIGHT,
    val stampX: Float = 0.5f,
    val stampY: Float = 0.5f,
    val stampSize: Int = 25,
    val stampOpacity: Int = 100,
    val stampRotation: Int = 0,
    val stampMargin: Int = 3,
    // social presets
    val socialSelected: Set<String> = setOf(SOCIAL_PRESETS.first().id),
    val socialFit: SocialFit = SocialFit.FILL,
    // palette
    val paletteCount: Int = 8,
    // collage
    val collageAspect: CollageAspect = CollageAspect.FOUR_THREE,
    val collageWidth: Int = 1600,
    val collageGap: Int = 16,
    val collageRadius: Int = 0,
    val collageBackground: Int = WHITE,
    val collageLayout: String = "",
    val collageFill: Boolean = true,
    // increase size
    val increaseValue: String = "500",
    val increaseMegabytes: Boolean = false,
    val increaseFill: String = "0",
    // strip metadata
    val stripKinds: Set<MetaKind> = emptySet(),
    val stripReencode: Boolean = false,
)

/** The size typed in the "target size" box in bytes, or null when it isn't a usable number. */
fun ImageOptions.targetBytes(): Long? = targetValue.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { sizeToBytes(it, targetMegabytes) }

/** The extra size to add in bytes, or null when it is outside 1 KB..50 MB. */
fun ImageOptions.increaseBytes(): Long? =
    increaseValue.trim().replace(',', '.').toDoubleOrNull()?.let { sizeToBytes(it, increaseMegabytes) }
        ?.takeIf { it in INCREASE_MIN_BYTES..INCREASE_MAX_BYTES }
