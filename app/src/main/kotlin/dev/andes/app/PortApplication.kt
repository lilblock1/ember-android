package dev.andes.app

import android.app.Application
import dev.andes.authentication.MicrosoftAccount
import dev.andes.authentication.CredentialPrivacy

class PortApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CredentialPrivacy.install()
    }
    val account by lazy { MicrosoftAccount(this) }
}
