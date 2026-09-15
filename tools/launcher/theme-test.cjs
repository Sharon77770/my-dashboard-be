const fs=require('fs'),assert=require('node:assert/strict');
const path=require('path'),root=path.resolve(__dirname,'../..');const dir=path.join(root,'src/main/resources/static/css');
const text=fs.readFileSync(path.join(dir,'design-system.css'),'utf8');
const blocks=[...text.matchAll(/:root(?:\[data-theme=light\])?\s*\{([^}]+)\}/g)].map(match=>Object.fromEntries([...match[1].matchAll(/--([\w-]+):\s*(#[0-9a-f]{6})\s*[;}]/g)].map(match=>[match[1],match[2]])));
const lum=color=>{const rgb=color.slice(1).match(/../g).map(value=>parseInt(value,16)/255).map(value=>value<=.04045?value/12.92:((value+.055)/1.055)**2.4);return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;};
const ratio=(a,b)=>{const values=[lum(a),lum(b)].sort((a,b)=>b-a);return (values[0]+.05)/(values[1]+.05);};
for(const [index,theme] of blocks.entries()){
 const name=index?'light':'dark';let lowest=100;
 for(const bg of ['bg-app','bg-sidebar','bg-surface','bg-surface-hover','bg-elevated','accent-subtle'])for(const fg of ['text-primary','text-secondary','text-muted']){const contrast=ratio(theme[bg],theme[fg]);lowest=Math.min(lowest,contrast);assert.ok(contrast>=4.5,`${name} ${fg}/${bg} ${contrast.toFixed(2)}`);}
 for(const bg of ['accent-solid','accent-solid-hover'])assert.ok(ratio(theme['text-inverse'],theme[bg])>=4.5,`${name} primary button`);
 for(const fg of ['danger','warning','success','accent'])assert.ok(ratio(theme[fg],theme['bg-surface'])>=4.5,`${name} ${fg}`);
 for(const bg of ['bg-app','bg-surface','bg-surface-hover'])assert.ok(ratio(theme['border-strong'],theme[bg])>=3,`${name} control boundary ${bg}`);
 console.log(`${name}: text minimum ${lowest.toFixed(2)}:1, primary/state/control contrast passed`);
}
for(const name of fs.readdirSync(dir).filter(name=>name.endsWith('.css')&&name!=='design-system.css'))assert.ok(!/#[0-9a-f]{3,8}(?=[;,)\s}])/i.test(fs.readFileSync(path.join(dir,name),'utf8')),`${name} hardcoded color`);
console.log('PASS: semantic colors, both theme contrast and no feature-level color literals');
