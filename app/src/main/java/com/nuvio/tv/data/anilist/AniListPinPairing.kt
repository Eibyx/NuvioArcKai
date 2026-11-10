package com.nuvio.tv.data.anilist

import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * NuvioArcKai: AniList sign-in from the phone with AniList's official Auth Pin flow.
 *
 * The TV's own client (registered with Redirect URL `https://anilist.co/api/v2/oauth/pin`) cannot redirect back to
 * the TV, so AniList shows the token on its own page. The phone opens a short-lived page on the TV
 * (`http://<tv>:18767/anilist/<nonce>`), follows its button to AniList, copies the token there and pastes it back
 * together with the one-time code the TV shows. The TV checks the token with `Viewer` before keeping it.
 *
 * Protection, as for the cloud setup page: random path, one-time 6-digit code, 10 minutes, 5 wrong codes close it,
 * private IPv4 callers only, nothing logged, the token is never echoed and the server stops once a token is taken.
 * The token travels over the LAN in plain HTTP: `crypto.subtle` is not available on `http://` LAN pages and no audited
 * JavaScript crypto is bundled, so the page does not encrypt it (see docs/kai/anilist.md).
 */
internal class AniListPinPairing(
    clientId: String,
    private val onToken: (String) -> Unit,
    random: SecureRandom = SecureRandom(),
    now: () -> Long = System::currentTimeMillis
) {
    private val address: String = privateIpv4() ?: error("TV needs a local IPv4 network")
    private val nonce = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    private val path = "/anilist/$nonce"
    val gate = AniListPinGate(code = (0 until 6).joinToString("") { random.nextInt(10).toString() }, now = now)
    val code: String get() = gate.code
    val pageUrl: String = "http://$address:$PORT$path"
    val authorizeUrl: String = AniListConfig.pinAuthorizeUrl(clientId)

    private val server = object : NanoHTTPD(address, PORT) {
        override fun serve(session: IHTTPSession): Response {
            fun answer(status: Response.Status, type: String, body: String) = newFixedLengthResponse(status, type, body).also {
                it.addHeader("Cache-Control", "no-store")
                it.addHeader("Referrer-Policy", "no-referrer")
                it.addHeader("X-Content-Type-Options", "nosniff")
                it.addHeader("X-Frame-Options", "DENY")
                it.addHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'")
            }
            fun html(status: Response.Status, body: String) = answer(status, "text/html; charset=utf-8", body)
            if (!isPrivateCaller(session.remoteIpAddress)) return html(Response.Status.FORBIDDEN, AniListPinPage.message("Solo desde la red local."))
            if (session.uri != path) return html(Response.Status.NOT_FOUND, AniListPinPage.message("Página no encontrada."))
            return when (session.method) {
                Method.GET -> if (gate.isOpen()) html(Response.Status.OK, AniListPinPage.form(authorizeUrl, null))
                    else html(Response.Status.GONE, AniListPinPage.message("Esta página ha caducado. Inicia la conexión de nuevo en la TV."))
                Method.POST -> {
                    val length = session.headers["content-length"]?.toLongOrNull()
                    if (length == null || length !in 1..MAX_BODY) return html(Response.Status.BAD_REQUEST, AniListPinPage.message("Solicitud no válida."))
                    val parameters = runCatching { session.parseBody(hashMapOf()); session.parameters }.getOrNull()
                        ?: return html(Response.Status.BAD_REQUEST, AniListPinPage.message("Solicitud no válida."))
                    val result = gate.submit(parameters["code"]?.firstOrNull(), parameters["token"]?.firstOrNull())
                    when (result) {
                        is AniListPinGate.Result.Accepted -> {
                            onToken(result.token)
                            html(Response.Status.OK, AniListPinPage.message("Autorización recibida. Comprueba la TV; ya puedes cerrar esta pestaña."))
                        }
                        AniListPinGate.Result.WrongCode -> html(Response.Status.FORBIDDEN, AniListPinPage.form(authorizeUrl, "Código incorrecto. Te quedan ${gate.attemptsLeft()} intentos."))
                        AniListPinGate.Result.InvalidToken -> html(Response.Status.BAD_REQUEST, AniListPinPage.form(authorizeUrl, "Eso no parece un token de AniList. Copia el texto completo que muestra AniList."))
                        AniListPinGate.Result.Closed -> html(Response.Status.GONE, AniListPinPage.message("Esta página está cerrada. Inicia la conexión de nuevo en la TV."))
                    }
                }
                else -> html(Response.Status.METHOD_NOT_ALLOWED, AniListPinPage.message("Solicitud no válida."))
            }
        }
    }

    fun start() = server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
    fun stop() {
        gate.close()
        server.stop()
    }

    companion object {
        const val PORT = 18767
        private const val MAX_BODY = 20_000L

        private fun privateIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        internal fun isPrivateCaller(ip: String?): Boolean {
            if (ip.isNullOrBlank() || ':' in ip) return false
            val parsed = runCatching { InetAddress.getByName(ip) }.getOrNull() as? Inet4Address ?: return false
            return parsed.isSiteLocalAddress
        }
    }
}

