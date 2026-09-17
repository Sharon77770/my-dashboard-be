# Bundled browser clients

- xterm.js 6.0.0: https://github.com/xtermjs/xterm.js (MIT, xterm-LICENSE).
- @xterm/addon-fit 0.11.0: same project (MIT, addon-fit-LICENSE).
- guacamole-common-js 1.6.0: https://github.com/apache/guacamole-client/tree/1.6.0/guacamole-common-js (Apache-2.0, guacamole-LICENSE and guacamole-NOTICE).

Guacamole modules are concatenated with Namespace.js first. Dependencies are served locally; runtime CDN downloads are not used.

- Notebook editor: BlockNote 0.54.2, React/ReactDOM 19.2.0, Mantine 8.3.11. Exact transitive versions: `tools/notes-editor/package-lock.json`. Source/build: `tools/notes-editor/editor.jsx`, `build.mjs`. Notices and corresponding source links: `notes-editor.LICENSE.txt`.
