package com.nuvio.tv.core.logging

import fi.iki.elonen.NanoHTTPD
import java.security.SecureRandom

/**
 * "Ver en el celular": a LAN page with the (redacted) log as plain text, alive only while the log
 * screen shows its QR. Every request needs the random token from the QR.
 */
class AppLogShareServer private constructor(
    private val token: String,
    port: Int,
    private val text: () -> String
) : NanoHTTPD(port) {
    val path: String get() = "/logs/$token"

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET || session.uri != path) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", text()).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Referrer-Policy", "no-referrer")
        }
    }

    companion object {
        fun start(text: () -> String, startPort: Int = 8150, attempts: Int = 10): AppLogShareServer? {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val token = bytes.joinToString("") { "%02x".format(it) }
            for (port in startPort until startPort + attempts) {
                try {
                    return AppLogShareServer(token, port, text).also { it.start(SOCKET_READ_TIMEOUT, false) }
                } catch (_: Exception) {
                }
            }
            return null
        }
    }
}
