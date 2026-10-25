package com.anda.launcher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCustomizationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val key = ComponentKey("com.example", "com.example.Main", 0).flatten()

    @Test
    fun styleAndExceptionsRoundTrip() {
        val settings = LauncherSettings(
            icons = IconSettings(
                shape = IconShapeKind.Custom,
                customShape = CustomShape().withAll(ShapeCorner(CornerStyle.Cut, 0.3f)),
                style = IconEdit(background = IconBackground.Gradient, hue = 40, outlineWidth = 0.05f, shadow = true),
                perApp = mapOf(
                    key to AppIconOverride(
                        source = IconSource.Pack,
                        pack = "com.pack",
                        drawable = "example_alt",
                        themed = false,
                        edit = IconEdit(shape = IconShapeKind.Heart, foreground = IconForeground.Monochrome, foregroundColor = IconColor.fixed(0xFFFF0000)),
                    ),
                ),
            ),
        )
        val decoded = json.decodeFromString<LauncherSettings>(json.encodeToString(settings))
        assertEquals(settings, decoded)
    }

    @Test
    fun settingsFromBeforeTheEditorDecodeToPlainIcons() {
        val old = json.decodeFromString<LauncherSettings>("""{"icons":{"shape":"Circle","iconPack":"com.pack"}}""")
        assertEquals(IconShapeKind.Circle, old.icons.shape)
        assertTrue(old.icons.style.isPlain)
        assertTrue(old.icons.perApp.isEmpty())
    }

    @Test
    fun unknownShapeFromANewerVersionFallsBackToSystem() {
        val decoded = json.decodeFromString<LauncherSettings>("""{"icons":{"shape":"Starburst","themedIcons":true}}""")
        assertEquals(IconShapeKind.System, decoded.icons.shape)
        assertTrue(decoded.icons.themedIcons)
    }

    @Test
    fun sanitizedClampsTheEditor() {
        val edit = LauncherSettings(
            icons = IconSettings(style = IconEdit(scale = 9f, offsetX = Float.NaN, rotation = 720, gradientAngle = -90, padding = 2f, outlineWidth = -1f)),
        ).sanitized().icons.style
        assertEquals(1.6f, edit.scale)
        assertEquals(0f, edit.offsetX)
        assertEquals(180, edit.rotation)
        assertEquals(270, edit.gradientAngle)
        assertEquals(0.25f, edit.padding)
        assertEquals(0f, edit.outlineWidth)
    }

    @Test
    fun sanitizedDropsEmptyAndUnreadableExceptions() {
        val icons = LauncherSettings(
            icons = IconSettings(
                perApp = mapOf(
                    key to AppIconOverride(source = IconSource.App),
                    "not a key" to AppIconOverride(source = IconSource.App),
                    ComponentKey("a", "a.B").flatten() to AppIconOverride(),
                    // A pack exception without a pack follows the default again, so it's empty.
                    ComponentKey("c", "c.D").flatten() to AppIconOverride(source = IconSource.Pack),
                ),
            ),
        ).sanitized().icons
        assertEquals(setOf(key), icons.perApp.keys)
    }

    @Test
    fun exceptionFieldsOnlyCountForTheirSource() {
        val o = AppIconOverride(source = IconSource.App, pack = "com.pack", drawable = "x").sanitized()
        assertNull(o.pack)
        assertNull(o.drawable)
        assertEquals(IconSource.App, o.source)
    }

    @Test
    fun plainIgnoresShapeAndPaletteUseFollowsRoles() {
        assertTrue(IconEdit(shape = IconShapeKind.Heart).isPlain)
        assertFalse(IconEdit(flip = true).isPlain)
        assertFalse(IconEdit().usesPalette)
        assertTrue(IconEdit(background = IconBackground.Solid).usesPalette)
        assertFalse(IconEdit(background = IconBackground.Solid, backgroundColor = IconColor.fixed(0xFF000000)).usesPalette)
        assertTrue(IconEdit(outlineWidth = 0.02f).usesPalette)
    }

    @Test
    fun exceptionLookupUsesTheFlattenedKey() {
        val component = ComponentKey("com.example", "com.example.Main", 0)
        val icons = IconSettings(perApp = mapOf(key to AppIconOverride(themed = true)))
        assertEquals(true, icons.exceptionFor(component)?.themed)
        assertNull(icons.exceptionFor(component.copy(user = 10)))
    }
}
