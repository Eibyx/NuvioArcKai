# Informe de NuvioArcKai — estado al 11-nov-2026 (fase 2)

Rama de trabajo: `ccr-60b9fe86-mp2dal`. Esta sesión solo puede subir a esa rama; no se creó `kai/develop` (el usuario
abrirá los PR). PR mínimo con **solo** el workflow: <https://github.com/Eibyx/NuvioArcKai/pull/1> (rama
`kai/release-workflow`, creada a petición expresa). Cada job usa `actions/checkout@v6` sin `ref`, así que hace checkout
de la rama elegida al lanzarlo y lee de ella `scripts/kai/*` y `scripts/verify_native_apk.py`.

Entorno: contenedor Linux x86_64, sin TV, sin dispositivo `armeabi-v7a` y sin acceso a los secretos del repositorio.
Lo que exige la TCL, la firma real o una ejecución en GitHub Actions queda **NO VERIFICADO** con su motivo.

## 1. Pruebas frente a la línea base

`./gradlew :app:testFullDebugUnitTest --rerun --continue`, tres veces en la base (`main`, `c54507f`) y tres en Kai
(`5286a7f`), una tras otra en la misma máquina (montón de pruebas a 3 GB con un init script local, igual en las dos):

| Ejecución | Base: pruebas / fallidas | Fallas fuera de las 10 de la base | Kai: pruebas / fallidas | Fallas fuera de las 10 de la base |
| --- | --- | --- | --- | --- |
| 1 | 8 890 / 10 | ninguna | 9 108 / 10 | **ninguna** |
| 2 | 8 890 / 12 | `MetaRepositoryRateLimitTest` (pausa de host), `HomeContinueWatchingOwnershipTest` | 9 108 / 11 | `PlayerLoadingSourcePreferencesTest` |
| 3 | 8 890 / 10 | `PostPlayWatchedHistorySchedulingTest` (y una de las 10 de la base pasó) | 9 108 / 11 | `MetaRepositoryRateLimitTest` (pausa de host) |

- **Objetivo cumplido en la ejecución 1:** exactamente las 10 fallas de la base y ninguna más (`kai-tests.json`). Las 10 dependen
  de red/TLS/temporización del contenedor y fallan igual en la base (`baseline-tests.json`).
- Las fallas sueltas de las ejecuciones 2 y 3 son **intermitencias que la base también tiene**: la base falló en 3 pruebas
  distintas fuera de sus 10, y `MetaRepositoryRateLimitTest` falló una vez en cada lado. Ninguna de esas pruebas toca código de
  Kai. `PlayerLoadingSourcePreferencesTest` falló 1 de 3 en Kai y 0 de 3 en la base; con una sola ocurrencia no hay evidencia de
  que Kai la cause. Queda anotada como riesgo; no se desactivó ni se tocó.
- **`HomeEnrichmentRepositoryBoundaryTest`:** aislada, 5/5 aprobadas en la base y 5/5 en Kai; en suite completa, 0 fallas en 3+3.
  No falla más en Kai. Las pruebas nuevas no cambian `Locale`, `TimeZone`, `Dispatchers.Main` ni propiedades del sistema
  (búsqueda en `core/cloudstorage`, `data/anilist`, `kai/*`, `Kai*Test`), y los servidores NanoHTTPD de las pruebas de nube se
  cierran en `@After`. No se le subió el tiempo límite.
- **218 pruebas nuevas**, todas aprobadas en las tres ejecuciones.
- Pruebas originales cambiadas, con motivo: `IconPackTest`, `ApkInstallerValidationTest`, `ForkUpdateRepositoryTest` (paquete y
  repositorio del actualizador, por requisito); `CloudSourceLabelTest` de LiteOG («Google Drive» completo). Ninguna desactivada.
- Python (`PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests`): 33 OK.

## 2. APK

