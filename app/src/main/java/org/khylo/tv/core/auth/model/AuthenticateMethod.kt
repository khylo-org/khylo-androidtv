package org.khylo.tv.core.auth.model

sealed class AuthenticateMethod
data class AutomaticAuthenticateMethod(val user: User) : AuthenticateMethod()
data class CredentialAuthenticateMethod(val username: String, val password: String = "") : AuthenticateMethod()
data class QuickConnectAuthenticateMethod(val secret: String) : AuthenticateMethod()

/** A sign-in the server already issued, e.g. switching to a Khylo home profile. */
data class AuthenticationResultMethod(val result: org.khylo.sdk.model.api.AuthenticationResult) : AuthenticateMethod()
