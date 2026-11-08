const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const ROOT='/home/claude/Kosh/app/src/main/assets/pdfviewer';
const MIME={html:'text/html',js:'text/javascript',mjs:'text/javascript',css:'text/css',svg:'image/svg+xml',wasm:'application/wasm'};
const server=http.createServer((req,res)=>{const u=decodeURIComponent(req.url.split('?')[0]);let f=u.startsWith('/app/')?path.join(ROOT,u.slice(5)):u.startsWith('/doc/')?'/tmp/vt/'+u.slice(5):null;if(!f||!fs.existsSync(f)){res.writeHead(404);res.end();return;}res.writeHead(200,{'Content-Type':MIME[path.extname(f).slice(1)]||'application/octet-stream'});fs.createReadStream(f).pipe(res);});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const ok=(c,m)=>{console.log((c?'PASS ':'FAIL ')+m);if(!c)process.exitCode=1;};
(async()=>{await new Promise(r=>server.listen(0,r));const base=`http://127.0.0.1:${server.address().port}`;
const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome',args:['--no-sandbox']});
const page=await b.newPage({viewport:{width:400,height:700}});
page.on('pageerror',e=>console.log('PAGEERROR',e.message));
await page.addInitScript(()=>{window.__calls=[];window.JWI=new Proxy({},{get:(_t,n)=>(...a)=>{window.__calls.push([n,...a]);}});});
await page.goto(base+'/app/viewer.html');await page.evaluate(()=>setupHelper());await page.evaluate(u=>openUrl({url:u}),base+'/doc/test.pdf');await sleep(2500);
await page.evaluate(()=>setScaleValue('page-width'));await sleep(600);
const calls=n=>page.evaluate(n=>window.__calls.filter(c=>c[0]===n),n);
const strokes=()=>page.evaluate(()=>DOODLE.strokes.length);
await page.evaluate(()=>{setDoodleMode(true);setDoodleTool('pen');setDoodleColor('#1E88E5');setDoodleWidth(0.006);});
// stroke 1 (blue)
await page.mouse.move(100,150);await page.mouse.down();for(let i=1;i<=10;i++)await page.mouse.move(100+i*15,150+Math.sin(i)*20);await page.mouse.up();
ok(await strokes()===1,'one stroke drawn');
// stroke 2 (red)
await page.evaluate(()=>setDoodleColor('#E53935'));
await page.mouse.move(100,300);await page.mouse.down();for(let i=1;i<=8;i++)await page.mouse.move(100+i*20,300);await page.mouse.up();
ok(await strokes()===2,'second stroke drawn');
ok(await page.evaluate(()=>document.querySelectorAll('.kosh-doodle-layer path').length)===2,'two svg paths rendered');
ok(await page.evaluate(()=>document.querySelector('#viewerContainer').scrollTop)<=20,'drawing did not scroll the page');
ok((await calls('onSingleClick')).length===0,'drawing does not trigger a tap');
// eraser removes instantly
await page.evaluate(()=>setDoodleTool('eraser'));
await page.mouse.move(180,300);await page.mouse.down();await page.mouse.move(185,302);await page.mouse.up();
ok(await strokes()===1,'eraser removed the red stroke');
ok(await page.evaluate(()=>document.querySelectorAll('.kosh-doodle-layer path').length)===1,'erased path removed from the page immediately');
// undo restores erased stroke
await page.evaluate(()=>doodleUndo());
ok(await strokes()===2,'undo restores the erased stroke');
// rotation keeps strokes
await page.evaluate(()=>setRotation(90));await sleep(900);
ok(await page.evaluate(()=>document.querySelectorAll('.kosh-doodle-layer path').length)===2,'strokes still shown after rotating');
await page.evaluate(()=>setRotation(0));await sleep(900);
// save
await page.evaluate(()=>{window.__calls.length=0;saveDoodled();});
let t=Date.now();while(Date.now()-t<10000&&!(await calls('onSaveEnd')).length&&!(await calls('onSaveFailed')).length)await sleep(100);
ok((await calls('onSaveFailed')).length===0,'save did not fail '+JSON.stringify(await calls('onSaveFailed')));
const chunks=(await calls('onSaveChunk')).map(c=>c[1]);const begin=(await calls('onSaveBegin'))[0];
const buf=Buffer.concat(chunks.map(c=>Buffer.from(c,'base64')));
ok(begin&&buf.length===begin[1],'received all '+buf.length+' bytes in '+chunks.length+' chunk(s)');
fs.writeFileSync('/tmp/vt/out.pdf',buf);
await b.close();server.close();})();
