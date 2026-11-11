# Archivos de Nuvio Arc modificados por NuvioArcKai

Todo el código nuevo vive en paquetes y archivos nuevos (`core/cloudstorage`, `core/device`, `data/anilist`,
`kai/…`, `CloudStorage*`, `AniList*`, `strings_kai.xml`, `strings_cloud.xml`, `strings_anilist.xml`). Esta lista
recoge cada archivo **original** de Arc que se tocó y por qué, para poder seguir fusionando `upstream`.

| Archivo | Tarea | Motivo |
| --- | --- | --- |
| `app/build.gradle.kts` | Fase 0, B | `applicationId = com.eibyx.nuvioarckai`; versión propia (`1.1.0-nt.stable-kai.1`, código 1); solo `armeabi-v7a`; falla ante cualquier `bundle*` o salida con otro ABI; firma sin valores por defecto (falla sin keystore, nunca firma release con la clave de depuración, se ignora `CI_USE_DEBUG_SIGNING`); actualizador a `Eibyx/NuvioArcKai`; campos `ANILIST_CLIENT_ID` / `ANILIST_REDIRECT_URI`; `testImplementation(kotlin("test"))` para las pruebas portadas de LiteOG (solo pruebas). |
| `app/src/main/res/xml/appfilter.xml`, `app/src/main/assets/appfilter.xml` | Fase 0 | `ComponentInfo{com.eibyx.nuvioarckai/…}`; nombres de actividad sin cambios. |
| `app/src/main/res/values*/strings.xml` | Fase 0, D | `app_name` = «Nuvio Arc Kai»; seis cadenas `skip_segment_*` (en otros idiomas copian las etiquetas existentes de Arc para no perder traducción). |
| `app/src/test/java/com/nuvio/tv/launcher/IconPackTest.kt` | Fase 0 | Comprueba el paquete literal: actualizado a `com.eibyx.nuvioarckai` (requisito de identidad). |
| `app/src/testFull/java/com/nuvio/tv/updater/ApkInstallerValidationTest.kt` | Fase 0 | Datos de paquete del instalador actualizados al paquete de Kai; las aserciones no cambian. |
| `.github/workflows/android-release.yml` | Fase 0 | `if: github.repository == 'ysosrs123/NuvioTV-Fork'`: nunca publica APK de dos ABI en este repositorio. |
| `.gitignore` | Herramientas | Permite versionar `scripts/kai/`. `docs/kai/` se añade con `git add -f` (Arc ignora `docs/`). |
| `scripts/verify_native_apk.py` | Release | Modo `--kai` añadido (ARM 32 bits, mpv, `libnuviothumb`, `libc++_shared`, comparación con la línea base, `--output`). El modo original no cambia. |
| `app/src/main/AndroidManifest.xml` | B | `<service>` privado `AniListRetryService` (JobScheduler puntual). |
| `NuvioApplication.kt` | A | `DeviceMemoryTier.init(this)` antes de la inyección (límites de índice de nube por RAM física). |
| `data/mediaserver/ServerStreams.kt` | A | Fuentes de nube (opcionales) tras las de los servidores, también sin servidores; revisión combinada; logotipo de nube solo si se muestran logotipos de servidor. |
| `data/mediaserver/ServerPlayback.kt` | A | `prepare()` desvía destinos `cloud-…` a `CloudStreams`; `isServerSource()` reconoce el loopback de nube; `isCloudSource()`; `stop()` cierra la sesión loopback; `staticUrl()` nulo para nube. El resto ya devuelve vacío/nulo porque la URL no está en `active`. |
| `data/mediaserver/ServerLogos.kt` | A, C | `terabox` y `gdrive` → logotipos oficiales. |
| `ui/screens/settings/MediaServersSettingsScreen.kt` | A | Cuentas de nube en «Conectados» y filas TeraBox / Google Drive en «Añadir»; superposición QR de configuración. |
| `ui/screens/settings/ContentDiscoverySettingsContent.kt` | A | Página `kai-cloud-account:<id>` con la pantalla de cuenta y vuelta a Servidores multimedia. |
| `ui/screens/settings/SettingsSearchIndex.kt` | A, B | Entradas TeraBox, Google Drive y AniList. |
| `ui/screens/settings/LicensesAttributionsScreen.kt` | C | Avisos de marca de Google Drive, TeraBox y AniList. |
| `ui/screens/settings/TrackingSettingsScreen.kt` | B | Fila AniList tras WeTrakr, su foco y su diálogo (QR + PIN, o navegador de la TV). |
| `data/repository/WatchProgressRepositoryImpl.kt` | B | AniList recibe cada `markAsCompleted`/`markAsCompletedBatch` (con `broadcastTrackingHistory` verdadero o falso). Parámetro opcional al final del constructor. |
| `ui/screens/detail/MetaDetailsViewModel.kt` | B | Marcas importadas de AniList como capa aparte en la ficha; parámetro opcional al final. |
| `data/repository/SkipIntroRepository.kt` | D | `SkipInterval.label` (opcional); respaldo ARM sin clave cuando Simkl no está configurado; `getSkipIntervalsForAnimeId` (anilist/anidb). |
| `ui/screens/player/PlayerChapters.kt` | D | `PlayerChapter.allTitles` (todos los nombres del capítulo); `isSegmentName` usa el vocabulario común. |
| `ui/screens/player/ChapterSkipIntervals.kt` | D | Clasifica con el vocabulario común y todos los nombres; etiqueta op/intro/ed/outro/credits/recap. Los tipos (`intro`/`outro`/`recap`) no cambian. |
| `ui/screens/player/SkipIntroButton.kt` | D | Etiqueta por tipo (`skipLabelRes`). |
| `ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` | D | Salto a fin + 100 ms con `SeekParameters(0, 300 ms)` y seek exacto en mpv; traza `ArcSkipDiag`. |
| `ui/screens/player/PlayerRuntimeControllerObservers.kt` | D | Prefijos `anilist:`/`anidb:`; `tmdb:`/`tvdb:` usan el IMDb de los metadatos; trazas. |
| `ui/screens/player/PlayerRuntimeControllerChapters.kt`, `PlayerRuntimeControllerMetadata.kt`, `PlayerScreen.kt` | D | Solo llamadas a la traza `ArcSkipDiag`. |

