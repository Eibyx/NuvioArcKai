# Subtítulos de carpetas con sus fuentes (Kai)

Rama `kai/subtitle-folders`, creada desde `ccr-60b9fe86-mp2dal` (la base de la pre-release `1.1.0-nt.stable-kai.1`):
`main` solo tiene el workflow (PR #1) y no tiene TeraBox, Google Drive ni AniList, de los que depende esta función.

## Decisiones tomadas (la opción más conservadora en cada caso)

| Punto | Decisión |
|---|---|
| Rama base | `ccr-60b9fe86-mp2dal` en vez de `main` (ver arriba). El PR va hacia `main` y no se fusiona. |
| Integración | Los subtítulos de carpeta son `Subtitle` normales (`file://` en la caché, id `kai-lib-…`). Reutilizan el menú, la selección automática diferida (`deferredAutoAddonSubtitle`), `attachAddonSubtitleViaMediaReload`/`toSubtitleConfiguration`, el retardo, AutoSync y `subtitleTimeShiftUs`. No hay ruta paralela. |
| Idioma | Etiqueta en el nombre (`.es.ass`, `[Español]`), luego `Language:` de `[Script Info]`; si no hay ninguna, se toma el idioma preferido del usuario (son sus propias carpetas). |
| Límite de fuentes | 32 MB por subtítulo (no 40) y 25 MB por archivo: durante la recarga del medio, `AssHandler` guarda las copias Java pendientes hasta crear la pista y luego libass las copia en nativo, así que el pico es el doble. |
| Selección de fuentes en la nube | Se descargan primero las que por nombre de archivo parecen usadas y se para cuando todas las familias usadas están cubiertas (y sus negritas/cursivas); máximo 80 MB descargados por carpeta. Las fuentes se guardan en la caché LRU y se reutilizan. |
| Sin fecha de modificación | TeraBox y Drive dan `mtime`; SAF y archivos también. La clave de caché es `hash(raíz + id + tamaño + mtime)`. |
| Índice nombre→archivo | En memoria por proceso (clave = archivo en caché); leer la tabla `name` de un archivo ya en caché solo lee la cabecera. |
| Explorador | Uno propio (mismos componentes de Ajustes) en vez de `CloudFolderBrowser`, que añade carpetas al índice de vídeos de la cuenta; así ese índice no cambia. |
| libass desactivado | El subtítulo se carga igual; el menú muestra «Activa libass para ver las fuentes de este subtítulo» y solo al pulsarlo se cambia el ajuste. |

## Lo que se comprobó en el código (evidencia)

- **`format.id`**: en `lib-exoplayer-release.aar` (Media3 1.8.0 de Nuvio), `DefaultMediaSourceFactory.createMediaSource` crea el
  `Format` del sidecar con `setId(subtitleConfiguration.id)` y llama a `subtitleParserFactory.create(format)` dentro de la
  fábrica de extractores (lambda `lambda$createMediaSource$0`, en el hilo de carga). El id llega igual que lo pone
  `toSubtitleConfiguration` (`nuvio-addon-sub:<id>:<hash>`). Por si un grupo de pistas lo prefija (`1:`), el registro también
  empareja por sufijo (`LibraryFontRegistryTest`).
- **Orden de eventos**: `AssSubtitleParserFactory.create` → `AssHandler.createTrack` → `fontStore.flush` (javap del AAR de libass).
  `setMediaSource` notifica `onMediaItemTransition` (que borra las fuentes) en el hilo de la app antes de `prepare()`, y el
  parser se crea después, al cargar. Las fuentes se añaden en `CompatAssSubtitleParserFactory.create` antes de
  `delegate.create`, así que quedan pendientes y se vuelcan justo al crear la pista. Un `Player.Listener` registrado después de
  `assHandler.init(player)` deja un aviso en el log si una transición llega menos de 2 s después de crear un parser con fuentes
  de carpeta (carrera teórica). No se vuelven a añadir allí: quedarían en memoria sin pista que las use.
- **Fuentes tardías**: con pistas ya creadas (MKV con ASS propio) `addFont` va directo a `Ass.addFont`; libass, al renderizar
  cada fotograma, compara `library->num_fontdata` con las fuentes que conoce y carga las nuevas
  (`ass_update_embedded_fonts`). Es el mismo mecanismo que ya usan los adjuntos MKV. NO VERIFICADO en el televisor.
- **mpv**: `libmpv.so` de `mpv-android-lib 0.1.12` es `mpv v0.41.0-174`. En esa versión cada decodificador de subtítulos crea su
  propia biblioteca libass al iniciarse (`sd_ass` → `mp_ass_init` → `ass_set_fonts_dir`), y libass lee entero cada archivo del
  directorio. Estrategia (a): `sub-fonts-dir` apunta a `noBackupFilesDir/mpv-sub-fonts-active/` con las fuentes incluidas
  (enlaces duros, o copias) más las del subtítulo que se va a añadir; se reescribe (archivo temporal + renombrado) antes de cada
  `sub-add`. El ASS se pasa como ruta local sin reescribir. `sub-ass-override=no` y `sub-font` no cambian.

## Permisos añadidos al manifest

- `READ_EXTERNAL_STORAGE` con `maxSdkVersion="32"`: solo para leer archivos en Android 7–10 cuando el televisor no tiene
  selector de documentos; se pide solo al elegir esa opción. `requestLegacyExternalStorage="true"` para Android 10.
- `MANAGE_EXTERNAL_STORAGE`: en Android 11+ los `.ass`/`.ttf` no son multimedia y el permiso anterior no los alcanza. Solo se
  pide si el usuario elige «Acceso a todos los archivos» y la pantalla de ajustes existe (si no, se muestra el comando adb).
  La app no se distribuye en Google Play, así que la política de Play sobre este permiso no aplica.
- `<queries>` para `OPEN_DOCUMENT_TREE` y `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, necesarios para que `resolveActivity`
  diga la verdad en Android 11+.

## Licencias

Todo el código es nuevo (GPL-3.0 como Arc). No se copió nada de `SubtitleFontDirectory.kt` (AGPL-3.0) de NuvioMobileKai ni de
NuvioTvLiteOG.

## Lista de comprobación para el TCL (NO VERIFICADO)

1. ExoPlayer + libass: un episodio con su `.ass` y dos fuentes en la carpeta; las fuentes se ven (no la predeterminada).
2. MPV: lo mismo; cambiar a otro subtítulo de carpeta y volver; las fuentes siguen bien.
3. TeraBox: carpeta raíz `Subtítulos` elegida desde Ajustes; el episodio aparece como «Español · TeraBox · N fuentes».
4. Google Drive: igual, «Español · Drive».
5. USB: selector de documentos (o permiso / acceso a todos los archivos, según el televisor); desconectar el USB y abrir un
   episodio → «USB no conectado» en el menú, sin errores.
6. «Enviar desde el celular»: escanear el QR, elegir la carpeta, ver «Archivos recibidos: N»; el episodio la usa.
7. Fuente que falta: quitar una fuente usada → aviso «Faltan fuentes: …» una sola vez.
8. libass desactivado: el subtítulo se carga; el menú ofrece activar libass.
9. Cambio de episodio (siguiente episodio y elegir otro): el subtítulo y las fuentes son las del nuevo episodio.
10. Memoria: episodio con 30 MB de fuentes en un TCL de 1 GB; reproducir 20 min sin cierres.
11. «Buscar en mis carpetas…» y «Vincular carpeta…»: elegir a mano, salir y volver; se recuerda.

## Pre-release publicada

https://github.com/Eibyx/NuvioArcKai/releases/tag/1.1.0-nt.stable-kai.2 — ejecución
https://github.com/Eibyx/NuvioArcKai/actions/runs/43063720015 (commit `baf7c18`).

| Comprobación (APK descargado de la release) | Resultado |
|---|---|
| SHA-256 del APK | `347a457c092d2e10b3524eb5cb3470204c8508386ed335a976e567dd8cc40702` (coincide con `SHA256SUMS.txt`) |
| Firma | v2 válida, el mismo certificado que kai.1: `b5f906aecd445cf51d6abfcc1276b40eb2a2c29739efeacc1d73fc88bd940feb` |
| aapt2 | `com.eibyx.nuvioarckai`, versionCode 2, versionName `1.1.0-nt.stable-kai.2`, native-code `armeabi-v7a` |
| Bibliotecas | solo `lib/armeabi-v7a/`; `libasskt.so` y `libmpv.so` presentes |
| Release | pre-release, tres assets, `<!-- nuvio-fork-version-code: 2 -->` |

Ejecuciones previas: `43044293410` falló por `SettingsSearchTest` (filas nuevas sin indexar; corregido en `2d59f2e` sin tocar la
prueba); `43052003525` falló dos veces porque el runner no resolvía `security.ubuntu.com` al instalar Chromium con apt
(corregido en `baf7c18`: Chromium sin apt salvo que el render lo necesite). `HomeEnrichmentRetryTest` es inestable (carrera
en la propia prueba) y no falló en la ejecución publicada.

## kai.3: correcciones tras la prueba en la TV

| Problema | Causa encontrada | Cambio |
|---|---|---|
| Se congela al cargar el subtítulo después de empezar | Algunas selecciones automáticas (idioma principal, forzados, respaldo de addon) llamaban a `selectAddonSubtitle` directamente; con ASS + libass eso recarga el medio en plena reproducción | Todas pasan por `autoPickAddonSubtitle` → `autoSelectAddonSubtitleDeferringReload`; una recarga durante la reproducción (o un rebuffer tras la primera imagen) espera a la siguiente pausa |
| El subtítulo de la carpeta tiene que llegar antes | La búsqueda empezaba en el reproductor | `SubtitleLibraryService.prefetch` desde `StreamScreenViewModel.loadStreams` (como `SubtitleLibraryPrefetch` de NuvioMobileKai); el reproductor toma ese resultado y lo espera hasta 8 s para meterlo en el primer `MediaItem` |
| El subtítulo seleccionado se cambia/apaga solo | AutoSync puede sustituir el subtítulo elegido por otro «mejor sincronizado» | AutoSync no actúa sobre subtítulos de carpeta; cada cambio de selección queda en el diagnóstico con su causa (usuario / automático) |
| «El decodificador no responde» a los ~20 s | `StartupWatchdogDecision` disparaba si el búfer no crecía en 20 s | `patientStart`: mientras no esté READY espera hasta 80 s (techo de servidores lentos); READY sin imagen sigue avisando |
| Pasa a MPV sin explicación | Con el motor en Automático, cualquier anime va a MPV (`resolveAutoInternalPlayerEngine`) | Motivo guardado y mostrado (aviso breve + Información del stream + diagnóstico); también cambios manuales y tras error |
| Aviso de fuentes que faltan | — | Eliminado; solo diagnóstico/log |
