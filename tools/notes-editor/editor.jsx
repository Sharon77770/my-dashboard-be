import React from 'react';
import {createRoot} from 'react-dom/client';
import {BlockNoteEditor,BlockNoteSchema,defaultBlockSpecs} from '@blocknote/core';
import {ko} from '@blocknote/core/locales';
import {BlockNoteView} from '@blocknote/mantine';
import '@blocknote/mantine/style.css';

// Keep the server contract and slash menu aligned: the notebook accepts image attachments,
// not arbitrary executables, audio/video uploads or third-party embeds.
const {audio,video,file,...blockSpecs}=defaultBlockSpecs;
const schema=BlockNoteSchema.create({blockSpecs});
function create(blocks,upload){
 return BlockNoteEditor.create({schema,dictionary:ko,initialContent:blocks.length?blocks:undefined,
  uploadFile:upload,resolveFileUrl:url=>url,
 });
}
window.NotesBlockEditor={
 mount(host,{blocks,onChange,upload,onError}){
  const editor=create(blocks,async file=>{try{return await upload(file);}catch(error){onError(error);throw error;}});
  const root=createRoot(host);
  const render=()=>root.render(<BlockNoteView editor={editor} theme={document.documentElement.dataset.theme==='light'?'light':'dark'}/>);
  render();
  const unsubscribe=editor.onChange(onChange);
  const observer=new MutationObserver(render);observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
  return {
   blocks:()=>JSON.parse(JSON.stringify(editor.document)),
   markdown:()=>editor.blocksToMarkdownLossy(editor.document),
   importMarkdown:text=>{const blocks=editor.tryParseMarkdownToBlocks(text);editor.replaceBlocks(editor.document,blocks.length?blocks:[{type:'paragraph'}]);},
   destroy(){observer.disconnect();unsubscribe();root.unmount();},
  };
 },
};
