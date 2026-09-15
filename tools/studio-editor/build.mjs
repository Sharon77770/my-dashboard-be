import {build} from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const destination=path.resolve(here,'../../src/main/resources/static/vendor');
await build({entryPoints:[path.join(here,'editor.js')],bundle:true,minify:true,outfile:path.join(destination,'studio-editor.js')});
const lock=JSON.parse(fs.readFileSync(path.join(here,'package-lock.json'),'utf8'));
const notices=[];
for(const directory of Object.keys(lock.packages).filter(key=>key.startsWith('node_modules/') && !key.includes('esbuild') && !lock.packages[key].dev)) {
  const folder=path.join(here,directory);
  const license=fs.readdirSync(folder).find(name=>/^licen[cs]e(?:\.txt|\.md)?$/i.test(name));
  if(!license)throw new Error('Missing license: '+directory);
  notices.push(directory+'\n'+fs.readFileSync(path.join(folder,license),'utf8'));
}
fs.writeFileSync(path.join(destination,'studio-editor.LICENSE.txt'),notices.join('\n\n---\n\n'));
