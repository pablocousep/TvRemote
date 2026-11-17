// Composes Google Play listing art from raw app screens.
const {chromium} = require('playwright');
const fs = require('fs'); const path = require('path');
const [rawDir, outDir] = process.argv.slice(2);
const font = w => fs.readFileSync(`fonts/package/files/inter-latin-${w}-normal.woff2`).toString('base64');
const css = `
@font-face{font-family:Inter;font-weight:500;src:url(data:font/woff2;base64,${font(500)})}
@font-face{font-family:Inter;font-weight:700;src:url(data:font/woff2;base64,${font(700)})}
@font-face{font-family:Inter;font-weight:800;src:url(data:font/woff2;base64,${font(800)})}
*{margin:0;box-sizing:border-box}
body{font-family:Inter,sans-serif;-webkit-font-smoothing:antialiased}`;
const img = f => 'data:image/png;base64,' + fs.readFileSync(path.join(rawDir, f)).toString('base64');
const glyph = fs.readFileSync('glyph.txt', 'utf8');

const slides = [
  {raw:'1-remote.png', title:'Tu Smart TV,<br>en el móvil', sub:'Un mando moderno, rápido y sin anuncios', accent:'#3E8EF7'},
  {raw:'2-apps.png', title:'Tus apps,<br>a un toque', sub:'Fija tus favoritas en el mando principal', accent:'#30C26F'},
  {raw:'4-power.png', title:'Enciéndela<br>desde el sofá', sub:'Te guiamos para activarlo en 3 pasos', accent:'#F5A623'},
  {raw:'3-settings.png', title:'Cada tele,<br>con su nombre', sub:'Salón, dormitorio… y aviso si cambias de Wi‑Fi', accent:'#9B6CFF'},
  {raw:'5-numbers.png', title:'Todas las teclas<br>del mando', sub:'Números, colores, fuente, guía y más', accent:'#E5484D'},
  {raw:'6-light.png', title:'Oscuro o claro,<br>como prefieras', sub:'También sigue el tema de tu móvil', accent:'#3E8EF7', light:true},
];

const shot = (s) => `
<div style="width:1080px;height:1920px;position:relative;overflow:hidden;
  background:${s.light ? '#EEF0F4' : '#060709'}">
  <div style="position:absolute;left:50%;top:1150px;width:1500px;height:1500px;transform:translate(-50%,-50%);
    background:radial-gradient(circle, ${s.accent}${s.light ? '40' : '55'} 0%, ${s.accent}00 62%)"></div>
  <div style="position:absolute;inset:0;background:radial-gradient(120% 60% at 50% 0%, ${s.light ? '#ffffff' : '#1a1c22'} 0%, transparent 70%)"></div>
  <div style="position:absolute;top:150px;left:0;right:0;text-align:center;padding:0 70px">
    <div style="display:inline-block;width:70px;height:7px;border-radius:4px;background:${s.accent};margin-bottom:46px"></div>
    <div style="font-weight:800;font-size:92px;line-height:1.04;letter-spacing:-3px;color:${s.light ? '#121418' : '#F6F7F9'}">${s.title}</div>
    <div style="margin-top:30px;font-weight:500;font-size:38px;line-height:1.3;color:${s.light ? '#5d626b' : '#9DA1A9'}">${s.sub}</div>
  </div>
  <div style="position:absolute;top:620px;left:50%;transform:translateX(-50%);width:760px;height:1646px;border-radius:104px;
    background:${s.light ? '#d9dce2' : '#1d1f24'};padding:14px;
    box-shadow:0 0 0 2px ${s.light ? 'rgba(0,0,0,.06)' : 'rgba(255,255,255,.09)'}, 0 60px 140px rgba(0,0,0,${s.light ? '.22' : '.7'}), 0 0 120px ${s.accent}${s.light ? '22' : '33'}">
    <div style="width:100%;height:100%;border-radius:90px;overflow:hidden;background:#000">
      <img src="${img(s.raw)}" style="width:100%;display:block">
    </div>
  </div>
</div>`;

const feature = `
<div style="width:1024px;height:500px;position:relative;overflow:hidden;background:#060709">
  <div style="position:absolute;left:760px;top:330px;width:900px;height:900px;transform:translate(-50%,-50%);
    background:radial-gradient(circle,#3E8EF755 0%,#3E8EF700 60%)"></div>
  <div style="position:absolute;inset:0;background:radial-gradient(80% 90% at 0% 0%,#1b1e25 0%,transparent 70%)"></div>
  <div style="position:absolute;left:70px;top:0;bottom:0;display:flex;flex-direction:column;justify-content:center">
    <div style="display:flex;align-items:center;gap:22px">
      <div style="width:84px;height:84px;border-radius:24px;background:#000;box-shadow:0 0 0 2px rgba(255,255,255,.1)">
        <svg viewBox="14 14 80 80" width="84" height="84"><path fill="#F4F4F6" fill-rule="evenodd" d="${glyph}"/></svg>
      </div>
      <div style="font-weight:800;font-size:54px;letter-spacing:-1.5px;color:#F6F7F9">TvRemote</div>
    </div>
    <div style="margin-top:30px;font-weight:800;font-size:46px;line-height:1.08;letter-spacing:-1.2px;color:#F6F7F9">Tu Smart TV,<br>en el móvil</div>
    <div style="margin-top:18px;font-weight:500;font-size:22px;color:#9DA1A9">Mando, apps y encendido · sin anuncios</div>
  </div>
  <div style="position:absolute;left:600px;top:60px;width:330px;height:715px;border-radius:46px;background:#1d1f24;padding:7px;
    box-shadow:0 0 0 1.5px rgba(255,255,255,.09),0 30px 80px rgba(0,0,0,.7),0 0 70px #3E8EF733">
    <div style="width:100%;height:100%;border-radius:39px;overflow:hidden"><img src="${img('1-remote.png')}" style="width:100%;display:block"></div>
  </div>
</div>`;

const icon = `
<div style="width:512px;height:512px;background:#000;display:flex;align-items:center;justify-content:center">
  <svg viewBox="14 14 80 80" width="512" height="512"><path fill="#F4F4F6" fill-rule="evenodd" d="${glyph}"/></svg>
</div>`;

(async () => {
  const b = await chromium.launch();
  const render = async (html, w, h, file) => {
    const p = await b.newPage({viewport: {width: w, height: h}, deviceScaleFactor: 1});
    await p.setContent(`<style>${css}</style>${html}`);
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(200);
    await p.screenshot({path: path.join(outDir, file), clip: {x: 0, y: 0, width: w, height: h}});
    await p.close();
  };
  fs.mkdirSync(path.join(outDir, 'phoneScreenshots'), {recursive: true});
  for (let i = 0; i < slides.length; i++) await render(shot(slides[i]), 1080, 1920, `phoneScreenshots/${i + 1}.png`);
  await render(feature, 1024, 500, 'featureGraphic.png');
  await render(icon, 512, 512, 'icon.png');
  await b.close();
})();
