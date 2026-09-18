const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'../..'),base=path.join(root,'src/main/resources');
const postcss=require('../ui/node_modules/postcss');
const sheet=postcss.parse(fs.readFileSync(path.join(base,'static/vendor/workspace-ui.css'),'utf8'));
function match(query,width){if(query.includes('prefers-reduced-motion'))return false;const max=/(?:max-width:\s*|width\s*<=\s*)([\d.]+)(px|rem)/.exec(query),min=/(?:min-width:\s*|width\s*>=\s*)([\d.]+)(px|rem)/.exec(query);const pixels=m=>Number(m[1])*(m[2]==='rem'?16:1);return (!max||width<=pixels(max))&&(!min||width>=pixels(min));}
// jsdom does not implement cascade layers: expand ordered layers for DOM regression checks.
function flatten(node,width){return (node.nodes||[]).map(rule=>rule.type==='atrule'?(rule.name==='layer'||rule.name==='media'&&match(rule.params,width)?flatten(rule,width):''):rule.type==='rule'&&!rule.nodes.some(n=>n.type==='rule'||n.type==='atrule')?rule.toString():'').join('\n');}
for(const width of [1440,1280,1024,768,700,390]){
 const dom=new JSDOM(fs.readFileSync(path.join(base,'templates/home.html'),'utf8'),{runScripts:'outside-only',url:'http://localhost'}),w=dom.window,d=w.document;
 const style=d.createElement('style');style.textContent=flatten(sheet,width);d.head.append(style);
 w.WorkspaceCodeEditor=()=>({load(){},focus(){}});w.eval(fs.readFileSync(path.join(base,'static/js/studio-panels.js'),'utf8'));w.eval(fs.readFileSync(path.join(base,'static/js/studio-codex.js'),'utf8'));w.eval(fs.readFileSync(path.join(base,'static/js/studio.js'),'utf8'));w.WorkspaceStudio.init({escape:String});
 const workbench=d.querySelector('.studio-workbench');workbench.hidden=false;
 if(width<=700){assert.equal(d.querySelector('#tabs'),null);assert.equal(w.getComputedStyle(d.querySelector('.os-tools')).display,'none');assert.equal(w.getComputedStyle(d.querySelector('#mobile-current-app')).display,'block');assert.equal(w.getComputedStyle(d.querySelector('.studio-editor')).display,'flex');assert.equal(w.getComputedStyle(d.querySelector('.studio-explorer')).display,'none');workbench.dataset.mobilePane='explorer';assert.equal(w.getComputedStyle(d.querySelector('.studio-editor')).display,'flex');assert.equal(w.getComputedStyle(d.querySelector('.studio-explorer')).display,'none');workbench.dataset.mobilePane='inspector';assert.equal(w.getComputedStyle(d.querySelector('.studio-inspector')).display,'none');}
 else{assert.equal(d.querySelector('#tabs'),null);assert.equal(w.getComputedStyle(d.querySelector('.os-tools')).display,'flex');assert.equal(w.getComputedStyle(workbench).display,'grid');assert.equal(w.getComputedStyle(d.querySelector('#mobile-current-app')).display,'block');}
 assert.equal(d.querySelector('#sidebar'),null);dom.window.close();console.log(`PASS ${width}px: CSS parsed, shell and IDE mode rules`);
}
console.log('These are CSS/DOM checks, not browser layout or touch rendering tests.');
