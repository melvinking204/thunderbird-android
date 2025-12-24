package app.k9mail.core.ui.compose.designsystem.atom.icon

import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.common.image.ImageWithOverlayCoordinate
import androidx.compose.material.icons.Icons as MaterialIcons

// We're using "by lazy" so not all icons are loaded into memory as soon as the object is accessed. But once a property
// is accessed we want to retain the `ImageWithOverlayCoordinate` instance.
object IconsWithBottomRightOverlay {
    val person: ImageWithOverlayCoordinate by lazy {
        ImageWithOverlayCoordinate(
            image = MaterialIcons.Filled.Person,
            overlayOffsetX = 24.dp,
            overlayOffsetY = 20.dp,
        )
    }

    val notification: ImageWithOverlayCoordinate by lazy {
        ImageWithOverlayCoordinate(
            image = MaterialIcons.Filled.Notifications,
            overlayOffsetX = 23.dp,
            overlayOffsetY = 19.dp,
        )
    }

    val schedule: ImageWithOverlayCoordinate by lazy {
        ImageWithOverlayCoordinate(
            image = MaterialIcons.Filled.Schedule,
            overlayOffsetX = 22.dp,
            overlayOffsetY = 22.dp,
        )
    }
}
