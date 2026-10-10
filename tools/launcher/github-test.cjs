const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const assert = require('node:assert/strict');

const html = fs.readFileSync('src/main/resources/templates/home.html', 'utf8');
const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost' });
const { window } = dom;
const document = window.document;
const requests = [];
let authenticated = false;
let approvals = [];

window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
window.HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new window.Event('close')); };
window.setInterval = () => 0;
let mobile = false;
window.matchMedia = () => ({ get matches() { return mobile; }, addEventListener() {} });
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
  if (url === '/api/v1/github/owners') result = [
    {login:'alice',type:'USER'}, {login:'example-org',type:'ORGANIZATION'}
  ];
  if (url === '/api/v1/github/approvals') result = approvals;
  if (url.startsWith('/api/v1/github/approvals/') && options.method === 'POST') {
    approvals = approvals.filter(item => !url.endsWith('/' + item.id));
    result = {approved:true};
  }
  if (url === '/api/v1/github/owners/alice/repositories') result = [
    {nameWithOwner:'alice/sample', description:'Personal', isPrivate:false, isArchived:false, isFork:false, updatedAt:'2026-01-01'}
  ];
  if (url === '/api/v1/github/owners/example-org/repositories') result = [
    {nameWithOwner:'example-org/service', description:'Service', isPrivate:true, isArchived:false, isFork:false, updatedAt:'2026-01-02'}
  ];
  if (url === '/api/v1/github/owners/alice/overview') result = {repositories:[],openIssues:[],openPullRequests:[]};
  if (url === '/api/v1/github/owners/example-org/overview') result = {repositories:[],openIssues:[],openPullRequests:[]};
  if (url === '/api/v1/github/repositories/context?repository=example-org%2Fservice') result = {
    repository:{fullName:'example-org/service'},openIssues:[],openPullRequests:[],recentWorkflowRuns:[]
  };
  if (url.startsWith('/api/v1/github/owners/example-org/issues?')) result = [
    {number:7,title:'Fix CI',state:'open',url:'https://github.com/example-org/service/issues/7',updatedAt:'2026-01-02'}
  ];
  if (url === '/api/v1/github/issues/detail?repository=example-org%2Fservice&number=7') result = {
    number:7,title:'Fix CI',state:'open',body:'Details',assignees:[],labels:[]
  };
  if (url === '/api/v1/github/actions/workflows?repository=example-org%2Fservice') result = [
    {id:11,name:'CI',state:'active',url:'https://github.com/example-org/service/actions/workflows/ci.yml'}
  ];
  if (url === '/api/v1/github/actions/runs?repository=example-org%2Fservice') result = [
    {id:99,name:'CI',conclusion:'failure',branch:'main',createdAt:'2026-01-02',url:'https://github.com/example-org/service/actions/runs/99'}
  ];
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
  'ui.js', 'live-dom.js', 'launcher/app-registry.js', 'launcher/grid-model.js',
  'launcher/persistence.js', 'launcher/widget-registry.js',
  'launcher/interactions.js', 'launcher/launcher.js', 'authentication-browser.js', 'workspace.js', 'drawers.js', 'assistant-markdown.js', 'github.js'
];
for (const file of scripts) window.eval(fs.readFileSync('src/main/resources/static/js/' + file, 'utf8'));

async function settled(){for(let i=0;i<100;i++){await new Promise(resolve=>setTimeout(resolve,10));if(document.documentElement.dataset.loading!=='true')return;}throw Error('foreground loading did not finish');}
(async () => {
  window.dispatchEvent(new window.Event('DOMContentLoaded'));
  await window.WorkspaceGithub.open('github');
  await settled();
  assert.match(document.querySelector('#github-status').textContent, /로그인 필요/);
  document.querySelector('#github-login').click();
  await new Promise(resolve => setTimeout(resolve, 800));
  assert.ok(requests.some(request => request.url === '/api/v1/studio/jobs' && request.method === 'POST'));
  assert.match(document.querySelector('#github-status').textContent, /연결됨/);
  assert.equal(document.querySelector('#github-auth-code').textContent, 'ABCD-1234');
  assert.equal([...document.querySelectorAll('#github-owner option')].filter(option=>option.value).length, 2);
  assert.equal(document.querySelectorAll('#github-auth .authentication-browser-open').length, 1);
  const scope = document.querySelector('#github-owner');
  scope.value = 'example-org'; scope.dispatchEvent(new window.Event('change', {bubbles:true}));
  await settled();
  assert.ok(document.querySelector('[data-repository="example-org/service"]'));
  document.querySelector('[data-repository="example-org/service"]').click();
  await settled();
  assert.equal(document.querySelector('#github-selected').textContent, 'example-org/service');
  document.querySelector('[data-tab="issues"]').click();
  await settled();
  document.querySelector('[data-create-kind="issue"]').click();
  assert.ok(document.querySelector('form[data-github-create="issue"]'));
  document.querySelector('[data-detail-kind="issue"]').click();
  await settled();
  const response = document.querySelector('[data-github-response="issue"]');
  assert.ok(response);
  response.querySelector('textarea').value = 'Investigating';
  response.dispatchEvent(new window.Event('submit', {bubbles:true,cancelable:true}));
  await settled();
  assert.ok(requests.some(request => request.method === 'POST'
    && request.url === '/api/v1/github/issues/7/comments?repository=example-org%2Fservice'));
  document.querySelector('[data-tab="actions"]').click();
  await settled();
  document.querySelector('[data-dispatch-workflow="11"]').click();
  const dispatch = document.querySelector('[data-github-dispatch]');
  dispatch.querySelector('input').value = 'main';
  dispatch.dispatchEvent(new window.Event('submit', {bubbles:true,cancelable:true}));
  await settled();
  assert.ok(requests.some(request => request.method === 'POST'
    && request.url === '/api/v1/github/actions/workflows/11/dispatches?repository=example-org%2Fservice'));
  approvals = [{id:'delete-fixture',operation:'DELETE_RELEASE',repository:'example-org/service',number:5000000000}];
  window.confirm = () => false;
  await window.WorkspaceGithub.open('github');
  const approval = document.querySelector('[data-approval="delete-fixture"]');
  assert.match(approval.parentElement.textContent, /릴리스 #5000000000/);
  approval.click();
  await settled();
  assert.ok(!requests.some(request => request.url.endsWith('/approvals/delete-fixture')));
  window.confirm = () => true;
  approval.click();
  await settled();
  assert.ok(requests.some(request => request.url.endsWith('/approvals/delete-fixture') && request.method === 'POST'));
  mobile = true;
  document.querySelector('.github-scope-trigger').click();
  assert.ok(document.querySelector('.ui-side-drawer[open] .github-scope'));
  document.querySelector('.ui-side-drawer [data-tab="issues"]').click();
  await settled();
  assert.equal(document.querySelector('.ui-side-drawer'), null);
  assert.equal(document.querySelector('[data-tab="issues"]').getAttribute('aria-current'), 'true');
  console.log('PASS GitHub UI: login, Owner switch, issue comment, workflow dispatch and mobile browse drawer');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
}).finally(() => {window.dispatchEvent(new window.Event('pagehide'));dom.window.close();});
