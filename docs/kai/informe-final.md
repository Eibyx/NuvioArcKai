# Informe de NuvioArcKai — estado al 10-nov-2026

Rama de trabajo: `ccr-60b9fe86-mp2dal` (la rama de desarrollo asignada a esta sesión; ver «Desviaciones»).
Entorno: contenedor Linux x86_64 sin TV, sin dispositivo `armeabi-v7a`, sin acceso a los secretos del repositorio.
Todo lo que exige la TCL, firma real o GitHub Actions figura como **NO VERIFICADO** con su motivo.

## Pruebas frente a la línea base

`./gradlew :app:testFullDebugUnitTest --continue` (JDK 21 local; montón de pruebas a 3 GB por un init script local,
igual para ambas ejecuciones; sin cambios en el repositorio):

| | Pruebas | Aprobadas | Fallidas | Omitidas | Clases |
| --- | --- | --- | --- | --- | --- |
| Base (`main`, `c54507f`) | 8 890 | 8 868 | 10 | 12 | 1 061 |
| Kai (1.ª ejecución completa) | 9 089 | 9 065 | 12 | 12 | 1 094 |

- Las **10 fallas de la base** (`LocalhostZeroCopyDataSourceTest`, `TraktAuthServiceTest`, `ContentLengthProbeIsolationTest` ×2,
  `ParallelDiagnosticCallsTest` ×3, `ParallelRangeProductionWorkloadTest`, `StreamBaselineIsolationTest` ×2) fallan igual en Kai:
  dependen de red/TLS/temporización del contenedor. No cuentan como regresión. Detalle en `baseline-tests.json`.
- **2 fallas nuevas en la 1.ª ejecución**, resueltas:
  - `SettingsSearchTest` — exige indexar cada fila de ajustes; faltaban 11 filas de la pantalla de cuenta de nube.
    Añadidas a `SettingsSearchIndex.kt`; la prueba pasa (`--tests '*SettingsSearchTest'`: BUILD SUCCESSFUL).
  - `HomeEnrichmentRepositoryBoundaryTest` — intermitente: falla dentro de la suite completa y pasa aislada con el mismo
    código; no toca nada de Kai. Queda anotado como riesgo, no se desactivó.
- Ninguna clase de la base desaparece y ninguna aprueba menos pruebas salvo esas dos.
- **199 pruebas nuevas en 33 clases**, todas aprobadas (`kai-tests-run1.json`): nube 89 (las 12 clases de LiteOG + ida y vuelta
  `ServerItemRef`), AniList 78 (las 10 de LiteOG + `AniListPinGateTest`), segmentos 28, ARM 8, identidad 4.
- Pruebas originales cambiadas, con motivo: `IconPackTest`, `ApkInstallerValidationTest` y `ForkUpdateRepositoryTest`
  (paquete y repositorio del actualizador, por requisito); `CloudSourceLabelTest` de LiteOG («Google Drive» completo por la
  guía de marca). Ninguna se desactivó ni se marcó `@Ignore`.
- Python: `PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests` → 30 pruebas OK (incluye 4 nuevas de `--kai`).

## APK

| Comprobación | Resultado |
| --- | --- |
| `:app:assembleFullDebug` (Kai) | BUILD SUCCESSFUL; en `app/build/outputs/apk/full/debug/` solo `app-full-armeabi-v7a-debug.apk` (la base produce además `arm64-v8a`) |
| `aapt2 dump badging` | `package: name='com.eibyx.nuvioarckai' versionCode='1' versionName='1.1.0-nt.stable-kai.1'`, `targetSdkVersion:'36'`, `native-code: 'armeabi-v7a'` |
| Inventario nativo frente a la base | 24 bibliotecas, todas ELF de 32 bits little-endian `e_machine = 40`; ninguna perdida ni añadida (`native-inventory-debug.json` frente a `baseline-native.json`); `libdovi_bridge.so` con enlace real a libdovi; `libmpv.so`, `libnuviothumb.so`, `libc++_shared.so` presentes |
| `libass.so` | Ausente en el APK debug **de la base y de Kai** (está `libasskt.so`); Arc la exige en release. Comprobación pendiente sobre la release firmada |
| `scan_apk_secrets.py` | «No private credentials found.» |
| Firma | El APK debug lleva la clave de depuración (esperado). **APK release firmado: NO VERIFICADO** — el keystore de Kai solo está en los secretos del repositorio, que esta sesión no puede leer; hay que lanzar `kai-release-armv7.yml` |
| Release en GitHub | NO publicada (depende del workflow y de tu confirmación en la TV) |

## Criterios

