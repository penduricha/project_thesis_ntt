#!/usr/bin/env node
/**
 * check-build-syntax.mjs — Kiem tra cu phap dung CHINH compiler cua project.
 *
 * Vi sao can
 * ----------
 * Khi copy code tu chat / Word / web vao file .vue, dau nhay thang ' co the bi doi
 * thanh dau nhay "thong minh" U+2019, lam vo build:
 *
 *   [plugin:vite:vue] AccountView.vue:45:87
 *   SyntaxError: Error parsing JavaScript expression: Unterminated string constant
 *
 * Loi nay chi lo ra khi build (hoac khi mo trang), rat kho doan bang mat. Script nay
 * chay dung @vue/compiler-sfc va node --check de bao loi SOM, dung dong/cot.
 *
 * Vi sao khong dung "quet smart quote" tho
 * ---------------------------------------
 * Du an CO CHU DINH dung "..." (U+201C/U+201D) de hien thi tieng Viet cho dep:
 *
 *   toast('Da them chuong "..." vao mon')            <- hop le
 *   log(`Doi ten thanh "${v}"`)                       <- hop le
 *   <p>Khong tim thay mon "{{ q }}"</p>               <- hop le
 *
 * Chi bao loi khi COMPILER that su tu choi file. Khong bao dong gia.
 *
 * Cach dung
 * ---------
 *   node tools/check-build-syntax.mjs              # kiem tra src/
 *   node tools/check-build-syntax.mjs src/ x.js    # chi dinh duong dan
 *   node tools/check-build-syntax.mjs --fix        # tu sua dong bi loi + xoa conflict marker
 *
 * Ma thoat: 0 = sach, 1 = con loi.
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, extname, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..');
const require = createRequire(join(ROOT, 'package.json'));

const SMART = /[\u2018\u2019\u201C\u201D]/;
const SMART_ALL = /[\u2018\u2019\u201C\u201D]/g;
const CONFLICT = /^(?:<{7}[^\n]*|={7}|>{7}[^\n]*)\n?/gm;

const SRC_EXT = new Set(['.vue', '.js', '.mjs', '.ts']);

function walk(target, out = []) {
  const st = statSync(target, { throwIfNoEntry: false });
  if (!st) return out;
  if (st.isDirectory()) {
    for (const name of readdirSync(target)) {
      if (name === 'node_modules' || name === 'dist') continue;
      walk(join(target, name), out);
    }
  } else if (SRC_EXT.has(extname(target))) {
    out.push(target);
  }
  return out;
}

/** Kiem tra 1 file .vue bang compiler-sfc. Tra ve null neu OK, hoac {line, msg}. */
function checkVue(file, code) {
  const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc');
  const { descriptor, errors } = parse(code, { filename: file });
  if (errors.length) {
    const e = errors[0];
    return { line: e.loc?.start.line ?? 0, msg: e.message || String(e) };
  }
  try {
    if (descriptor.script || descriptor.scriptSetup) compileScript(descriptor, { id: 'chk' });
    if (descriptor.template) {
      const r = compileTemplate({
        source: descriptor.template.content,
        filename: file,
        id: 'chk',
      });
      if (r.errors.length) {
        const e = r.errors[0];
        // loi template tinh theo dong trong rieng khoi <template> -> doi ve dong file
        const off = descriptor.template.loc.start.line - 1;
        return { line: (e.loc?.start.line ?? 1) + off, msg: e.message || String(e) };
      }
    }
  } catch (e) {
    const line = e.loc?.start?.line ?? e.loc?.line ?? 0;
    return { line, msg: String(e.message || e).split('\n')[0] };
  }
  return null;
}

/** Kiem tra 1 file .js/.mjs bang `node --check` (dung parser cua Node). */
function checkJs(file, code) {
  const tmp = join(ROOT, `.syntax-check.${process.pid}.mjs`);
  writeFileSync(tmp, code);
  try {
    execFileSync(process.execPath, ['--check', tmp], { stdio: 'pipe' });
    return null;
  } catch (e) {
    const out = String(e.stderr || e.message);
    const lineMatch = out.match(/:(\d+)\n/);
    const msgMatch = out.match(/^(\w*SyntaxError: .+)$/m);
    return {
      line: lineMatch ? Number(lineMatch[1]) : 0,
      msg: msgMatch ? msgMatch[1] : 'SyntaxError',
    };
  } finally {
    try { writeFileSync(tmp, ''); } catch {}
    try { require('node:fs').unlinkSync(tmp); } catch {}
  }
}

const check = (file, code) => (file.endsWith('.vue') ? checkVue(file, code) : checkJs(file, code));

/**
 * Sua dong bi loi: doi smart quote tren dung dong compiler phan nan thanh ASCII.
 * An toan vi chi dung vao dong ma compiler da tu choi.
 */
function fixLine(code, lineNo) {
  const lines = code.split('\n');
  const i = lineNo - 1;
  if (i < 0 || i >= lines.length) return code;
  if (!SMART.test(lines[i])) return code;
  lines[i] = lines[i]
    .replace(/\u2018|\u2019/g, "'")
    .replace(/\u201C|\u201D/g, '"');
  return lines.join('\n');
}

function main(argv) {
  const fix = argv.includes('--fix');
  const targets = argv.filter((a) => !a.startsWith('--'));
  const files = (targets.length ? targets : ['src']).flatMap((t) => walk(resolve(ROOT, t)));

  let bad = 0;
  for (const file of files) {
    let code = readFileSync(file, 'utf8');
    const rel = file.replace(ROOT + '/', '');
    let conflicted = false;

    if (CONFLICT.test(code)) {
      conflicted = true;
      CONFLICT.lastIndex = 0;
      if (fix) code = code.replace(CONFLICT, '');
    }

    let err = check(file, code);
    let repaired = false;

    // Neu loi va dong do co smart quote -> sua roi thu lai (toi da 5 vong)
    if (err && fix) {
      for (let round = 0; err && round < 5; round++) {
        const next = fixLine(code, err.line);
        if (next === code) break;
        code = next;
        repaired = true;
        err = check(file, code);
      }
    }

    if (repaired) {
      writeFileSync(file, code);
      console.log(`[DA SUA] ${rel}${err ? ' (van con loi)' : ''}`);
    } else if (conflicted && fix) {
      writeFileSync(file, code);
      console.log(`[DA SUA] ${rel} (xoa conflict marker, GIU ca 2 phia - kiem tra lai noi dung)`);
    }

    if (err) {
      bad++;
      console.log(`[LOI] ${rel}:${err.line}  ${err.msg}`);
      const line = readFileSync(file, 'utf8').split('\n')[err.line - 1];
      if (line) console.log(`      | ${line.trim().slice(0, 110)}`);
      if (SMART.test(line || '')) {
        console.log('      -> dong nay co smart quote: chay lai voi --fix');
      }
    }
  }

  if (bad) {
    console.log(`\n${bad} file loi cu phap. Dung: node tools/check-build-syntax.mjs --fix`);
    return 1;
  }
  console.log(`[OK] ${files.length} file sach cu phap`);
  return 0;
}

process.exit(main(process.argv.slice(2)));
