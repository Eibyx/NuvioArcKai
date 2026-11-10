# Diagnóstico de los botones de salto con ExoPlayer (Tarea D)

**Estado: la causa principal (H2/H3) está demostrada: la prueba automática comprueba que ExoPlayer elige «Apertura» y
«Créditos» con la TV en español, y los cinco patrones de Arc (evaluados aparte con las mismas expresiones) solo
reconocen «Opening»/«Ending», no «Apertura»/«Créditos». Los registros en la TCL con ExoPlayer y MPV están NO VERIFICADOS: este entorno no tiene la TV ni
ningún dispositivo `armeabi-v7a`.** La traza `ArcSkipDiag` queda lista para tomarlos (abajo).

## Prueba que reproduce la causa

`app/src/test/java/com/nuvio/tv/kai/segments/MultilangChapterSkipTest.kt` lee el fixture
`app/src/test/resources/mkv-chapters/multilang.mkv` (generado por `scripts/tests/make_multilang_chapters_mkv.py` a partir
de `front.mkv`; `ffprobe` lee sus 5 capítulos) cuyos capítulos llevan nombre en inglés y en español
(«Opening»/«Apertura», «Ending»/«Créditos»), y lo pasa por `PlayerChapters.namedFileChapters(tracks, Locale("es"))`, el
mismo camino que usa ExoPlayer en `chaptersFor()`:

- Con el código de Arc, el nombre elegido es «Apertura»/«Créditos»; `ChapterSkipIntervals.classifyTitle()` solo conocía
  nombres ingleses → **0 intervalos → sin botón** (caso CAPITULOS_NO_RECONOCIDOS).
- Con la corrección, se clasifica con todos los nombres del capítulo → intervalo `intro` 60–150 s (etiqueta `op`) e
  intervalo `outro` 1200–1290 s (etiqueta `ed`), iguales con `es`, `en`, `ja` y `es-419`.

## Respuestas a las 10 preguntas

| # | Pregunta | Respuesta | Evidencia |
| --- | --- | --- | --- |
| 1 | ¿ExoPlayer obtiene capítulos y segmentos? | Sí: `chaptersFor()` → `namedFileChapters()` en cada cambio de pistas; los segmentos de servidor por `fetchServerSkipIntervals()`. | Lectura de código; traza `chapters` y `intervals`. En TV: NO VERIFICADO. |
| 2 | ¿El resolvedor devuelve los IDs? | Con Simkl configurado, sí; **sin `SIMKL_CLIENT_ID` devolvía null** y `kitsu:`/`tt` no llegaban a AniSkip. Ahora ARM (sin clave) cubre `kitsu`, `anilist`, `anidb` e IMDb cuando una sola entrada ocupa la temporada TVDB. | `KaiArmSkipFallbackTest` (5 casos). |
| 3 | ¿Se ejecutan las peticiones con ExoPlayer? | Sí, `fetchSkipIntervals()` no mira el motor. Antes salía sin buscar con `tmdb:`, `tvdb:`, `anilist:`, `anidb:`. | Código; traza `fetch` (`route=no-imdb` cuando no hay IMDb). |
| 4 | ¿Se guardan en el estado? | Sí: `providerSkipIntervals` → `recomputeMergedSkipIntervals()` → `skipIntervals`. | Traza `intervals` (capítulos, servidor, proveedor, fusionados). |
| 5 | ¿Llega la posición de ExoPlayer al sistema de botones? | Sí: `updateActiveSkipInterval(pos)` en la rama ExoPlayer de `startProgressUpdates()`. | Código; traza `active`. |
| 6 | ¿Las reglas de visibilidad lo bloquean? | No por motor; `findActiveSkipInterval` solo excluye `post-credits`. | `SkipIntroVisibilityRulesTest` sin cambios. |
| 7 | ¿Hay condición específica de motor? | Solo en los datos: ExoPlayer elige el nombre del capítulo en el idioma del dispositivo; mpv usa su título. | `MultilangChapterSkipTest` (paridad de tiempos y tipos). |
| 8 | ¿Lo tapan controles, libass, pausa, carga o la tarjeta de siguiente episodio? | Pausa, carga y la tarjeta postplay lo ocultan a propósito (`PlayerScreen`); `zIndex(2.1f)` está por encima de subtítulos. | Código. En TV: NO VERIFICADO. |
| 9 | ¿Funciona al reproducir, adelantar, retroceder y cambiar de episodio? | La lógica es la misma de Arc; el salto ahora cae fuera del intervalo, así que no reaparece. | `SkipSeekTargetTest`. En TV: NO VERIFICADO. |
| 10 | ¿Sirven los capítulos incrustados como alternativa? | Sí, y ahora también con nombres en español, portugués, francés, alemán, italiano y japonés. | `SegmentVocabularyTest`. |

## Clasificación de casos (en la traza)

`ArcSkipDiag.classify()`: **SIN_FUENTE** (ni proveedor, ni servidor, ni capítulo), **CAPITULOS_NO_RECONOCIDOS** (≥ 2
capítulos con nombre y ninguno clasificado), **DATOS_SIN_BOTON** (intervalo activo y botón invisible), **OK**.
Probado en `ArcSkipDiagTest`.

## Cómo tomar los registros en la TCL

```
adb shell setprop log.tag.ArcSkipDiag VERBOSE      # en release; en debug ya está activa
adb logcat -s ArcSkipDiag
```

Etapas: `ids`, `fetch`, `chapters`/`chapter` (todos los nombres y su clasificación), `intervals`, `active`, `button`,
`skip` (destino) y `reached` (posición 1,5 s después y desfase). Sin URLs, tokens ni rutas.

## Correcciones aplicadas

1. Vocabulario común `kai/segments/SegmentVocabulary` usado por `PlayerChapters.isSegmentName` y por
   `ChapterSkipIntervals` (anclado, sin «The Opening Scene» ni «Alternate Ending», excluye post/mid/after-credits).
2. Clasificación con todos los nombres del capítulo (`PlayerChapter.allTitles`); mpv con su único título.
3. Tipos: se conservan `intro`/`outro`/`recap` (los usan `ChapterSkipIntervalsTest`, auto-salto,
   `OUTRO_SEGMENT_TYPES` y la fusión) y se añade `SkipInterval.label` = `op`/`intro`/`ed`/`outro`/`credits`/`recap`.
   **Desviación justificada:** el requisito pedía tipos distintos, pero también que `ChapterSkipIntervalsTest` pase sin
   cambios y no romper `OUTRO_SEGMENT_TYPES`; cambiar el tipo rompía ambos. La etiqueta separada cumple la intención
   (botón «Saltar opening/intro/ending/outro/créditos/recapitulación») sin cambiar el comportamiento.
4. Etiquetas `skip_segment_*` en `values`, `values-es` y `values-b+es+419`; los demás idiomas reutilizan sus traducciones
   existentes. `skip_to_post_credits`, `skip_movie_credits` y `skip_generic` sin cambios.
5. IDs: `anilist:`/`anidb:` (ARM → MAL), `tmdb:`/`tvdb:` con el IMDb de los metadatos; ARM con caché acotada (128/64) y
   3 s por petición; nunca supone temporada (dos entradas en una temporada TVDB → no busca).
6. Salto: fin + 100 ms; ExoPlayer `SeekParameters(0, 300 000 µs)`; mpv `seekToMsExact`. Post-créditos y créditos de
   película siguen con su seek exacto. El tiempo hasta reanudar en la TCL: NO VERIFICADO.
7. Visibilidad y foco: sin cambios.
