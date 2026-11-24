const cacheName = 'aether-android-fonts-v1';
const validKey = k => /^[a-z0-9][a-z0-9-]{0,90}-[1-9]00$/.test(k);
let dbPromise;
function timeoutSignal(ms){const c=new AbortController();setTimeout(()=>c.abort(),ms);return c.signal;}
function db(){return dbPromise ??= new Promise((resolve,reject)=>{const r=indexedDB.open(cacheName,1);r.onupgradeneeded=()=>r.result.createObjectStore('faces');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});}
async function read(key){const d=await db();return new Promise((res,rej)=>{const r=d.transaction('faces').objectStore('faces').get(key);r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error);});}
async function write(key, bytes){const d=await db();return new Promise((res,rej)=>{const t=d.transaction('faces','readwrite');t.objectStore('faces').put(bytes,key);t.oncomplete=()=>res();t.onerror=()=>rej(t.error);t.onabort=()=>rej(t.error);});}
let catalogPromise;
export function catalog(){return catalogPromise ??= fetch('assets/font-catalog.json').then(r=>{if(!r.ok)throw Error('Font catalog unavailable');return r.json();});}
export async function cacheFont(id,weight){const row=(await catalog()).find(x=>x.id===id);if(!row||!row.weights.includes(weight))throw Error('Unknown font or weight');const key=`${id}-${weight}`;if(!validKey(key))throw Error('Invalid font key');if(await read(key))return key;
const cssURL=new URL('https://fonts.googleapis.com/css2');cssURL.searchParams.set('family',`${row.family}:wght@${weight}`);cssURL.searchParams.set('display','swap');
const response=await fetch(cssURL,{signal:timeoutSignal(20000),credentials:'omit'});if(!response.ok)throw Error('Google Fonts request failed');const css=await response.text();const urls=[...css.matchAll(/url\(\s*['"]?(https:\/\/fonts\.gstatic\.com\/[^)'"\s]+)['"]?\s*\)/g)].map(m=>m[1]);if(!urls.length)throw Error('Font source unavailable');const font=await fetch(urls.at(-1),{signal:timeoutSignal(20000),credentials:'omit'});if(!font.ok)throw Error('Font download failed');const bytes=await font.arrayBuffer();if(bytes.byteLength>8*1024*1024||bytes.byteLength<4)throw Error('Invalid font size');const magic=String.fromCharCode(...new Uint8Array(bytes,0,4));if(!['wOF2','wOFF','OTTO','\u0000\u0001\u0000\u0000'].includes(magic))throw Error('Invalid font data');await new FontFace('AetherValidation',bytes,{weight:String(weight)}).load();await write(key,bytes);return key;}
export async function fontBytes(key){if(!validKey(key))throw Error('Invalid font key');const bytes=await read(key);if(!bytes)throw Error('Font not cached on this device');return bytes;}
