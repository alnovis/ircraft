#!/usr/bin/env node
// Draw the README cover: two random trees branching out of an amber core along a tilted axis, beside the wordmark.
// The trees are generated from fixed seeds, so every run gives the same picture.
// Writes docs/images/cover.svg and renders docs/images/cover.png (2560x1280) with Chrome.
//
// Usage: node scripts/readme-cover.mjs

import { writeFileSync, mkdtempSync, rmSync, existsSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const W = 1280, H = 640;

function rng(seed) {
    return () => {
        seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
        let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

const STYLE = `
  <style>
    text { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    .word { fill: #e0e0e0; font-size: 92px; font-weight: 700; letter-spacing: 0.14em; }
    .sub { fill: #fbbf24; font-size: 20px; font-weight: 500; letter-spacing: 0.12em; }
    .rule { stroke: #2a2a2a; stroke-width: 1; }
  </style>`;

const WORDMARK = `
  <text class="word" x="88" y="318">IRCRAFT</text>
  <line class="rule" x1="92" y1="350" x2="530" y2="350"/>
  <text class="sub" x="92" y="388">CODE GENERATION IN SMALL PASSES</text>`;

// Everything is drawn along a horizontal axis and then rotated as a whole, so right angles stay right.
const CX = 975, CY = 320, TILT = -28;
// Distance from a node to its children, by depth.
const STEP = [0, 72, 64, 58, 52, 47, 42, 38];
// How far across the axis a node may sit, by depth: narrow at the core, wide at the ends.
const CONE = d => 0.3 + 0.7 * d / (STEP.length - 1);
// Chance that a node at this depth has no children.
const STOP = [0, 0, 0.05, 0.15, 0.3, 0.45, 0.6, 1];
// Nodes this deep are cut loose from the tree and drift.
const LOOSE = 6;
const SIZE = [18, 15, 14, 13, 12, 11, 10, 9];

// Fill and stroke by depth: amber near the core, grey and dim towards the ends.
function look(d) {
    if (d <= 1) return { fill: '#2a1d06', stroke: '#f59e0b', edge: '#b45309', op: 1 };
    if (d <= 2) return { fill: '#1f1a10', stroke: '#b45309', edge: '#6b4a12', op: 0.95 };
    if (d <= 4) return { fill: '#1f1f1f', stroke: '#4a4a4a', edge: '#3a3a3a', op: 0.85 - 0.1 * (d - 3) };
    return { fill: '#181818', stroke: '#333333', edge: '#2c2c2c', op: 0.7 - 0.12 * (d - 5) };
}

function children(r, d) {
    if (d === 0) return 3;
    if (r() < STOP[d]) return 0;
    const x = r();
    if (d >= 4) return x < 0.45 ? 2 : 1;
    return x < 0.5 ? 2 : x < 0.8 ? 1 : 3;
}

function tree(r, d) {
    const n = d + 1 < STEP.length ? children(r, d) : 0;
    const kids = Array.from({ length: n }, () => tree(r, d + 1));
    return { kids, leaves: n ? kids.reduce((s, k) => s + k.leaves, 0) : 1 };
}

// Grows one lobe: every node owns a band across the axis and splits it between its children
// by the number of leaves under each, so branches never cross and crowded subtrees get room.
function lobe(seed, dir, start, band) {
    const r = rng(seed), out = [];
    function place(node, u, v, d, lo, hi) {
        const pv = v * CONE(d), step = STEP[d + 1];
        let at = lo;
        for (const kid of node.kids) {
            const span = (hi - lo) * kid.leaves / node.leaves;
            const cu = u + dir * step, cv = at + span / 2, sv = cv * CONE(d + 1);
            const c = look(d + 1);
            if (d + 1 >= LOOSE) {
                const x = cu + (r() - 0.5) * 16, y = sv + (r() - 0.5) * 16, rot = (r() - 0.5) * 70;
                out.push(cell(x, y, SIZE[d + 1], c, rot));
            } else {
                const knee = u + dir * step * 0.45;
                out.unshift(`  <path d="M${f(CX + u)} ${f(CY + pv)} H${f(CX + knee)} V${f(CY + sv)} H${f(CX + cu)}" fill="none" stroke="${c.edge}" stroke-width="1.3" opacity="${c.op.toFixed(2)}"/>`);
                out.push(cell(cu, sv, SIZE[d + 1], c, 0));
            }
            place(kid, cu, cv, d + 1, at, at + span);
            at += span;
        }
    }
    place(tree(r, 0), start, 0, 0, -band, band);
    return out;
}

const f = n => n.toFixed(1);

// Where a point of the unrotated drawing lands on the cover.
function screen(x, y) {
    const a = TILT * Math.PI / 180, dx = x - CX, dy = y - CY;
    return [CX + dx * Math.cos(a) - dy * Math.sin(a), CY + dx * Math.sin(a) + dy * Math.cos(a)];
}

function cell(u, v, s, c, rot) {
    const x = CX + u, y = CY + v;
    const [sx, sy] = screen(x, y);
    // Keep the wordmark clear.
    if (sx < 600 && sy < 420) return '';
    const t = rot ? ` transform="rotate(${rot.toFixed(1)} ${f(x)} ${f(y)})"` : '';
    return `  <rect x="${f(x - s / 2)}" y="${f(y - s / 2)}" width="${s}" height="${s}" rx="3" fill="${c.fill}" stroke="${c.stroke}" stroke-width="1.2" opacity="${c.op.toFixed(2)}"${t}/>`;
}

// The core: a small block of filled cells, brightest in the middle.
function core() {
    const out = [], pitch = 24, s = 18;
    for (let gy = -1; gy <= 1; gy++) {
        for (let gx = -1; gx <= 1; gx++) {
            const fill = gx === 0 && gy === 0 ? '#fde68a' : gx === 0 || gy === 0 ? '#fbbf24' : '#f59e0b';
            out.push(`  <rect x="${CX + gx * pitch - s / 2}" y="${CY + gy * pitch - s / 2}" width="${s}" height="${s}" rx="3.5" fill="${fill}"/>`);
        }
    }
    return out;
}

function cover() {
    const edge = 24 + 9;
    const right = lobe(41, 1, edge, 250);
    const left = lobe(67, -1, -edge, 250);
    const paths = l => l.filter(s => s.includes('<path'));
    const cells = l => l.filter(s => s && !s.includes('<path'));
    const body = [...paths(left), ...paths(right), ...cells(left), ...cells(right), ...core()];
    const defs = `
    <radialGradient id="glow" cx="50%" cy="50%" r="50%">
      <stop offset="0" stop-color="#f59e0b" stop-opacity="0.26"/>
      <stop offset="1" stop-color="#f59e0b" stop-opacity="0"/>
    </radialGradient>`;
    const glow = `  <ellipse cx="${CX}" cy="${CY}" rx="300" ry="170" fill="url(#glow)"/>`;
    return wrap(`  <g transform="rotate(${TILT} ${CX} ${CY})">\n${glow}\n${body.join('\n')}\n  </g>\n`, defs);
}

function wrap(body, defs = '') {
    return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg">${STYLE}
  <defs>${defs}</defs>
  <rect width="${W}" height="${H}" fill="#0f0f0f"/>
${body}${WORDMARK}
</svg>
`;
}

function screenshot(url, width, height, png, profile) {
    execFileSync(CHROME, [
        '--headless=new', '--disable-gpu', '--hide-scrollbars',
        ...(process.env.CI ? ['--no-sandbox'] : []),
        `--user-data-dir=${profile}`,
        '--force-device-scale-factor=2',
        `--window-size=${width},${height}`,
        `--screenshot=${png}`,
        url,
    ], { stdio: 'ignore' });
    if (!existsSync(png)) throw new Error(`Chrome wrote no ${png}`);
}

const dir = join(ROOT, 'docs', 'images');
mkdirSync(dir, { recursive: true });
const svg = cover();
writeFileSync(join(dir, 'cover.svg'), svg);

const work = mkdtempSync(join(tmpdir(), 'ircraft-cover-'));
try {
    const html = join(work, 'cover.html');
    writeFileSync(html, `<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:#0f0f0f}svg{display:block}</style>${svg}`);
    const png = join(dir, 'cover.png');
    rmSync(png, { force: true });
    screenshot(`file://${html}`, W, H, png, join(work, 'chrome'));
} finally {
    rmSync(work, { recursive: true, force: true });
}
console.log(`wrote ${join(dir, 'cover.svg')} and cover.png`);
