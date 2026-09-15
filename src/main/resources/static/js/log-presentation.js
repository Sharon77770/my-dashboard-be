'use strict';
/** Bounded, text-only log presentation. Formatting never mutates the received buffer. */
window.LogPresentation = (() => {
  const limit = 400000;

  function prettyJson(source) {
    const trimmed = source.trim();
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return source;
    try { JSON.parse(trimmed); } catch { return source; }
    // Keep original number/string lexemes: JSON.stringify would round large identifiers.
    const parts = [];
    let depth = 0, quoted = false, escaped = false, size = 0;
    for (const character of trimmed) {
      let part = character;
      if (quoted) {
        if (escaped) escaped = false;
        else if (character === '\\') escaped = true;
        else if (character === '"') quoted = false;
      } else if (character === '"') quoted = true;
      else if (character === '{' || character === '[') {
        if (++depth > 64) return source;
        part += '\n' + '  '.repeat(depth);
      } else if (character === '}' || character === ']') part = '\n' + '  '.repeat(--depth) + character;
      else if (character === ',') part += '\n' + '  '.repeat(depth);
      else if (character === ':') part += ' ';
      else if (/\s/.test(character)) continue;
      size += part.length;
      if (size > limit) return source;
      parts.push(part);
    }
    return parts.join('').replace(/\{\n\s*\}/g,'{}').replace(/\[\n\s*\]/g,'[]');
  }

  function format(source, mode) {
    if (mode !== 'json') return source;
    const entire = prettyJson(source);
    if (entire !== source) return entire;
    let size = 0;
    const lines = source.split('\n').map(line => {
      // Docker --timestamps prefixes each JSON/JSONL record with an RFC3339 timestamp.
      const prefix = line.match(/^\d{4}-\d{2}-\d{2}T\S+\s+/)?.[0] || '';
      const formatted = prefix + prettyJson(line.slice(prefix.length));
      size += formatted.length + 1;
      return size <= limit ? formatted : line;
    });
    return lines.join('\n').length <= limit ? lines.join('\n') : source;
  }

  function render(element, source, options) {
    const text = format(source, options.format);
    element.dataset.wrap = String(options.wrap);
    element.dataset.frame = String(options.frame);
    if (options.color === 'none') { element.textContent = text; return; }
    const pattern = options.color === 'levels'
      ? /\b(?:TRACE|DEBUG|INFO|NOTICE|WARN|WARNING|ERROR|FATAL|CRITICAL|SUCCESS)\b/gi
      : /"(?:\\.|[^"\\])*"|\b(?:true|false|null)\b|-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b|\b(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SUCCESS)\b/g;
    const fragment = document.createDocumentFragment();
    let offset = 0, count = 0;
    for (const match of text.matchAll(pattern)) {
      if (++count > 6000) break;
      fragment.append(document.createTextNode(text.slice(offset, match.index)));
      const span = document.createElement('span');
      const token = match[0];
      let kind = 'number';
      if (token.startsWith('"')) kind = /^\s*:/.test(text.slice(match.index + token.length)) ? 'key' : 'string';
      else if (/^(true|false|null)$/.test(token)) kind = 'literal';
      else if (/^[a-z]+$/i.test(token)) {
        kind = /^(error|fatal|critical)$/i.test(token) ? 'error' : /^(warn|warning)$/i.test(token) ? 'warning' : /^(success)$/i.test(token) ? 'success' : 'info';
      }
      span.className = 'log-token-' + kind;
      span.textContent = token;
      fragment.append(span);
      offset = match.index + token.length;
    }
    fragment.append(document.createTextNode(text.slice(offset)));
    element.replaceChildren(fragment);
  }
  return {format, render};
})();
