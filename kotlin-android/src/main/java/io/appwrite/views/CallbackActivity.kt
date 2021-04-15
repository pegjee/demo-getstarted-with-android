package io.appwrite.views

import android.app.Activity
import android.os.Bundle
import io.appwrite.WebAuthComponent

class CallbackActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.data
        val scheme = url?.scheme
        if (scheme != null) {
            WebAuthComponent.onCallback(scheme, url)
        }
        finish()
    }
}