const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {JSDOM}=require('jsdom'),CSSOM=require('rrweb-cssom');
const root=path.resolve(__dirname,'../..'),base=path.join(root,'src/main/resources');
const styles=['design-system','workspace','launcher','planner','studio','studio-codex','os-shell'].map(name=>CSSOM.parse(fs.readFileSync(path.join(base,'static/css',name+'.css'),'utf8')));
function match(query,width){if(query.includes('prefers-reduced-motion'))return false;const max=/max-width:\s*(\d+)px/.exec(query),min=/min-width:\s*(\d+)px/.exec(query);return (!max||width<=Number(max[1]))&&(!min||width>=Number(min[1]));}
function flatten(rules,width){return [...rules].map(rule=>rule.media?match(rule.media.mediaText,width)?flatten(rule.cssRules,width):'':rule.cssText).join('\n');}
for(const width of [1440,1280,1024,768,700,390]){
 const dom=new JSDOM(fs.readFileSync(path.join(base,'templates/home.html'),'utf8'),{runScripts:'outside-only',url:'http://localhost'}),w=dom.window,d=w.document;
 const style=d.createElement('style');style.textContent=styles.map(sheet=>flatten(sheet.cssRules,width)).join('\n');d.head.append(style);
 w.WorkspaceCodeEditor=()=>({load(){},focus(){}});w.eval(fs.readFileSync(path.join(base,'static/js/studio-panels.js'),'utf8'));w.eval(fs.readFileSync(path.join(base,'static/js/studio-codex.js'),'utf8'));w.eval(fs.readFileSync(path.join(base,'static/js/studio.js'),'utf8'));w.WorkspaceStudio.init({escape:String});
 const workbench=d.querySelector('.studio-workbench');workbench.hidden=false;
 if(width<=700){assert.equal(d.querySelector('#tabs'),null);assert.equal(w.getComputedStyle(d.querySelector('.os-tools')).display,'none');assert.equal(w.getComputedStyle(d.querySelector('#mobile-current-app')).display,'block');assert.equal(w.getComputedStyle(d.querySelector('.studio-editor')).display,'flex');assert.equal(w.getComputedStyle(d.querySelector('.studio-explorer')).display,'none');workbench.dataset.mobilePane='explorer';assert.equal(w.getComputedStyle(d.querySelector('.studio-editor')).display,'none');assert.equal(w.getComputedStyle(d.querySelector('.studio-explorer')).display,'flex');workbench.dataset.mobilePane='inspector';assert.equal(w.getComputedStyle(d.querySelector('.studio-inspector')).display,'flex');}
 else{assert.equal(d.querySelector('#tabs'),null);assert.equal(w.getComputedStyle(d.querySelector('.os-tools')).display,'flex');assert.equal(w.getComputedStyle(workbench).display,'grid');assert.equal(w.getComputedStyle(d.querySelector('#mobile-current-app')).display,'block');}
 assert.equal(d.querySelector('#sidebar'),null);dom.window.close();console.log(`PASS ${width}px: CSS parsed, shell and IDE mode rules`);
}
console.log('These are CSS/DOM checks, not browser layout or touch rendering tests.');
