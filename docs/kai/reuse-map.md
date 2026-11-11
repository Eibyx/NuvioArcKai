# Mapa de reutilización

## De NuvioTvLiteOG (`release/nuviotvog-v0.6.4-source`, `c94d7b9`)

| Origen | Destino | Adaptación |
| --- | --- | --- |
| `core/cloudstorage/*.kt` (17) | mismo paquete | `CONNECTION_PREFIX` `cloud:` → `cloud-` (Arc corta `ServerItemRef` en el primer `:`); `ServerStreamSource` sin `limited` y con `matchedItems`; `catalogIds()` llevado al paquete (Arc no lo tiene); logotipo oficial en `addonLogo`; etiqueta «Google Drive» completa (guía de marca de Google) en lugar de «Drive». |
| `core/device/DeviceMemoryTier.kt` | mismo | Sin `AppFeaturePolicy.liteMode` (Arc no tiene modo lite). |
| `core/di/CloudStorageModule.kt` | mismo | Sin cambios. |
| `ui/screens/settings/CloudStorageSettingsScreen.kt`, `CloudStorageViewModel.kt` | mismo | Logotipos oficiales; `CloudSetupOverlay`, `CloudErrorToast` y `statusText` reutilizados desde Servidores multimedia. |
| `res/values*/strings_cloud.xml` | mismo | Sin colisiones con Arc (comprobado). |
| pruebas `core/cloudstorage` (12 + `FakeCloudHttp`) | mismo | `CloudSourceLabelTest`: «Drive» → «Google Drive» por la guía de marca. |
| `data/anilist/*.kt` (19) | mismo | `AniListConfig`: cliente propio (PIN) separado del 53329 de Mobile (WebView) y del relevo HTTPS opcional. `AniListRepository.connectWithPin()`. |
| `ui/screens/settings/AniList*.kt` (3) | mismo | Diálogo con logotipo, QR a la página PIN, código y botones «Conectar con el móvil» / «Conectar en esta TV». |
| `res/values*/strings_anilist.xml` | mismo + `values-b+es+419` | LiteOG no tenía `b+es+419`; se copió el español. |
| pruebas `data/anilist` (10 + utilidades) | mismo | Sin cambios. |
| Gancho en `WatchProgressRepositoryImpl` y capa en `MetaDetailsViewModel` | Arc | Igual que LiteOG. |

## Nuevo en Kai

`kai/segments/SegmentVocabulary` (vocabulario común), `SkipSeekTarget`, `ArcSkipDiag`; `kai/arm/ArmIdLookup` (ARM sin clave);
`data/anilist/AniListPinPairing` (Auth Pin + QR); `scripts/kai/*`; fixture `multilang.mkv` y su generador.

## De NuvioMobileKai (`kai`, `0be65a8`)

Solo como referencia: el uso de ARM en `features/player/skip/SkipIntroApi.kt`/`SkipIntroRepository.kt` (tiempo límite
por proveedor, cascada de IDs). No se copió código.

## Descartado o pendiente

- Biblioteca AniList: **hecha en la fase 2**, ver abajo.
- Selección del subtítulo por defecto del archivo para nube (`findFileDefaultSubtitleTrackIndex` de LiteOG): no portado;
  cambia la selección automática de subtítulos del reproductor, fuera del alcance permitido (regla 2).
- «Siguiente episodio» desde el punto de rewatch AniList en la ficha: no portado.
- Logotipos de Mobile: rechazados (ver `brand-assets.md`).

## Fase 2: AniList desde NuvioMobileKai

| Mobile (`composeApp/src/commonMain/kotlin/com/nuvio/app/features/anilist/`) | TV | Qué se tomó |
| --- | --- | --- |
| `AniListTrackingLibraryProvider.kt` | `data/anilist/AniListLibrary.kt` (`AniListTrackingLibraryProvider`) | Lógica: cinco pestañas, `selectionGroup` de estado, un estado por entrada, Planeado al marcar, confirmación al perder progreso o puntuación, resolver la entrada si aún no está en la lista. Reescrito para la interfaz de Arc (`items`/`tabs`/`observeMembership`). |
| `AniListProjection.kt` (`aniListStatusDefinitions`) | `aniListStatusTabs`, `aniListTabFor`, `aniListLibraryEntries`, `entriesFor` | REPEATING en Viendo con marca «↻»; IDs `mal:`/`kitsu:`/`tt` en ese orden; alias para la pertenencia. No se porta la proyección de Continuar viendo (AniList no es fuente de progreso). |
| `AniListTrackingWrites.kt` (`setStatus`, `deleteEntry`) | `AniListRepository.setListStatus` / `deleteListEntry` / `resolveMediaId` | Mismas mutaciones GraphQL que ya tenía el cliente de LiteOG; nunca escriben una entrada REPEATING. |
| `AniListListLinker.kt` | `AniListLinkHelpers.kt` | Solo las reglas puras (`aniListListEntries`, `aniListEntriesToLink`, `armSeasonsOf`, `animeIdLinks`). El enlazador en segundo plano no se porta: sería un trabajo periódico nuevo (regla 6). |
| `AniListLinkUi.kt` (`aniListLinkStatus`), `AniListWatchedSyncAdapter.kt` (`withoutImported`) | `AniListLinkHelpers.kt` | Funciones puras; sin UI. |
| `AniListSyncRepository.kt` | — | No se porta: el `AniListRepository` de LiteOG ya sincroniza la lista y guarda el snapshot; se expone como `listSnapshot`. |
| Pruebas `AniListLinkStatusTest`, `AniListTwoWaySyncTest`, `AniListListLinkerTest` | mismas clases en `app/src/test/.../data/anilist/` | LinkStatus sin cambios; TwoWaySync comprueba el mismo resultado de importación en lugar de las acciones de ficha de Mobile; ListLinker conserva los casos de reglas (los de extremo a extremo dependen del enlazador no portado). |
