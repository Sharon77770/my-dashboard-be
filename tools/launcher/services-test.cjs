const assert = require('node:assert/strict');
const fs = require('node:fs');
const { JSDOM } = require('jsdom');

const dom = new JSDOM('<meta name="csrf-header" content="X-CSRF-TOKEN"><meta name="csrf-token" content="test"><section id="services"><div class="page-head"></div><div id="services-content"></div></section>', { runScripts: 'outside-only', url: 'http://localhost' });
const { window } = dom;
const { document } = window;
const bindings = [];
const requests = [];
window.WorkspaceUI = { escape: value => String(value ?? '').replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;'), icon: () => '', emptyState: () => '', progress: () => '' };
window.alert = error => { throw new Error(error); };
window.fetch = async (url, options = {}) => {
  const path = url.replace('/api/v1', '');
  requests.push({ path, method: options.method || 'GET', body: options.body && JSON.parse(options.body) });
  let result;
  if (path === '/services/one/context') result = { service: { id: 'one', name: 'Example', icon: 'server', environment: 'Production', description: '' }, resources: bindings, health: { state: 'UNKNOWN', signals: [] }, github: {}, runtime: {}, telemetry: {}, activity: [] };
  else if (path === '/workspace') result = { devices: [{ id: 'device-1', name: 'Server' }] };
  else if (path === '/devices/device-1/docker') result = { output: 'ID NAMES\n123 app-container' };
  else if (path === '/github/owners') result = [{ login: 'alice' }];
  else if (path === '/github/owners/alice/repositories') result = [{ nameWithOwner: 'alice/first' }, { nameWithOwner: 'alice/second' }];
  else if (path === '/services/one/resources' && options.method === 'POST') { result = { id: `binding-${bindings.length}`, ...JSON.parse(options.body) }; bindings.push(result); }
  else throw new Error(`Unexpected API call: ${path}`);
  return { ok: true, status: 200, json: async () => result };
};
window.eval(fs.readFileSync('src/main/resources/static/js/services.js', 'utf8'));
const tick = () => new Promise(resolve => setTimeout(resolve, 20));
const submit = form => form.dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));

(async () => {
  await window.WorkspaceServices.openService('one');
  document.querySelector('[data-service-tab="Runtime"]').click();
  await tick();
  const deviceForm = document.querySelector('#service-bind-device');
  assert.equal(deviceForm.querySelector('[name=reference]').value, '');
  deviceForm.querySelector('[name=reference]').value = 'device-1';
  submit(deviceForm);
  await tick();
  assert.deepEqual(requests.find(request => request.body?.type === 'DEVICE').body, { type: 'DEVICE', reference: 'device-1', deviceId: '', label: '' });

  const containerForm = document.querySelector('#service-bind-container');
  containerForm.querySelector('[name=deviceId]').value = 'device-1';
  containerForm.querySelector('[name=deviceId]').dispatchEvent(new window.Event('change', { bubbles: true }));
  await tick();
  assert.equal(containerForm.querySelector('[name=reference]').value, 'app-container');
  submit(containerForm);
  await tick();
  assert.deepEqual(requests.find(request => request.body?.type === 'DOCKER_CONTAINER').body, { type: 'DOCKER_CONTAINER', reference: 'app-container', deviceId: 'device-1', label: '' });

  document.querySelector('[data-service-tab="Settings"]').click();
  await tick();
  const search = document.querySelector('#service-repository-search');
  assert.ok(search);
  search.value = 'second';
  search.dispatchEvent(new window.Event('input', { bubbles: true }));
  assert.deepEqual([...document.querySelectorAll('#service-bind [name=reference] option')].map(option => option.value), ['alice/second']);
  submit(document.querySelector('#service-bind'));
  await tick();
  assert.equal(requests.find(request => request.body?.type === 'GITHUB_REPOSITORY').body.reference, 'alice/second');
  console.log('services UI test passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
