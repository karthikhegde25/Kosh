const http=require('http'),fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const ROOT='/home/claude/Kosh/app/src/main/assets/pdfviewer';
const MIME={html:'text/html',js:'text/javascript',mjs:'text/javascript',css:'text/css'};
const server=http.createServer((req,res)=>{const u=decodeURIComponent(req.url.split('?')[0]);let f=u.startsWith('/app/')?path.join(ROOT,u.slice(5)):u.startsWith('/doc/')?'/tmp/vt/'+u.slice(5):null;if(!f||!fs.existsSync(f)){res.writeHead(404);res.end();return;}res.writeHead(200,{'Content-Type':MIME[path.extname(f).slice(1)]||'application/octet-stream'});fs.createReadStream(f).pipe(res);});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const ok=(c,m)=>{console.log((c?'PASS ':'FAIL ')+m);if(!c)process.exitCode=1;};
(async()=>{await new Promise(r=>server.listen(0,r));const base=`http://127.0.0.1:${server.address().port}`;
const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome',args:['--no-sandbox']});
async function run(doc,kind,args,out){
  const page=await b.newPage();
  page.on('pageerror',e=>console.log('PAGEERROR',e.message));
  await page.addInitScript(()=>{window.__c=[];window.KEDIT=new Proxy({},{get:(_t,n)=>(...a)=>{window.__c.push([n,...a]);}});});
  await page.goto(base+'/app/pdf_edit.html');
  await page.evaluate(([k,a])=>koshRun(k,a),[kind,Object.assign({url:base+'/doc/'+doc},args)]);
  const c=await page.evaluate(()=>window.__c);await page.close();
  const chunks=c.filter(x=>x[0]==='chunk').map(x=>Buffer.from(x[1],'base64'));
  if(chunks.length) fs.writeFileSync(out,Buffer.concat(chunks));
  return c;
}
let c;
for(const lvl of ['light','balanced','strong']){
  c=await run('imgs.pdf','compress',{level:lvl},`/tmp/vt/comp_${lvl}.pdf`);
  const end=c.find(x=>x[0]==='end');
  ok(!!end,`compress ${lvl}: ${end?end[1]+' -> '+end[2]+' bytes':JSON.stringify(c.filter(x=>x[0]!=='progress').slice(0,2))}`);
}
c=await run('test.pdf','compress',{level:'balanced'},'/tmp/vt/comp_text.pdf');
ok(c.some(x=>x[0]==='noGain')||c.some(x=>x[0]==='end'),'text-only PDF handled: '+JSON.stringify(c.filter(x=>x[0]!=='progress').map(x=>x.slice(0,3))));
await b.close();server.close();})();