### Fase 0

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| `applicationId` `com.eibyx.nuvioarckai`, `namespace` sin cambios | VERIFICADO | `KaiIdentityTest` (`BuildConfig.APPLICATION_ID`), `IconPackTest`; badging abajo |
| `appfilter.xml` con el paquete nuevo | VERIFICADO | `IconPackTest` (80 entradas) |
| Solo `armeabi-v7a`, sin universal, sin AAB | VERIFICADO en build | `splits.abi` + comprobación por variante + falla ante `bundle*` (ver APK abajo) |
| Firma sin valores por defecto; release sin keystore falla | VERIFICADO (lógica) | `validateSigning/package/assemble…Release` comprueban las cuatro variables; sin keystore no se firma |
| Actualizador a `Eibyx/NuvioArcKai`, versión parseable | VERIFICADO | `KaiIdentityTest`, `ForkUpdateRepositoryTest` |
| Instalación junto a Arc en la TCL | NO VERIFICADO | sin dispositivo |
| Protección de rama `main` | NO VERIFICADO | sin permiso de administración del repositorio en esta sesión |

### Tarea D — segmentos

Ver `diagnostico-segmentos.md`. Vocabulario, idioma, fixture MKV, paridad ExoPlayer/MPV, etiquetas, prefijos de ID, ARM
y seek: VERIFICADOS por pruebas. Todas las pruebas de segmentos de Arc nombradas en el requisito pasan sin cambios.
Matriz en la TCL, tiempos de aparición/foco del botón y tiempo de reanudación tras el salto: NO VERIFICADOS (sin TV).

### Tarea C — logotipos

`brand-assets.md`: fuente oficial, SHA-256 y condiciones de los tres; render automático 24/32/48 dp claro y oscuro
(`brand-render-check.json`); avisos en Licencias. Capturas en la TV: NO VERIFICADO. **La fuente del logotipo de TeraBox
está pendiente de tu aceptación.**

### Tarea A — TeraBox y Google Drive

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Filas TeraBox / Google Drive en «Añadir» y cuentas en «Conectados», con logotipo | IMPLEMENTADO, compila | `MediaServersSettingsScreen.kt`; render en TV NO VERIFICADO |
| QR, caducidad y bloqueo de la página del móvil | VERIFICADO (lógica de LiteOG) | `CloudSetupHandlerTest` 9/9 |
| Índice, coincidencias, numeración absoluta, especiales | VERIFICADO | `CloudMatcherTest`, `CloudReleaseParserTest`, `CloudSpecialsTest`, `CloudTitleAliasesTest` |
| Loopback: rangos, renovación de enlace, cierre | VERIFICADO (JVM) | `LocalCloudStreamServerTest` 14/14 |
| Subtítulos `.ass` externos | VERIFICADO (selección) | `CloudSidecarSubtitlesTest`; renderizado libass en TV NO VERIFICADO |
| Prefijo de conexión sin `:` (ida y vuelta) | VERIFICADO | `KaiCloudServerRefTest` |
| Plex/Jellyfin/Emby/Silo sin cambios | VERIFICADO | todas las pruebas `data/mediaserver` de Arc pasan |
| Reproducción ExoPlayer/MPV, sockets cerrados a los 5 min, borrado de perfil en dispositivo | NO VERIFICADO | sin TV |

### Tarea B — AniList

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Motor de LiteOG (cola, reintentos, COMPLETED, no retroceso, temporadas/cours) | VERIFICADO | 10 clases de LiteOG, 71 pruebas |
| QR + Auth Pin (código único, caducidad, 5 intentos, token inválido, solo LAN, sin scripts) | VERIFICADO (lógica) | `AniListPinGateTest` 7/7; conexión real NO VERIFICADA |
| Gancho en `markAsCompleted` y `markAsCompletedBatch` | IMPLEMENTADO | `WatchProgressRepositoryImpl.kt`; sin prueba propia todavía |
| Fila en Seguimiento, diálogo con logotipo, búsqueda | IMPLEMENTADO, compila | `TrackingSettingsScreen.kt` |
| `TrackingProviderId.ANILIST`, `LibrarySourceMode.ANILIST`, biblioteca por estados (Mobile) | **PENDIENTE** | no implementado en esta sesión |

## Desviaciones y decisiones

1. **Ramas.** Las instrucciones de la sesión solo permiten empujar a `ccr-60b9fe86-mp2dal`; no se crearon `kai/develop`
   ni las ramas por tarea. El trabajo está en commits separados de esa rama.
2. **Tipos de intervalo (D3).** Se mantienen `intro`/`outro`/`recap` y se añade `SkipInterval.label`; cambiar el tipo rompía
   `ChapterSkipIntervalsTest` y `OUTRO_SEGMENT_TYPES`, que el requisito pide conservar.
3. **AniList sin `TrackingProviderId`.** La fila y el diálogo usan su propio estado; añadir la entrada al enum afecta a
   ~24 archivos con `when` exhaustivos y a la biblioteca: queda como siguiente paso.
4. **Secretos.** Esta sesión no puede listar los secretos del repositorio. El workflow lee `KAI_*` y, si no existen, los
   `NUVIO_RELEASE_*` de Arc, y `ANILIST_CLIENT_ID` como secreto propio o dentro de `LOCAL_PROPERTIES_BASE64`.
5. **`PROMPT_MAESTRO.md`** no se copió al repositorio.
