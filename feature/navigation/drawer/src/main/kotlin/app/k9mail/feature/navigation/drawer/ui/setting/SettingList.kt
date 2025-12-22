package app.k9mail.feature.navigation.drawer.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.k9mail.core.ui.compose.designsystem.atom.icon.Icons
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.feature.navigation.drawer.R

@Composable
internal fun SettingList(
    onManageFoldersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(vertical = MainTheme.spacings.default)
            .fillMaxWidth(),
    ) {
        SettingListItem(
            label = stringResource(R.string.navigation_drawer_action_manage_folders),
            onClick = onManageFoldersClick,
            imageVector = Icons.Outlined.FolderManaged,
        )
    }
}
