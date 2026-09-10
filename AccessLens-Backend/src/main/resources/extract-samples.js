(maxSamples) => {
  const RGB = /^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,/\s]+([\d.]+))?\s*\)$/;

  function parseColor(str) {
    if (!str) return null;
    const m = str.match(RGB);
    if (!m) return null;
    return {
      r: parseFloat(m[1]),
      g: parseFloat(m[2]),
      b: parseFloat(m[3]),
      a: m[4] === undefined ? 1 : parseFloat(m[4])
    };
  }

  function over(src, dst) {
    const a = src.a;
    return {
      r: src.r * a + dst.r * (1 - a),
      g: src.g * a + dst.g * (1 - a),
      b: src.b * a + dst.b * (1 - a),
      a: 1
    };
  }

  function toHex(c) {
    const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
    const h = (v) => clamp(v).toString(16).padStart(2, '0');
    return '#' + h(c.r) + h(c.g) + h(c.b);
  }

  function ancestorChain(el) {
    const chain = [];
    let node = el;
    while (node && node.nodeType === 1) {
      const s = getComputedStyle(node);
      const o = parseFloat(s.opacity);
      chain.push({
        bg: parseColor(s.backgroundColor),
        opacity: isNaN(o) ? 1 : o,
        hasImage: s.backgroundImage !== 'none'
      });
      node = node.parentElement;
    }
    return chain;
  }

  function resolveColors(el, cs) {
    const chain = ancestorChain(el);
    const n = chain.length;

    const opacityToRoot = new Array(n);
    let product = 1;
    for (let i = n - 1; i >= 0; i--) {
      product *= chain[i].opacity;
      opacityToRoot[i] = product;
    }

    let indeterminate = false;
    let baseIndex = n;
    for (let i = 0; i < n; i++) {
      if (chain[i].hasImage) indeterminate = true;
      const bg = chain[i].bg;
      if (bg && bg.a * opacityToRoot[i] >= 0.999) {
        baseIndex = i;
        break;
      }
    }

    let result = baseIndex < n
      ? { r: chain[baseIndex].bg.r, g: chain[baseIndex].bg.g, b: chain[baseIndex].bg.b, a: 1 }
      : { r: 255, g: 255, b: 255, a: 1 };

    for (let i = baseIndex - 1; i >= 0; i--) {
      const bg = chain[i].bg;
      if (!bg || bg.a <= 0) continue;
      result = over({ r: bg.r, g: bg.g, b: bg.b, a: bg.a * opacityToRoot[i] }, result);
    }

    const fgRaw = parseColor(cs.color) || { r: 0, g: 0, b: 0, a: 1 };
    const fgAlpha = fgRaw.a * (n > 0 ? opacityToRoot[0] : 1);
    const fg = over({ r: fgRaw.r, g: fgRaw.g, b: fgRaw.b, a: fgAlpha }, result);

    return { fg: toHex(fg), bg: toHex(result), indeterminate };
  }

  function ownText(el) {
    let t = '';
    for (const node of el.childNodes) {
      if (node.nodeType === 3) t += node.nodeValue;
    }
    return t.trim().replace(/\s+/g, ' ');
  }

  function isVisible(el) {
    if (el.closest('[aria-hidden="true"]')) return false;
    if (typeof el.checkVisibility === 'function') {
      if (!el.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true })) return false;
    }
    const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    if (r.bottom < 0 || r.right < 0) return false;
    return true;
  }

  function cssPath(el) {
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && parts.length < 4) {
      if (node.id) {
        parts.unshift('#' + CSS.escape(node.id));
        break;
      }
      let part = node.tagName.toLowerCase();
      const raw = node.getAttribute('class') || '';
      const cls = raw.trim().split(/\s+/).filter(Boolean).slice(0, 2);
      if (cls.length) part += '.' + cls.map((c) => CSS.escape(c)).join('.');
      parts.unshift(part);
      node = node.parentElement;
    }
    return parts.join(' > ');
  }

  const SKIP = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TITLE', 'META', 'LINK', 'HEAD']);
  const byKey = new Map();
  let scanned = 0;

  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
  let el = document.body;

  while (el) {
    if (!SKIP.has(el.tagName)) {
      const text = ownText(el);
      if (text.length > 0 && isVisible(el)) {
        scanned++;
        const cs = getComputedStyle(el);
        const { fg, bg, indeterminate } = resolveColors(el, cs);
        const fontSizePx = parseFloat(cs.fontSize) || 16;
        const weight = parseInt(cs.fontWeight, 10);
        const bold = !isNaN(weight) && weight >= 700;

        const key = fg + '|' + bg + '|' + fontSizePx.toFixed(1) + '|' + bold + '|' + indeterminate;
        const existing = byKey.get(key);
        if (existing) {
          existing.occurrences++;
        } else if (byKey.size < maxSamples) {
          const r = el.getBoundingClientRect();
          byKey.set(key, {
            selector: cssPath(el),
            textSnippet: text.slice(0, 80),
            foregroundHex: fg,
            backgroundHex: bg,
            fontSizePx: fontSizePx,
            bold: bold,
            backgroundIndeterminate: indeterminate,
            x: Math.round(r.x + window.scrollX),
            y: Math.round(r.y + window.scrollY),
            width: Math.round(r.width),
            height: Math.round(r.height),
            occurrences: 1
          });
        }
      }
    }
    el = walker.nextNode();
  }

  return JSON.stringify({
    pageUrl: location.href,
    pageTitle: document.title,
    lang: document.documentElement.getAttribute('lang') || '',
    elementsScanned: scanned,
    truncated: byKey.size >= maxSamples,
    samples: Array.from(byKey.values())
  });
}
