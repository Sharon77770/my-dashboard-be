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
window.confirm = () => true;
window.fetch = async (url, options = {}) => {
  const path = url.replace('/api/v1', '');
  requests.push({ path, method: options.method || 'GET', body: options.body && JSON.parse(options.body) });
  let result;
  if (path === '/services/one/context') result = { service: { id: 'one', name: 'Example', icon: 'server', environment: 'Production', description: '' }, resources: bindings, health: { state: 'UNKNOWN', signals: [] }, github: {}, runtime: {}, telemetry: Object.fromEntries(bindings.filter(binding => binding.type === 'TELEMETRY').map(binding => [binding.reference, { summary: { serviceName: 'Telemetry API', status: 'Receiving data', requestsToday: 12, errorRate: 0 } }])), activity: [] };
  else if (path === '/services/one/runtime') result = bindings.filter(binding => ['DEVICE', 'DOCKER_CONTAINER'].includes(binding.type)).map(binding => ({ resourceId: binding.id, type: binding.type, name: binding.reference, deviceId: binding.type === 'DEVICE' ? binding.reference : binding.deviceId, state: binding.type === 'DEVICE' ? 'ONLINE' : 'RUNNING', cpu: binding.type === 'DEVICE' ? 42 : null, memory: 36, disk: 18, image: binding.type === 'DEVICE' ? '' : 'example:latest', detail: 'Up 2 hours', checkedAt: 1 }));
  else if (path.endsWith('/logs')) result = { output: '<token>\nsecond line' };
  else if (path.endsWith('/actions') && options.method === 'POST') result = { output: '' };
  else if (path === '/workspace') result = { devices: [{ id: 'device-1', name: 'Server' }] };
  else if (path === '/devices/device-1/docker') result = { output: 'ID NAMES\n123 app-container' };
  else if (path === '/telemetry/services') result = [{ serviceId: 'telemetry-1', serviceName: 'Telemetry API', serviceType: 'Backend API' }, { serviceId: 'telemetry-2', serviceName: 'Worker', serviceType: 'Worker' }];
  else if (path === '/github/owners') result = [{ login: 'alice' }];
  else if (path === '/github/owners/alice/repositories') result = [{ nameWithOwner: 'alice/first' }, { nameWithOwner: 'alice/second' }];
  else if (path === '/services/one/resources' && options.method === 'POST') { result = { id: `binding-${bindings.length}`, ...JSON.parse(options.body) }; bindings.push(result); }
  else if (path.startsWith('/services/one/resources/') && options.method === 'DELETE') { const index = bindings.findIndex(binding => path.endsWith(`/${binding.id}`)); assert.notEqual(index, -1); bindings.splice(index, 1); return { ok: true, status: 204 }; }
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

  assert.match(document.querySelector('#service-runtime-list').textContent, /CPU 42%/);
  document.querySelector('[data-service-logs]').click();
  await tick();
  assert.equal(document.querySelector('#service-runtime-log-output').textContent, '<token>\nsecond line');

  assert.equal(document.querySelector('#service-runtime-log-output').querySelector('token'), null);
  document.querySelector('[data-service-action="restart"]').click();
  await tick();
  assert.deepEqual(requests.find(request => request.path.endsWith('/actions')).body, { action: 'restart' });
  document.querySelector('[data-service-tab="Overview"]').click();
  document.querySelector('.service-actions [data-service-logs]').click();
  await tick();
  assert.equal(document.querySelector('[data-service-tab="Runtime"]').getAttribute('aria-current'), 'true');
  assert.equal(document.querySelector('#service-runtime-log-output').textContent, '<token>\nsecond line');

  document.querySelector('[data-service-tab="Telemetry"]').click();
  await tick();
  const telemetryForm = document.querySelector('#service-bind-telemetry');
  assert.equal(telemetryForm.querySelector('[name=reference] option:nth-child(2)').textContent, 'Telemetry API · Backend API');
  telemetryForm.querySelector('[name=reference]').value = 'telemetry-1';
  submit(telemetryForm);
  await tick();
  assert.deepEqual(requests.find(request => request.body?.type === 'TELEMETRY').body, { type: 'TELEMETRY', reference: 'telemetry-1', deviceId: '', label: '' });
  assert.match(document.querySelector('.service-telemetry-item').textContent, /오늘 요청 12/);
  assert.deepEqual([...document.querySelectorAll('#service-bind-telemetry [name=reference] option')].map(option => option.value), ['', 'telemetry-2']);
  document.querySelector('.service-telemetry-item [data-service-unbind]').click();
  await tick();
  assert.equal(document.querySelectorAll('.service-telemetry-item').length, 0);

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
