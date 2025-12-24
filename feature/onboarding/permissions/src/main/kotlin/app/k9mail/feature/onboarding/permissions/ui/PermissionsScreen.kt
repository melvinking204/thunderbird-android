package app.k9mail.feature.onboarding.permissions.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.k9mail.core.common.provider.BrandNameProvider
import app.k9mail.core.ui.compose.common.mvi.observe
import app.k9mail.feature.onboarding.permissions.ui.PermissionsContract.Effect
import app.k9mail.feature.onboarding.permissions.ui.PermissionsContract.Event
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun PermissionsScreen(
    viewModel: PermissionsContract.ViewModel = koinViewModel<PermissionsViewModel>(),
    brandNameProvider: BrandNameProvider = koinInject(),
    onNext: () -> Unit,
) {
    val context = LocalContext.current

    val contactsPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { success ->
        viewModel.event(Event.ContactsPermissionResult(success))
    }

    val notificationsPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { success ->
        viewModel.event(Event.NotificationsPermissionResult(success))
    }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        viewModel.event(Event.AlarmPermissionResult(success))
    }

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            Effect.RequestContactsPermission -> contactsPermissionLauncher.requestContactsPermission()
            Effect.RequestNotificationsPermission -> notificationsPermissionLauncher.requestNotificationsPermission()
            Effect.RequestAlarmPermission -> alarmPermissionLauncher.requestAlarmPermission(context)
            Effect.NavigateNext -> onNext()
        }
    }

    BackHandler {
        // no back navigation
    }

    LaunchedEffect(key1 = Unit) {
        dispatch(Event.LoadPermissionState)
    }

    PermissionsContent(
        state = state.value,
        onEvent = dispatch,
        brandName = brandNameProvider.brandName,
    )
}

private fun ManagedActivityResultLauncher<String, Boolean>.requestContactsPermission() {
    launch(Manifest.permission.READ_CONTACTS)
}

private fun ManagedActivityResultLauncher<String, Boolean>.requestNotificationsPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>.requestAlarmPermission(
    context: Context,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launch(intent)
    }
}