## Fase 2 (11-nov-2026)

| Archivo | Motivo |
| --- | --- |
| `core/tracking/TrackingProvider.kt` | `TrackingProviderId.ANILIST("anilist")`. |
| `domain/model/LibraryModels.kt` | `LibrarySourceMode.ANILIST`. |
| `core/tracking/TrackingSources.kt` | `LibrarySourceMode.ANILIST → TrackingProviderId.ANILIST`; AniList aparece como fuente de biblioteca si está conectado; **no** como fuente de progreso. |
| `core/catalog/TrackerListRows.kt`, `domain/model/LibraryListTabLocalization.kt`, `ui/screens/settings/LibraryTransferFlow.kt`, `ui/screens/settings/CalendarSettingsSection.kt` | Rama `ANILIST` en sus `when` (nombre «AniList» y títulos de las cinco pestañas con las cadenas de estado existentes de Arc). |
| `ui/screens/settings/TrackingSettingsScreen.kt` | Rama `ANILIST` en todos los `when`; `openProvider(ANILIST)` abre el diálogo propio de AniList; desconexión con su confirmación y textos propios. |
| `ui/screens/settings/TrackingSettingsViewModel.kt` | AniList entra en el conjunto de proveedores conectados (parámetro opcional al final del constructor). |
| `ui/screens/library/LibraryScreen.kt` | Etiqueta «ANILIST», textos de biblioteca vacía sin conexión y aviso en lugar de abrir una ficha vacía para entradas sin ID resoluble. |
| `data/repository/LibraryGenreFill.kt` | AniList trae sus géneros como Simkl/MDBList/WeTrakr. |
| `data/repository/WatchProgressRepositoryImpl.kt` | `notifyAniList()`: relanza `CancellationException` y solo registra el resto. |

Tratados por `else`/conjuntos sin cambio (comprobado): `core/calendar/CalendarFollowedSourceReader.kt` (el calendario no lee AniList),
`data/repository/LibraryTransferService.kt` (ruta genérica: la transferencia a AniList usa Planeado mediante `applyMembershipChanges`),
`ui/screens/settings/MDBListSettingsScreen.kt` (solo `WatchProgressSource`, que no incluye AniList), y los de `data/wetrakr/*`.
