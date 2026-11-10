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

- Biblioteca AniList por estados (`AniListTrackingLibraryProvider`, `AniListProjection`, `AniListListLinker` de Mobile),
  `TrackingProviderId.ANILIST` y `LibrarySourceMode.ANILIST`: **pendiente** (ver informe final).
- Selección del subtítulo por defecto del archivo para nube (`findFileDefaultSubtitleTrackIndex` de LiteOG): no portado;
  cambia la selección automática de subtítulos del reproductor, fuera del alcance permitido (regla 2).
- «Siguiente episodio» desde el punto de rewatch AniList en la ficha: no portado.
- Logotipos de Mobile: rechazados (ver `brand-assets.md`).
