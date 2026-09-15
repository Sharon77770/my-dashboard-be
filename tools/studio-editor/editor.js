import {basicSetup, EditorView} from 'codemirror';
import {EditorState} from '@codemirror/state';
import {javascript} from '@codemirror/lang-javascript';
import {python} from '@codemirror/lang-python';
import {html} from '@codemirror/lang-html';
import {css} from '@codemirror/lang-css';
import {java} from '@codemirror/lang-java';
import {json} from '@codemirror/lang-json';
import {markdown} from '@codemirror/lang-markdown';
import {HighlightStyle, syntaxHighlighting} from '@codemirror/language';
import {tags} from '@lezer/highlight';
const colors=syntaxHighlighting(HighlightStyle.define([
  {tag:tags.keyword,color:'var(--studio-keyword)'},
  {tag:[tags.string,tags.special(tags.string)],color:'var(--studio-string)'},
  {tag:[tags.number,tags.bool,tags.null],color:'var(--studio-number)'},
  {tag:tags.comment,color:'var(--studio-comment)',fontStyle:'italic'},
  {tag:[tags.function(tags.variableName),tags.tagName],color:'var(--studio-function)'}
]));
window.WorkspaceCodeEditor = (parent, onChange) => {
  const language = path => /\.[cm]?[jt]sx?$/.test(path) ? javascript({typescript:/\.tsx?$/.test(path),jsx:/x$/.test(path)}) : path.endsWith('.py') ? python() : /\.html?$/.test(path) ? html() : path.endsWith('.css') ? css() : path.endsWith('.java') ? java() : path.endsWith('.json') ? json() : path.endsWith('.md') ? markdown() : [];
  const view = new EditorView({parent});
  return {
    load(path, content) { view.setState(EditorState.create({doc:content, extensions:[EditorState.readOnly.of(!path),EditorView.editable.of(Boolean(path)),basicSetup,language(path),colors,EditorView.updateListener.of(update=>{if(update.docChanged)onChange(update.state.doc.toString());}),EditorView.theme({'&':{height:'100%',fontSize:'13px',backgroundColor:'transparent',color:'inherit'},'.cm-scroller':{fontFamily:'Consolas, monospace',overflow:'auto'},'.cm-gutters':{backgroundColor:'transparent',color:'var(--text-muted)',borderRight:'1px solid var(--border-subtle)'},'.cm-content':{caretColor:'var(--accent)'},'.cm-activeLine':{backgroundColor:'var(--bg-surface-hover)'},'.cm-activeLineGutter':{backgroundColor:'var(--bg-surface-hover)'},'.cm-selectionBackground':{backgroundColor:'var(--accent-subtle) !important'},'.cm-panels, .cm-tooltip':{backgroundColor:'var(--bg-elevated)',color:'var(--text-primary)',border:'1px solid var(--border-default)'},'.cm-textfield':{backgroundColor:'var(--bg-surface)',color:'var(--text-primary)',border:'1px solid var(--border-default)'},'.cm-button':{backgroundImage:'none',backgroundColor:'var(--bg-surface)',color:'var(--text-primary)',border:'1px solid var(--border-default)'},'.cm-tooltip-autocomplete ul li[aria-selected]':{backgroundColor:'var(--accent-subtle)',color:'var(--text-primary)'}})]})); },
    selection(){const {from,to}=view.state.selection.main;return {content:view.state.sliceDoc(from,to),fromLine:view.state.doc.lineAt(from).number,toLine:view.state.doc.lineAt(to).number};},
    focus(){view.focus();}, destroy(){view.destroy();}
  };
};
