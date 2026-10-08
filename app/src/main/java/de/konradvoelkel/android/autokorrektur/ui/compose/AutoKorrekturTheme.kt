package de.konradvoelkel.android.autokorrektur.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import de.konradvoelkel.android.autokorrektur.R

/**
 * The Compose half of the app's Material 3 theme, during the migration off Views.
 *
 * It reads the same `values/colors.xml` entries that `values/themes.xml` maps, rather than
 * restating any hex. That is not tidiness: the palette is *derived*, every tone being
 * `oklch(L C 55)` with only L and C moved (BRANDING.md, the header of `colors.xml`), so a second
 * copy of the numbers would be a second place for the derivation to rot. When the palette moves,
 * both themes move with it and neither has to be remembered.
 *
 * Only the primary family is branded here, exactly as `themes.xml` does it — Material 3's baseline
 * derives the rest, which keeps the scheme honest when the hue changes.
 *
 * No dynamic colour on purpose: the brand hue is the product's identity on a screenshot and in the
 * Play listing, and Android 12+ wallpaper extraction would replace it per device.
 */
@Composable
fun AutoKorrekturTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.brand_dark),
            onPrimary = colorResource(R.color.brand_on_dark),
            primaryContainer = colorResource(R.color.brand_container_dark),
            onPrimaryContainer = colorResource(R.color.on_surface_dark),
            secondary = colorResource(R.color.brand_dark),
            onSecondary = colorResource(R.color.brand_on_dark),
            secondaryContainer = colorResource(R.color.brand_container_dark),
            onSecondaryContainer = colorResource(R.color.on_surface_dark),
            surface = colorResource(R.color.surface_dark),
            onSurface = colorResource(R.color.on_surface_dark),
            onSurfaceVariant = colorResource(R.color.on_surface_variant_dark),
            background = colorResource(R.color.surface_dark),
            onBackground = colorResource(R.color.on_surface_dark),
            surfaceContainerLowest = colorResource(R.color.surface_container_lowest_dark),
            surfaceContainerLow = colorResource(R.color.surface_container_low_dark),
            surfaceContainer = colorResource(R.color.surface_container_dark),
            surfaceContainerHigh = colorResource(R.color.surface_container_high_dark),
            surfaceContainerHighest = colorResource(R.color.surface_container_highest_dark),
            surfaceBright = colorResource(R.color.surface_bright_dark),
            surfaceDim = colorResource(R.color.surface_dim_dark),
            outline = colorResource(R.color.outline_dark),
            error = colorResource(R.color.feedback_negative),
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.brand),
            onPrimary = colorResource(R.color.brand_on),
            primaryContainer = colorResource(R.color.brand_container),
            onPrimaryContainer = colorResource(R.color.brand_on_container),
            secondary = colorResource(R.color.brand),
            onSecondary = colorResource(R.color.brand_on),
            secondaryContainer = colorResource(R.color.brand_container),
            onSecondaryContainer = colorResource(R.color.brand_on_container),
            surface = colorResource(R.color.surface_light),
            onSurface = colorResource(R.color.on_surface_light),
            surfaceVariant = colorResource(R.color.surface_variant_light),
            onSurfaceVariant = colorResource(R.color.on_surface_variant_light),
            background = colorResource(R.color.surface_light),
            onBackground = colorResource(R.color.on_surface_light),
            surfaceContainerLowest = colorResource(R.color.surface_container_lowest),
            surfaceContainerLow = colorResource(R.color.surface_container_low),
            surfaceContainer = colorResource(R.color.surface_container),
            surfaceContainerHigh = colorResource(R.color.surface_container_high),
            surfaceContainerHighest = colorResource(R.color.surface_container_highest),
            surfaceBright = colorResource(R.color.surface_bright),
            surfaceDim = colorResource(R.color.surface_dim),
            error = colorResource(R.color.feedback_negative),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
