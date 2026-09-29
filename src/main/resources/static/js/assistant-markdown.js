'use strict';
(() => {
  // Build elements from Markdown so assistant output is never interpreted as HTML.
  function render(target, source) {
    target.replaceChildren();
    const lines = String(source || '').replace(/\r\n?/g, '\n').split('\n');
    appendBlocks(target, lines);
  }

  function appendInline(target, source) {
    const pattern = /(`+)([\s\S]*?)\1|(!?)\[([^\]]+)\]\(([^\s)]+)(?:\s+"[^"]*")?\)|(\*\*|__|~~|\*|_)(.+?)\6/g;
    let offset = 0;
    for (const match of source.matchAll(pattern)) {
      target.append(document.createTextNode(source.slice(offset, match.index)));
      if (match[1]) {
        const code = document.createElement('code');
        code.textContent = match[2].replace(/\n/g, ' ');
        target.append(code);
      } else if (match[4] !== undefined) {
        const url = match[5];
        const safeUrl = /^(https?:|mailto:)/i.test(url);
        if (safeUrl && !match[3]) {
          const link = document.createElement('a');
          link.href = url;
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
          link.textContent = match[4];
          target.append(link);
        } else {
          target.append(document.createTextNode(match[4]));
        }
      } else {
        const element = document.createElement(match[6] === '~~' ? 'del' : match[6].length === 2 ? 'strong' : 'em');
        appendInline(element, match[7]);
        target.append(element);
      }
      offset = match.index + match[0].length;
    }
    target.append(document.createTextNode(source.slice(offset)));
  }

  function isBoundary(line) {
    return /^\s*(?:#{1,6}\s|```|~~~|>|[-*+]\s|\d+[.)]\s|(?:---+|\*\*\*+|___+)\s*$)/.test(line);
  }

  function tableCells(line) {
    return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());
  }

  function appendBlocks(target, lines) {
    for (let index = 0; index < lines.length;) {
      const line = lines[index];
      if (!line.trim()) { index++; continue; }

      const fence = line.match(/^\s*(`{3,}|~{3,})([^`]*)$/);
      if (fence) {
        const codeLines = [];
        const closing = new RegExp('^\\s*' + fence[1][0] + '{' + fence[1].length + ',}\\s*$');
        index++;
        while (index < lines.length && !closing.test(lines[index])) codeLines.push(lines[index++]);
        if (index < lines.length) index++;
        const pre = document.createElement('pre');
        const code = document.createElement('code');
        code.textContent = codeLines.join('\n');
        const language = fence[2].trim().split(/\s+/)[0];
        if (/^[\w+-]+$/.test(language)) code.className = 'language-' + language;
        pre.append(code);
        target.append(pre);
        continue;
      }

      const heading = line.match(/^\s*(#{1,6})\s+(.+?)\s*#*\s*$/);
      if (heading) {
        const element = document.createElement('h' + heading[1].length);
        appendInline(element, heading[2]);
        target.append(element);
        index++;
        continue;
      }

      if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
        target.append(document.createElement('hr'));
        index++;
        continue;
      }

      if (/^\s*>/.test(line)) {
        const quoteLines = [];
        while (index < lines.length && /^\s*>/.test(lines[index])) quoteLines.push(lines[index++].replace(/^\s*>\s?/, ''));
        const quote = document.createElement('blockquote');
        appendBlocks(quote, quoteLines);
        target.append(quote);
        continue;
      }

      if (index + 1 < lines.length && line.includes('|')) {
        const headings = tableCells(line);
        const separator = tableCells(lines[index + 1]);
        if (headings.length === separator.length && separator.every(cell => /^:?-{3,}:?$/.test(cell))) {
          const table = document.createElement('table');
          const head = document.createElement('thead');
          const headerRow = document.createElement('tr');
          for (const value of headings) {
            const cell = document.createElement('th');
            appendInline(cell, value);
            headerRow.append(cell);
          }
          head.append(headerRow);
          table.append(head);
          const body = document.createElement('tbody');
          index += 2;
          while (index < lines.length && lines[index].trim() && lines[index].includes('|')) {
            const row = document.createElement('tr');
            for (const value of tableCells(lines[index++]).slice(0, headings.length)) {
              const cell = document.createElement('td');
              appendInline(cell, value);
              row.append(cell);
            }
            body.append(row);
          }
          table.append(body);
          target.append(table);
          continue;
        }
      }

      const listMatch = line.match(/^\s*([-*+]|\d+[.)])\s+(.+)$/);
      if (listMatch) {
        const ordered = /^\d/.test(listMatch[1]);
        const list = document.createElement(ordered ? 'ol' : 'ul');
        if (ordered) list.start = Number.parseInt(listMatch[1], 10);
        while (index < lines.length) {
          const item = lines[index].match(/^\s*([-*+]|\d+[.)])\s+(.+)$/);
          if (!item || /^\d/.test(item[1]) !== ordered) break;
          const entry = document.createElement('li');
          appendInline(entry, item[2]);
          list.append(entry);
          index++;
        }
        target.append(list);
        continue;
      }

      const paragraph = [line.trim()];
      index++;
      while (index < lines.length && lines[index].trim() && !isBoundary(lines[index])) paragraph.push(lines[index++].trim());
      const element = document.createElement('p');
      appendInline(element, paragraph.join(' '));
      target.append(element);
    }
  }

  window.AssistantMarkdown = { render };
})();
