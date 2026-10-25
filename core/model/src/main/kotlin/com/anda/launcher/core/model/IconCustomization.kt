package com.anda.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * A color used by the icon editor: either a role from the launcher's color scheme (Material You
 * by default, so it follows the wallpaper and the theme) or a fixed color. [role] wins when both
 * are set.
 */
@Serializable
data class IconColor(val role: IconColorRole? = null, val argb: Long? = null) {
    companion object {
        fun of(role: IconColorRole) = IconColor(role = role)
        fun fixed(argb: Long) = IconColor(argb = argb)
    }
}

/** The color-scheme roles an icon can use; resolved from the live theme at render time. */
@Serializable
enum class IconColorRole {
    Primary,
    OnPrimary,
    PrimaryContainer,
    OnPrimaryContainer,
    Secondary,
    SecondaryContainer,
    OnSecondaryContainer,
    Tertiary,
    TertiaryContainer,
    OnTertiaryContainer,
    Surface,
    SurfaceContainerHighest,
    OnSurface,
    InverseSurface,
    InverseOnSurface,
}

/** What fills the icon behind its foreground. */
@Serializable
enum class IconBackground {
    /** The app's own background layer (white for legacy icons, nothing for icon-pack art). */
    Original,
    Solid,
    Gradient,

    /** Foreground only, no backdrop. */
    None,
}

/** How the foreground (the glyph) is colored. */
@Serializable
enum class IconForeground {
    /** The app's own colors, after the hue / saturation / brightness / contrast adjustments. */
    Original,

    /** One flat color: the app's monochrome layer when it has one, else its foreground's silhouette. */
    Monochrome,
}

@Serializable
enum class CornerStyle { Round, Squircle, Cut, Sharp }

/** One corner of a custom shape. [size] is the share of the half side it takes, 0..1. */
@Serializable
data class ShapeCorner(val style: CornerStyle = CornerStyle.Round, val size: Float = 0.5f)

/** A shape built corner by corner (IconShapeKind.Custom). */
@Serializable
data class CustomShape(
    val topLeft: ShapeCorner = ShapeCorner(),
    val topRight: ShapeCorner = ShapeCorner(),
    val bottomRight: ShapeCorner = ShapeCorner(),
    val bottomLeft: ShapeCorner = ShapeCorner(),
) {
    val corners: List<ShapeCorner> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun withAll(corner: ShapeCorner) = CustomShape(corner, corner, corner, corner)
}

/**
 * Everything the icon editor changes. The global style lives in [IconSettings.style]; a per-app
 * exception carries its own copy. [shape] and [customShape] are only used by exceptions (null
 * there means "the global shape"): the global shape stays in [IconSettings.shape].
 *
 * Fractions are of the icon's size, so an edit looks the same at every icon size.
 */
@Serializable
data class IconEdit(
    val shape: IconShapeKind? = null,
    val customShape: CustomShape? = null,
    // Colors
    val background: IconBackground = IconBackground.Original,
    val backgroundColor: IconColor = IconColor.of(IconColorRole.PrimaryContainer),
    val backgroundColor2: IconColor = IconColor.of(IconColorRole.TertiaryContainer),
    val gradientAngle: Int = 135,
    val foreground: IconForeground = IconForeground.Original,
    val foregroundColor: IconColor = IconColor.of(IconColorRole.OnPrimaryContainer),
    // Adjust (Original foreground)
    val hue: Int = 0,
    val saturation: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val invert: Boolean = false,
    // Layout of the foreground inside the shape
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Int = 0,
    val flip: Boolean = false,
    // Effects on the whole icon
    val padding: Float = 0f,
    val outlineWidth: Float = 0f,
    val outlineColor: IconColor = IconColor.of(IconColorRole.Primary),
    val shadow: Boolean = false,
) {
    /** True when the colors pass through untouched (no color matrix needed). */
    val hasColorAdjustments: Boolean
        get() = hue != 0 || saturation != 1f || brightness != 0f || contrast != 1f || invert

    val hasForegroundTransform: Boolean
        get() = scale != 1f || offsetX != 0f || offsetY != 0f || rotation != 0 || flip

    /** Same look as no edit at all (ignoring the shape, which is set separately). */
    val isPlain: Boolean get() = copy(shape = null, customShape = null) == Plain

    /** Whether rendering needs the theme's colors. */
    val usesPalette: Boolean
        get() = (background == IconBackground.Solid && backgroundColor.role != null) ||
            (background == IconBackground.Gradient && (backgroundColor.role != null || backgroundColor2.role != null)) ||
            (foreground == IconForeground.Monochrome && foregroundColor.role != null) ||
            (outlineWidth > 0f && outlineColor.role != null)

    fun sanitized(): IconEdit = copy(
        customShape = customShape?.sanitized(),
        gradientAngle = Math.floorMod(gradientAngle, 360),
        hue = hue.coerceIn(-180, 180),
        saturation = saturation.safe(1f).coerceIn(0f, 2f),
        brightness = brightness.safe(0f).coerceIn(-1f, 1f),
        contrast = contrast.safe(1f).coerceIn(0.5f, 1.5f),
        scale = scale.safe(1f).coerceIn(0.4f, 1.6f),
        offsetX = offsetX.safe(0f).coerceIn(-0.3f, 0.3f),
        offsetY = offsetY.safe(0f).coerceIn(-0.3f, 0.3f),
        rotation = rotation.coerceIn(-180, 180),
        padding = padding.safe(0f).coerceIn(0f, 0.25f),
        outlineWidth = outlineWidth.safe(0f).coerceIn(0f, 0.12f),
    )

    companion object {
        val Plain = IconEdit()
    }
}

/** Where an exception's icon comes from. */
@Serializable
enum class IconSource {
    /** The icon pack chosen for all apps (or the app's own icon when there is none). */
    Default,

    /** The app's own icon, even when an icon pack is set. */
    App,

    /** [AppIconOverride.pack], optionally one specific [AppIconOverride.drawable] from it. */
    Pack,
}

/**
 * A per-app icon exception: most apps follow the icon pack and style, these don't. Every field
 * left at its default follows the global settings.
 */
@Serializable
data class AppIconOverride(
    val source: IconSource = IconSource.Default,
    val pack: String? = null,
    /** A drawable name from [pack]; null uses the pack's own match for the app. */
    val drawable: String? = null,
    /** Themed (Material You monochrome) icon on or off for this app; null follows the setting. */
    val themed: Boolean? = null,
    /** This app's own style; null uses the global style. */
    val edit: IconEdit? = null,
) {
    val isEmpty: Boolean get() = this == AppIconOverride()

    fun sanitized(): AppIconOverride = copy(
        pack = pack?.takeIf { source == IconSource.Pack && it.isNotBlank() },
        drawable = drawable?.takeIf { source == IconSource.Pack && it.isNotBlank() },
        source = if (source == IconSource.Pack && pack.isNullOrBlank()) IconSource.Default else source,
        edit = edit?.sanitized(),
    )
}

internal fun CustomShape.sanitized(): CustomShape {
    fun ShapeCorner.clamp() = copy(size = size.safe(0.5f).coerceIn(0f, 1f))
    return CustomShape(topLeft.clamp(), topRight.clamp(), bottomRight.clamp(), bottomLeft.clamp())
}

/** NaN or infinity (never produced by the UI, but possible in a foreign file) → [fallback]. */
internal fun Float.safe(fallback: Float): Float = if (isFinite()) this else fallback
