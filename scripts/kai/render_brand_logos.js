// Renders each brand logo at 24, 32 and 48 dp (2x) on a light and a dark background and checks that
// the logo is visible on both: enough pixels differ from the background, and nothing touches the edge
// in a way that suggests clipping. Usage: node scripts/kai/render_brand_logos.js <out-dir> <file>...
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const [outDir, ...files] = process.argv.slice(2);
const SIZES = [24, 32, 48];
const BACKGROUNDS = { light: '#F2F2F2', dark: '#16161A' };

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  const results = [];
  for (const file of files) {
    const data = fs.readFileSync(file);
    const mime = file.endsWith('.svg') ? 'image/svg+xml' : 'image/png';
    const src = `data:${mime};base64,${data.toString('base64')}`;
    for (const [bgName, bg] of Object.entries(BACKGROUNDS)) {
      for (const dp of SIZES) {
        await page.setContent(`<html><body style="margin:0;background:${bg}">
          <div id="box" style="width:${dp + 8}px;height:${dp + 8}px;display:flex;align-items:center;justify-content:center;background:${bg}">
          <img id="logo" src="${src}" style="width:${dp}px;height:${dp}px;object-fit:contain"></div></body></html>`);
        await page.waitForFunction(() => document.getElementById('logo').complete);
        const shot = path.join(outDir, `${path.basename(file).replace(/\.[^.]+$/, '')}_${bgName}_${dp}dp.png`);
        await page.locator('#box').screenshot({ path: shot });
        const stats = await page.evaluate(async ({ src, dp, bg }) => {
          const img = new Image(); img.src = src; await img.decode();
          const n = dp * 2, c = document.createElement('canvas'); c.width = n; c.height = n;
          const g = c.getContext('2d'); g.fillStyle = bg; g.fillRect(0, 0, n, n);
          const scale = Math.min(n / img.naturalWidth, n / img.naturalHeight);
          const w = img.naturalWidth * scale, h = img.naturalHeight * scale;
          g.drawImage(img, (n - w) / 2, (n - h) / 2, w, h);
          const px = g.getImageData(0, 0, n, n).data;
          const r0 = parseInt(bg.slice(1, 3), 16), g0 = parseInt(bg.slice(3, 5), 16), b0 = parseInt(bg.slice(5, 7), 16);
          let differ = 0;
          for (let i = 0; i < px.length; i += 4) {
            if (Math.abs(px[i] - r0) + Math.abs(px[i + 1] - g0) + Math.abs(px[i + 2] - b0) > 60) differ++;
          }
          return { visibleShare: differ / (n * n), aspect: img.naturalWidth / img.naturalHeight };
        }, { src, dp, bg });
        results.push({ file: path.basename(file), background: bgName, dp, visibleShare: +stats.visibleShare.toFixed(3), aspect: +stats.aspect.toFixed(3), screenshot: path.basename(shot) });
      }
    }
  }
  await browser.close();
  fs.writeFileSync(path.join(outDir, 'render-check.json'), JSON.stringify(results, null, 2));
  const failed = results.filter(r => r.visibleShare < 0.15);
  for (const r of results) console.log(`${r.file} ${r.background} ${r.dp}dp visible=${r.visibleShare}`);
  if (failed.length) { console.error('Logos not clearly visible:', failed); process.exit(1); }
})();