| Comprobación | Resultado |
| --- | --- |
| `:app:assembleFullDebug` | Solo `app-full-armeabi-v7a-debug.apk` (la base genera además `arm64-v8a`) |
| `aapt2 dump badging` | `package: name='com.eibyx.nuvioarckai' versionCode='1' versionName='1.1.0-nt.stable-kai.1'`, `targetSdkVersion:'36'`, `native-code: 'armeabi-v7a'` |
| Inventario nativo | 24 bibliotecas, ELF 32 bits LE `e_machine = 40`, ninguna perdida ni añadida frente a la base; `libdovi_bridge.so` con enlace real a libdovi |
| libass (comprobación nueva) | `verify_native_apk.py --kai` sobre el APK debug: `libasskt.so` exporta `ass_library_init` y `ass_render_frame` y no tiene `NEEDED libass.so`; `libmpv.so` exporta 216 símbolos `ass_*`; ninguna dependencia `NEEDED` sin resolver (APK + bibliotecas del sistema Android). Mismo resultado en la base: libass va enlazado de forma estática |
| Escaneo de secretos | «No private credentials found.» |
| **APK release firmado** | **NO VERIFICADO**: el keystore solo está en los secretos del repositorio. Pendiente de fusionar el PR #1 y lanzar el workflow; entonces se revisarán badging, SHA-256 del certificado (se anotará aquí), libass sobre el release, escaneo y `SHA256SUMS.txt` |
| Release en GitHub | No publicada |

## 3. Criterios

### Fase 0 — VERIFICADO salvo la TV

Paquete, `appfilter.xml`, solo `armeabi-v7a`, sin AAB, release sin keystore falla, actualizador: ver fase 1 (`KaiIdentityTest`,
`IconPackTest`, badging). Instalación junto a Arc en la TCL y protección de `main`: NO VERIFICADO.

### Tarea D — VERIFICADO por pruebas; TV NO VERIFICADO

`diagnostico-segmentos.md`. Desviación aceptada: tipos `intro`/`outro` con etiqueta aparte.

### Tarea C — VERIFICADO

Tres logotipos oficiales con procedencia y SHA-256; **TeraBox aceptado por el usuario (10-nov-2026)**. Capturas en la TV: NO VERIFICADO.

### Tarea A — lógica VERIFICADA por pruebas; reproducción en TV NO VERIFICADA

Igual que en la fase 1 (89 pruebas de nube + todas las de servidores de Arc).

### Tarea B — AniList

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Motor de LiteOG (cola, reintentos, COMPLETED, no retroceso, cours) | VERIFICADO | 10 clases de LiteOG |
| QR + Auth Pin | VERIFICADO (lógica) | `AniListPinGateTest` 7/7; aviso de red de confianza en el diálogo |
| Enganche: `markAsCompleted` (con y sin difusión) y `markAsCompletedBatch`, una entrada por episodio; no es `TrackingHistoryWriter` | VERIFICADO | `KaiAniListCompletionHookTest` 6/6; repetir el final → misma clave, la cola guarda un solo envío (`AniListSendQueueTest`) |
| `CancellationException` relanzada | VERIFICADO | `KaiAniListCompletionHookTest` |
| `TrackingProviderId.ANILIST`, todos los `when` | VERIFICADO (compila) | `upstream-changes.md` (fase 2) |
| `LibrarySourceMode.ANILIST` y biblioteca de 5 pestañas, REPEATING en Viendo sin escribirse, IDs resolubles, entradas sin ID no abren ficha, confirmación al perder progreso/puntuación | VERIFICADO (lógica) | `AniListLibraryTest` 5/5; pruebas de biblioteca de Arc sin cambios |
| Pruebas de Mobile adaptadas | VERIFICADO | `AniListLinkStatusTest` 3/3, `AniListTwoWaySyncTest` 3/3, `AniListListLinkerTest` 2/2 |
| Logotipo en la biblioteca | No aplica | Arc muestra el proveedor de biblioteca como texto; AniList aparece como «ANILIST», igual que los demás |
| Conexión real, cambio de estado desde la ficha y progreso en AniList | NO VERIFICADO | necesita la TV y una cuenta |

## 4. Desviaciones y decisiones

1. Ramas: ver arriba.
2. Tipos de intervalo: se conservan `intro`/`outro` (aceptado).
3. Del enlazador de lista de Mobile solo se portan las reglas puras; el trabajo en segundo plano sería un servicio periódico nuevo (regla 6).
4. AniList no es fuente de progreso: no guarda posiciones y añadirlo cambiaría Continuar viendo.
5. Secretos: el workflow lee `KAI_*` y, si faltan, `NUVIO_RELEASE_*`; `ANILIST_CLIENT_ID` como secreto propio o en `LOCAL_PROPERTIES_BASE64`.

