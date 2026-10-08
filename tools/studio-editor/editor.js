import * as monaco from 'monaco-editor/esm/vs/editor/editor.main.js';

// Serve workers from the same authenticated origin as the editor bundle.
const assets = new URL('.', document.currentScript?.src || new URL('vendor/studio-editor.js', document.baseURI));
self.MonacoEnvironment = {
  getWorkerUrl(_module, label) {
    const worker = ({json:'json',css:'css',scss:'css',less:'css',html:'html',handlebars:'html',razor:'html',javascript:'ts',typescript:'ts'})[label] || 'editor';
    return new URL(`studio-${worker}.worker.js`, assets).href;
  }
};

/** One model and view state per Studio document; models survive tab switches. */
window.WorkspaceCodeEditor = (parent, onChange) => {
  const models = new Map(), views = new Map();
  let active = '', loading = false;
  const editor = monaco.editor.create(parent, {
    model:null, automaticLayout:true, fontSize:13, minimap:{enabled:false},
    scrollBeyondLastLine:false, fixedOverflowWidgets:true, padding:{top:8},
    accessibilitySupport:'auto', theme:document.documentElement.dataset.theme === 'light' ? 'vs' : 'vs-dark'
  });
  const theme = new MutationObserver(() => monaco.editor.setTheme(document.documentElement.dataset.theme === 'light' ? 'vs' : 'vs-dark'));
  theme.observe(document.documentElement, {attributes:true, attributeFilter:['data-theme']});
  const changes = editor.onDidChangeModelContent(() => {if (!loading && active) onChange(editor.getValue());});
  const cursor = editor.onDidChangeCursorPosition(event => parent.dispatchEvent(new CustomEvent('studio-cursor', {bubbles:true, detail:event.position})));
  return {
    load(path, content) {
      if (active) views.set(active, editor.saveViewState());
      loading = true;
      try {
        active = path;
        if (!path) {editor.setModel(null); return;}
        let model = models.get(path);
        if (!model) {
          model = monaco.editor.createModel(content, undefined, monaco.Uri.from({scheme:'studio',path:'/' + path}));
          models.set(path, model);
        } else if (model.getValue() !== content) model.setValue(content);
        editor.setModel(model);
        if (views.has(path)) editor.restoreViewState(views.get(path));
      } finally {loading = false;}
    },
    close(path) {const model=models.get(path);if(editor.getModel()===model)editor.setModel(null);if(active===path)active='';model?.dispose();models.delete(path);views.delete(path);},
    reset() {editor.setModel(null);for(const model of models.values())model.dispose();models.clear();views.clear();active='';},
    selection() {const selection=editor.getSelection(),model=editor.getModel();return selection&&model?{content:model.getValueInRange(selection),fromLine:selection.startLineNumber,toLine:selection.endLineNumber}:null;},
    command(name) {return editor.getAction(({find:'actions.find',replace:'editor.action.startFindReplaceAction',goto:'editor.action.gotoLine'})[name])?.run();},
    goto(line,column=1) {editor.setPosition({lineNumber:line,column});editor.revealLineInCenter(line);editor.focus();},
    diagnostics() {return monaco.editor.getModelMarkers({}).filter(marker=>marker.resource.scheme==='studio').map(marker=>({path:marker.resource.path.slice(1),line:marker.startLineNumber,column:marker.startColumn,message:marker.message}));},
    focus() {editor.focus();},
    destroy() {theme.disconnect();cursor.dispose();changes.dispose();this.reset();editor.dispose();}
  };
};