/** Code, attempts and expiry of one pairing; pure so it can be tested without sockets. */
internal class AniListPinGate(
    val code: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val lifetimeMs: Long = 10 * 60_000L,
    private val maxWrongCodes: Int = 5
) {
    sealed interface Result {
        data class Accepted(val token: String) : Result
        data object WrongCode : Result
        data object InvalidToken : Result
        data object Closed : Result
    }

    private val startedAt = now()
    private var wrong = 0
    private var closed = false

    @Synchronized fun isOpen(): Boolean = !closed && now() - startedAt < lifetimeMs && wrong < maxWrongCodes
    @Synchronized fun attemptsLeft(): Int = (maxWrongCodes - wrong).coerceAtLeast(0)
    @Synchronized fun close() { closed = true }

    @Synchronized fun submit(submittedCode: String?, token: String?): Result {
        if (!isOpen()) return Result.Closed
        val cleanCode = submittedCode?.filter(Char::isDigit).orEmpty()
        if (!constantTimeEquals(cleanCode, code)) {
            wrong++
            if (wrong >= maxWrongCodes) closed = true
            return if (closed) Result.Closed else Result.WrongCode
        }
        val cleanToken = token?.trim().orEmpty()
        if (!looksLikeToken(cleanToken)) return Result.InvalidToken
        // One use only: the page closes as soon as a token is handed over.
        closed = true
        return Result.Accepted(cleanToken)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        /** Same bounds as Mobile's callback parser: a JWT-like string without spaces or control characters. */
        fun looksLikeToken(token: String): Boolean =
            token.length in 16..16_384 && token.none { it.isWhitespace() || it.isISOControl() } && token.count { it == '.' } >= 2
    }
}

/** The phone page: plain HTML, no scripts, Spanish text. */
internal object AniListPinPage {
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun shell(body: String) = """<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>AniList · Nuvio Arc Kai</title>
<style>body{font-family:system-ui,sans-serif;background:#1f2631;color:#e9eef5;margin:0;padding:24px;max-width:560px}
a.button,button{display:block;width:100%;box-sizing:border-box;text-align:center;padding:14px;border-radius:10px;border:0;
background:#02a9ff;color:#0b1622;font-weight:700;font-size:17px;text-decoration:none;margin:12px 0}
input,textarea{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;border:1px solid #445;background:#141a22;color:#fff;font-size:16px}
ol{padding-left:20px;line-height:1.5}.note{color:#9fb0c3;font-size:14px}.error{color:#ffb4a9}</style></head><body>$body</body></html>"""

    fun form(authorizeUrl: String, error: String?): String = shell(
        """<h1>Conectar AniList</h1>
<ol><li>Pulsa «Abrir AniList», inicia sesión y autoriza la aplicación.</li>
<li>AniList mostrará un token largo. Cópialo entero.</li>
<li>Vuelve a esta página, escribe el código que ves en la TV y pega el token.</li></ol>
<a class="button" href="${escape(authorizeUrl)}" target="_blank" rel="noopener noreferrer">Abrir AniList</a>
${error?.let { "<p class=\"error\">${escape(it)}</p>" }.orEmpty()}
<form method="post" autocomplete="off"><p><label>Código de la TV<br><input name="code" inputmode="numeric" maxlength="6" required></label></p>
<p><label>Token de AniList<br><textarea name="token" rows="6" required spellcheck="false"></textarea></label></p>
<button type="submit">Enviar a la TV</button></form>
<p class="note">La página caduca a los 10 minutos y se cierra tras 5 códigos incorrectos o al recibir el token. El token solo se guarda en la TV, cifrado.</p>"""
    )

    fun message(text: String): String = shell("<h1>AniList</h1><p>${escape(text)}</p>")
}

internal fun AniListConfig.pinAuthorizeUrl(clientId: String): String =
    "$AUTHORIZE_URL?client_id=${URLEncoder.encode(clientId, "UTF-8")}&response_type=token"
