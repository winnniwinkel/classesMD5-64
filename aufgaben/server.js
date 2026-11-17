// Minimaler Server ohne Abhängigkeiten: liefert die App aus und speichert die Daten als JSON-Datei.
const http=require("http"),fs=require("fs"),path=require("path");
const PORT=process.env.PORT||8080,DATA=process.env.DATA_FILE||"/data/aufgaben.json";
const USER=process.env.APP_USER||"",PASS=process.env.APP_PASSWORD||"";
let db={rev:0,data:{tasks:[]}};
try{db=JSON.parse(fs.readFileSync(DATA,"utf8"))}catch{}
function persist(){fs.mkdirSync(path.dirname(DATA),{recursive:true});fs.writeFileSync(DATA+".tmp",JSON.stringify(db));fs.renameSync(DATA+".tmp",DATA)}
const send=(res,code,obj)=>{res.writeHead(code,{"Content-Type":"application/json","Cache-Control":"no-store"});res.end(JSON.stringify(obj))};
http.createServer((req,res)=>{
  if(PASS){const a=(req.headers.authorization||"").split(" ")[1]||"";
    if(Buffer.from(a,"base64").toString()!==`${USER}:${PASS}`){res.writeHead(401,{"WWW-Authenticate":'Basic realm="Aufgaben"'});return res.end("Login erforderlich")}}
  const url=req.url.split("?")[0];
  if(url==="/api/data"&&req.method==="GET")return send(res,200,db);
  if(url==="/api/data"&&req.method==="PUT"){
    let b="";req.on("data",c=>{b+=c;if(b.length>20e6)req.destroy()});
    req.on("end",()=>{try{const{rev,data}=JSON.parse(b);
      if(!data||!Array.isArray(data.tasks))return send(res,400,{error:"bad"});
      if(rev!==db.rev)return send(res,409,db);
      db={rev:db.rev+1,data};persist();send(res,200,{rev:db.rev})}catch{send(res,400,{error:"bad"})}});return}
  if(url==="/"||url==="/index.html"){res.writeHead(200,{"Content-Type":"text/html; charset=utf-8"});return res.end(fs.readFileSync(path.join(__dirname,"index.html")))}
  res.writeHead(404);res.end();
}).listen(PORT,()=>console.log("Aufgaben läuft auf Port "+PORT));
