const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const assert = require('node:assert/strict');

const html = fs.readFileSync('src/main/resources/templates/home.html', 'utf8');
const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost' });
const { window } = dom;
const document = window.document;
const requests = [];
let authenticated = false;

window.HTMLDialogElement.prototype.close = function () { this.open = false; };
window.setInterval = () => 0;
window.matchMedia = () => ({ matches: false, addEventListener() {} });
window.ResizeObserver = class { observe() {} disconnect() {} };
window.workspaceInitial = {
  devices: [{ id: 'local', name: 'Server', host: 'localhost', rootPath: '/srv/dashboard', remoteProtocol: 'NONE' }],
  applications: [], clips: [], bookmarks: [], activity: [], tabs: [],
  preferences: { theme: 'dark', compact: false, terminalFont: 13, clipMinutes: 60 },
  browserSettings: { mode: 'CLIENT' }
};
window.fetch = async (url, options = {}) => {
  requests.push({ url, method: options.method || 'GET' });
  let result = [];
  if (url === '/api/v1/github/status') result = { authenticated };
  if (url === '/api/v1/workspace') result = window.workspaceInitial;
  if (url === '/api/v1/studio/jobs' && options.method === 'POST') result = { id: 'login', state: 'RUNNING' };
  if (url === '/api/v1/studio/jobs/login') {
    authenticated = true;
    result = { id: 'login', state: 'SUCCEEDED', events: [
      { url: 'https://github.com/login/device' }, { code: 'ABCD-1234' }
    ] };
  }
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => result };
};

const scripts = [
  'ui.js', 'launcher/app-registry.js', 'launcher/grid-model.js',
  'launcher/persistence.js', 'launcher/widget-registry.js',
  'launcher/interactions.js', 'launcher/launcher.js', 'workspace.js', 'github.js'
];
for (const file of scripts) window.eval(fs.readFileSync('src/main/resources/static/js/' + file, 'utf8'));

(async () => {
  window.dispatchEvent(new window.Event('DOMContentLoaded'));
  await window.WorkspaceGithub.open('github');
  assert.match(document.querySelector('#github-status').textContent, /로그인이 필요/);
  document.querySelector('#github-login').click();
  await new Promise(resolve => setTimeout(resolve, 800));
  assert.ok(requests.some(request => request.url === '/api/v1/studio/jobs' && request.method === 'POST'));
  assert.match(document.querySelector('#github-status').textContent, /연결됨/);
  assert.equal(document.querySelector('#github-auth-code').textContent, 'ABCD-1234');
  console.log('PASS GitHub UI: deferred initialization and login job');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
}).finally(() => dom.window.close());
