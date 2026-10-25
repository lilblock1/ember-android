package dev.andes.authentication

import net.openid.appauth.internal.PrivateLogging

object CredentialPrivacy {
    fun install() = PrivateLogging.install()
}