## 4b. Job de pruebas del workflow

`kai-release-armv7.yml` (copia de esta rama) ya no se bloquea por las 10 fallas medidas de la base:

1. `:app:testFullDebugUnitTest --continue` sin cortar el job; los XML se guardan aparte en `build/kai-tests/first`
   (Gradle vacía el directorio de resultados en la siguiente ejecución).
2. `scripts/kai/compare_test_baseline.py` compara cada falla (clase + prueba) con `docs/kai/baseline-tests.json`.
3. Las clases con fallas fuera de la lista se reintentan **una vez** (`--rerun --tests <clase>`); sus resultados sustituyen
   a los de la primera ejecución solo para esas clases.
4. El job falla solo si queda alguna falla nueva tras el reintento. El informe (`report.md`/`report.json`, XML de ambas
   pasadas e informe HTML de Gradle) se sube siempre como artefacto `kai-unit-test-report` y se añade al resumen del run.

Comprobado: 6 pruebas Python con XML simulados (`scripts/tests/test_compare_test_baseline.py`); con los XML reales de la
ejecución 3 de Kai, la primera comparación detecta `MetaRepositoryRateLimitTest` como nueva, el reintento real con Gradle
la aprueba y el veredicto final es OK; y el paso completo del workflow, ejecutado con `bash -e` y un `gradlew` simulado,
da éxito con solo fallas conocidas, éxito con una falla que pasa al reintentar y error con una falla que se repite.

## 4c. Compilación firmada verificada

Ejecución de prueba (`publish=false`, commit `873df72`): https://github.com/Eibyx/NuvioArcKai/actions/runs/42970511854

Artefacto `kai-armv7-release` descargado y verificado por separado:

| Comprobación | Resultado |
|---|---|
| SHA-256 del APK (`app-full-armeabi-v7a-release.apk`) | `1f7c9e6f4aa0ba0a728005bd5c944e56882359cab0e9ead340250490cbdbc041` (coincide con `SHA256SUMS.txt`) |
| apksigner | firma v2 válida, un firmante, `CN=Eibyx, OU=Development, O=NuvioArcKai, L=Lima, ST=Lima, C=PE` (no es de depuración) |
| SHA-256 del certificado | `b5f906aecd445cf51d6abfcc1276b40eb2a2c29739efeacc1d73fc88bd940feb` |
| aapt2 | `com.eibyx.nuvioarckai`, versionCode 1, versionName `1.1.0-nt.stable-kai.1`, targetSdk 36, native-code `armeabi-v7a` |
| Bibliotecas nativas | solo `lib/armeabi-v7a/` |

Correcciones del workflow hasta llegar a verde: memoria de las pruebas a 2 GB (`2815f51`, la primera pasada daba
`OutOfMemoryError`) y lectura de la salida real de apksigner/aapt2 y del dex sin `SIGPIPE` (`873df72`).

## 4d. Pre-release publicada

https://github.com/Eibyx/NuvioArcKai/releases/tag/1.1.0-nt.stable-kai.1 — ejecución
https://github.com/Eibyx/NuvioArcKai/actions/runs/42976270884 (`publish=true`, commit `ce210ad`).

Marcada como pre-release; tres assets (`app-full-armeabi-v7a-release.apk`, `SHA256SUMS.txt`, `native-inventory.json`);
el cuerpo lleva `<!-- nuvio-fork-version-code: 1 -->`. Descargados de la release y verificados: el APK coincide con
`SHA256SUMS.txt` (`277356acae3aeaa4fe2b8d8835516d6709b0305f309b186b65c8fee761bdad43`; es una compilación nueva, por eso
difiere del artefacto de prueba), firma v2 válida con el mismo certificado
`b5f906aecd445cf51d6abfcc1276b40eb2a2c29739efeacc1d73fc88bd940feb`, mismo paquete, versión y ABI única `armeabi-v7a`.

## 5. Pendiente para el usuario

1. La prueba en la TCL del anexo del prompt de continuación.
