# Editor bundle

CodeMirror 6 is built into a same-origin static asset; Node is a build tool only and is not needed on the dashboard or SSH runtime.

```sh
cd tools/studio-editor
npm ci --ignore-scripts --no-audit --no-fund
node build.mjs
node test.cjs
```

Commit the source, lock file, `src/main/resources/static/vendor/studio-editor.js` and its generated license notices together.
