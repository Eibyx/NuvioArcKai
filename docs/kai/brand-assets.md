# Logotipos de marca (Tarea C)

Los tres archivos se usan solo para identificar la integración correspondiente. No se redibujaron, ni
vectorizaron, ni recolorearon: son los bytes descargados de la fuente oficial, sin transformación.
Se pintan con `rememberRawSvgPainter` (Coil), que no tiñe, con `ContentScale.Fit`.

| Archivo en `app/src/main/res/raw/` | Marca y titular | URL de origen | Descarga | SHA-256 | Transformación |
| --- | --- | --- | --- | --- | --- |
| `google_drive_logo.png` (128×128 RGBA) | Google Drive — Google LLC | `https://www.gstatic.com/images/branding/productlogos/drive_2026/v2/web-64dp/logo_drive_2026_color_2x_web_64dp.png` (enlace «Google Drive product logo» de `https://developers.google.com/drive/branding`) | 10-nov-2026 | `39e2c15449e7fa75ebe3a29f3f99e2e9ee11b5ef36aebf4dda3d30e484635495` | Ninguna (escalado en pantalla) |
| `terabox_logo.png` (1200×1200 RGBA) | TeraBox — Flextech Inc. | `https://s5.teraboxcdn.com/common-static/favicon.ico` (icono declarado por `https://www.terabox.com/`; el archivo es un PNG aunque se llame `.ico`) | 10-nov-2026 | `e5870bbb8800f47d8f608f92d33ef7e5f07ff74446d7116f2762fab135ee4e7b` | Ninguna (solo cambio de nombre de archivo) |
| `anilist_logo.png` (512×512, paleta) | AniList — AniList | `https://anilist.co/img/icons/android-chrome-512x512.png` (icono del `manifest.json` de `https://anilist.co`) | 10-nov-2026 | `318dea88cf67b0334eb82b925362a35d1f56c25aaa6be4304e7ba7326e238d5a` | Ninguna |

## Condiciones de uso

- **Google Drive.** La página de marca indica: «You don't need pre-approval to use the Google Drive logo»; su uso
  se rige por los Términos de la API de Google Drive; «You can resize the logo, but don't make any other changes»;
  «Always refer to Google Drive in full name» y «Don't abbreviate the term "Google Drive."» dentro de la app.
  Atribución: «Google Drive is a trademark of Google …». Consecuencia aplicada: las filas, tarjetas y grupos de
  fuentes dicen «Google Drive» completo; no se usa la etiqueta corta «Drive» de LiteOG.
- **TeraBox.** No hay kit de prensa público. El logotipo es el icono que sirve el sitio oficial de Flextech
  (`www.terabox.com`) desde su CDN, y corresponde al logotipo de 2024 (caja que integra la «T»). Se usa solo para
  identificar la integración y con aviso de marca. **Fuente aceptada por el usuario el 10-nov-2026.**
- **AniList.** Icono oficial de la app web (placa oscura `#1F2631` con la «A» blanca y la «L» `#02A9FF`), del
  manifiesto de `anilist.co`. Se descartó el SVG de `AniList/ApiV2-GraphQL-Docs` (`docs/public/anilist.svg`) y
  `https://anilist.co/img/icons/icon.svg` porque no llevan placa y la «A» blanca desaparece en fondos claros
  (renderizado: solo el 8–9 % del área visible a 24–48 dp sobre `#F2F2F2`). No hay guía de marca pública de
  AniList; se usa solo para identificar la integración, con aviso de marca.

Recursos de NuvioMobileKai rechazados (no son el logotipo oficial): `cloud_terabox_logo.xml` (T dibujada a mano),
`cloud_google_drive_logo.xml` (redibujado aproximado) y `anilist_logo.xml` (glifo monocromo de Simple Icons).

## Verificación de renderizado

`node scripts/kai/render_brand_logos.js <salida> app/src/main/res/raw/{terabox_logo,google_drive_logo,anilist_logo}.png`
renderiza cada archivo a 24, 32 y 48 dp (×2) sobre `#F2F2F2` y `#16161A` con Chromium y falla si menos del 15 %
del área se distingue del fondo. Resultado del 10-nov-2026 en `docs/kai/brand-render-check.json`:
TeraBox 0,79–0,96; Google Drive 0,47–0,48; AniList 0,23–0,87. Ninguno queda invisible ni recortado.

Avisos de marca visibles en Ajustes → Licencias y atribuciones (`LicensesAttributionsScreen.kt`,
cadenas en `res/values*/strings_kai.xml`).
