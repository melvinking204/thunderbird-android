package com.fsck.k9.activity.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class PickEmailGroupContract : ActivityResultContract<Unit?, List<String>?>() {
    override fun createIntent(context: Context, input: Unit?): Intent {
        // This Intent will find the app that can handle picking an email group
        return Intent(ACTION_PICK_EMAIL_GROUP)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String>? {
        // This function parses the list of emails returned from the contacts app
        if (resultCode != Activity.RESULT_OK) {
            return null
        }
        return intent?.getStringArrayListExtra(EXTRA_EMAIL_ADDRESSES)
    }

    companion object {
        // These constants must match the ones in your Contacts app
        const val ACTION_PICK_EMAIL_GROUP = "com.starlight.mycontactsapp.action.PICK_EMAIL_GROUP"
        const val EXTRA_EMAIL_ADDRESSES = "com.starlight.mycontactsapp.extra.EMAIL_ADDRESSES"
    }
}
