# AniList en NuvioArcKai

## Inicio de sesión

Orden en el diálogo de Ajustes → Seguimiento → AniList:

1. **Conectar con el móvil (QR)** — principal. Usa el cliente propio de Kai (`ANILIST_CLIENT_ID`, público, numérico,
   registrado con Redirect URL `https://anilist.co/api/v2/oauth/pin`). La TV abre un servidor NanoHTTPD temporal en
   `18767` sobre su IPv4 privada y muestra un QR a `http://<ip-tv>:18767/anilist/<nonce aleatorio de 128 bits>` y un
   código de 6 cifras. La página del móvil enlaza a `https://anilist.co/api/v2/oauth/authorize?client_id=<id>&response_type=token`;
   AniList muestra el token; el usuario lo pega con el código; la TV valida con `Viewer` y lo guarda cifrado (AES-GCM con
   Android Keystore, por perfil). Sin `ANILIST_CLIENT_ID` la opción se oculta y el diálogo lo explica. Nunca se usa
   `53329` con la redirección PIN.
2. **Conectar en esta TV** — WebView con el cliente `53329` de NuvioMobileKai y `nuvio://auth/anilist` (LiteOG).
3. **Relevo HTTPS** — solo si `ANILIST_REDIRECT_URI` es una URL HTTPS propia (no la de PIN) con su cliente: el flujo cifrado
   de LiteOG (`AniListPhonePairing`, `docs/anilist-oauth/callback.html` de LiteOG).

## Seguridad del flujo PIN

El token viaja del móvil a la TV por HTTP en la LAN. Medidas: ruta aleatoria; código de un solo uso comparado en tiempo
constante; caducidad de 10 min (en el repositorio y en la propia página); 5 códigos erróneos cierran la página; la página
se cierra al recibir un token; solo acepta clientes IPv4 privados (`isSiteLocalAddress`); cuerpo ≤ 20 KB; cabeceras
`no-store`, `no-referrer`, `nosniff`, `DENY` y CSP sin scripts; nada se registra; el token nunca se devuelve en la
respuesta. **Decisión: la página no cifra el token.** `crypto.subtle` no existe en páginas `http://` de LAN (no es un
contexto seguro) y Kai no incluye una librería JavaScript de cifrado auditada; incrustar una sin auditar no mejora la
seguridad de forma demostrable. Riesgo residual: alguien que capture el tráfico de la misma red Wi-Fi durante esos minutos.

Los tokens de Implicit Grant duran un año y no tienen refresh: un 401 deja la cuenta pendiente de reconectar sin borrar
la cola. Borrar un perfil borra su sesión, cola y snapshot (`AniListProfileCleaner`).

## Sincronización

Motor de LiteOG sin cambios: `markAsCompleted` y `markAsCompletedBatch` (con `broadcastTrackingHistory` verdadero o
falso) encolan en disco antes de la red; lectura fresca y solo aumentos (`aniListProgressUpdate`); nunca reescribe
COMPLETED ni genera REPEATING; clave de cola por perfil/temporada/parte; reintentos con `AniListRetryService`
(JobScheduler puntual con red, 30 s → 6 h, `Retry-After`). AniList **no** se registra como `TrackingHistoryWriter`,
así que no hay doble envío.
