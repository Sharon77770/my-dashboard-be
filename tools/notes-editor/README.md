# Notebook editor bundle

BlockNote 0.54.2 provides the Korean block editor. The surrounding dashboard remains Spring Boot/Thymeleaf and plain JavaScript. React is confined to the editor mount. No Node runtime or external CDN is needed by the deployed app.

```sh
cd tools/notes-editor
npm ci --ignore-scripts --no-audit --no-fund
npm run build
npm test
```

Commit source, package-lock.json and all three generated `src/main/resources/static/vendor/notes-editor.*` assets together. `build.mjs` includes dependency license notices and links to the exact corresponding source packages, including BlockNote's MPL-2.0 source. No BlockNote source files are modified.

`window.NotesBlockEditor.mount(host, {blocks,onChange,upload,onError})` returns `blocks()`, `markdown()`, `importMarkdown(text)`, and `destroy()`. A mount is scoped to one document. Cleanup unregisters change/theme observers and unmounts React. Upload callbacks return a same-origin, authenticated image URL. The block schema excludes arbitrary files, audio and video to match the server contract.

`npm test` uses the real production bundle in jsdom; it covers all eight templates, actual contenteditable rendering, tables, Markdown import/export, change events and cleanup. Browser layout/drag/touch must be checked separately.
