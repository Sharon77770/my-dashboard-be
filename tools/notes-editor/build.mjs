import {build} from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const destination=path.resolve(here,'../../src/main/resources/static/vendor');
await build({entryPoints:[path.join(here,'editor.jsx')],bundle:true,minify:true,format:'iife',conditions:['style','browser','import'],
 outfile:path.join(destination,'notes-editor.js'),define:{'process.env.NODE_ENV':'"production"'},loader:{'.woff2':'dataurl','.woff':'dataurl'}});
const lock=JSON.parse(fs.readFileSync(path.join(here,'package-lock.json'),'utf8'));
const notices=['Notebook editor dependencies. BlockNote is unmodified MPL-2.0 software. Corresponding source (including src/) is available in the exact npm packages linked below.'];
for(const [directory,info] of Object.entries(lock.packages)){
 if(!directory.startsWith('node_modules/')||info.dev)continue;
 const folder=path.join(here,directory);if(!fs.existsSync(folder))continue;
 const manifest=JSON.parse(fs.readFileSync(path.join(folder,'package.json'),'utf8'));
 const licenses=fs.readdirSync(folder).filter(name=>/^(licen[cs]e|copying|notice)(\.|$)/i.test(name)&&fs.statSync(path.join(folder,name)).isFile());
 notices.push(`${manifest.name}@${manifest.version}\nLicense: ${manifest.license||'See package'}\nSource: ${info.resolved||manifest.repository?.url||''}\n`+licenses.map(name=>fs.readFileSync(path.join(folder,name),'utf8')).join('\n'));
}
fs.writeFileSync(path.join(destination,'notes-editor.LICENSE.txt'),notices.join('\n\n---\n\n'));
