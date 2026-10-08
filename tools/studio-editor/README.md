# Editor bundle

Monaco Editor 0.52.2 is built into same-origin static assets. Node is a build tool only and is not needed on the dashboard or SSH runtime. `editor.js` preserves one model and view state per open Studio file. Closing a tab or changing projects disposes its models.

```sh
cd tools/studio-editor
npm ci --ignore-scripts --no-audit --no-fund
node build.mjs
node test.cjs
```

Commit the source, lock file, `src/main/resources/static/vendor/studio-editor.js`, `studio-editor.css`, `studio-*.worker.js`, `studio-codicon-*.ttf` and generated license notices together. Workers cover editor, JSON, CSS, HTML and JS/TS and never load a CDN. This follows the [Monaco ESM worker contract](https://github.com/microsoft/monaco-editor/blob/main/docs/integrate-esm.md).

`test.cjs` verifies Studio workflows in jsdom and mounts the real Monaco bundle. `monaco-dom.cjs` supplies synthetic geometry and runs the bundled worker code in Node threads; it does **not** prove browser layout, browser worker loading, keyboard input or SSH/API acceptance. Expected model-disposal cancellation rejections are ignored; other rejected promises fail the test.

From the repository root, `node tools/studio-editor/workbench-test.cjs` checks retained embedded terminal clients and `node tools/studio-editor/api-test.cjs` checks cURL conversion. The Codex chat regression also verifies the runtime-observation attachment contract.

`acceptance.cjs` targets an explicitly prepared **isolated** Linux dashboard/Chromium/SSH fixture. It creates a fresh repository and launches a loopback HTTP server. Configure `STUDIO_TEST_URL`, `STUDIO_TEST_ENV` (untracked generated test credentials), optional `STUDIO_TEST_SSH=true`, and `STUDIO_TEST_CODEX=true` only when the fixture has an authorized Codex login. It verifies actual PTY → ports → Chromium → HTTP API → file save → failing tests → Codex edit → restart → passing tests/API/browser → git diff. Without `STUDIO_TEST_CODEX`, it explicitly reports that the fixture correction came from a save API, not Codex. This exercises authenticated backend/WebSocket paths; it does not replace browser UI acceptance. Stop the disposable fixtures afterwards.
