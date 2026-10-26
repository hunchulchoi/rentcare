// Real-browser check of the vendor login form (catches CSRF/Origin problems curl cannot).
//   node scripts/browser-login-check.mjs https://rentcare.devfor.link [--list-check] [--shot out.png] [--width 1400] [--path /vendor/…] [--hover]
// Uses local Google Chrome headless over the DevTools protocol; token from web/.env DEMO_VENDOR_TOKEN.
import { spawn } from 'node:child_process';
import { readFileSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const base = process.argv[2];
const arg = (k) => { const i = process.argv.indexOf(k); return i > 0 ? process.argv[i + 1] : undefined; };
const shot = arg('--shot');
const listCheck = process.argv.includes('--list-check');
const path = arg('--path');
const hover = process.argv.includes('--hover');
const actionCheck = arg('--action-check'); // incident detail path
const codeCheck = arg('--code-check'); // attach code: phone page 6-box input, no login
const rentalCheck = arg('--rental-check'); // rental code: phone basic report on /r (RENTCARE-13)
const width = Number(arg('--width') ?? 1400);
const token = readFileSync('web/.env', 'utf8').match(/^DEMO_VENDOR_TOKEN=(.*)$/m)?.[1]?.trim();
if (!base || !token) throw new Error('usage: browser-login-check.mjs <base url> (needs DEMO_VENDOR_TOKEN in web/.env)');
const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const profile = mkdtempSync(join(tmpdir(), 'rc-chrome-'));
const port = 9300 + Math.floor(Math.random() * 500);
const proc = spawn(chrome, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
try {
	let target;
	for (let i = 0; i < 50 && !target; i++) {
		await sleep(200);
		target = await fetch(`http://127.0.0.1:${port}/json`).then((r) => r.json()).then((l) => l.find((t) => t.type === 'page')).catch(() => null);
	}
	const ws = new WebSocket(target.webSocketDebuggerUrl);
	await new Promise((r) => ws.addEventListener('open', r));
	let id = 0;
	const pending = new Map();
	ws.addEventListener('message', (e) => {
		const m = JSON.parse(e.data);
		if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
	});
	const send = (method, params = {}) => new Promise((r) => { const n = ++id; pending.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
	const evalJs = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }))?.result?.value;
	const waitFor = async (expr, ms = 70000) => { for (let t = 0; t < ms; t += 500) { if (await evalJs(expr)) return true; await sleep(500); } return false; };

	await send('Page.enable');
	await send('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width < 600 });
	const capture = async (file) => {
		if (!file) return;
		const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
		writeFileSync(file, Buffer.from(data, 'base64'));
	};
	if (rentalCheck) {
		const lang = arg('--lang') ?? 'ko';
		await send('Browser.grantPermissions', { permissions: ['geolocation'], origin: new URL(base).origin });
		await send('Emulation.setGeolocationOverride', { latitude: 37.3947, longitude: 127.1112, accuracy: 25 });
		await send('Page.navigate', { url: `${base}/r?lang=${lang}` });
		if (!(await waitFor(`!!document.querySelector('main[data-ready] .boxes[data-ready]')`, 60000))) throw new Error('/r never became interactive');
		await capture(shot && shot.replace(/\.png$/, '-1-code.png'));
		for (const ch of rentalCheck.toLowerCase()) {
			await evalJs(`(() => { const i = document.querySelector('.boxes input'); i.focus(); i.value += ${JSON.stringify(ch)}; i.dispatchEvent(new InputEvent('input', { bubbles: true })); return true })()`);
			await sleep(60);
		}
		const verified = await waitFor(`!!document.querySelector('fieldset.types')`, 15000);
		const car = verified ? await evalJs(`document.querySelector('.car')?.innerText`) : '';
		console.log(`${verified ? 'PASS' : 'FAIL'} 8-box rental code auto-verified: ${String(car).replace(/\s+/g, ' ')}`);
		let filed = false, receipt = '', attach = '';
		if (verified) {
			await evalJs(`document.querySelector('input[name=type][value=NO_START]').click()`);
			await evalJs(`(() => { const t = document.querySelector('#note'); t.value = 'browser rental-check'; t.dispatchEvent(new InputEvent('input', { bubbles: true })); return true })()`);
			await evalJs(`document.querySelector('section.loc button').click()`);
			const loc = await waitFor(`!!document.querySelector('input[name=lat]')`, 20000);
			console.log(`${loc ? 'PASS' : 'FAIL'} phone location added: ${await evalJs(`document.querySelector('section.loc [role]')?.innerText`)}`);
			await evalJs(`document.querySelector('input[name=consent]').click()`);
			await capture(shot && shot.replace(/\.png$/, '-2-form.png'));
			await evalJs(`document.querySelector('form[action*=report] button[type=submit]').click()`);
			filed = await waitFor(`!!document.querySelector('[data-receipt]')`, 20000);
			receipt = await evalJs(`document.querySelector('[data-receipt]')?.dataset.receipt`);
			attach = await evalJs(`document.querySelector('[data-attach-code]')?.dataset.attachCode`);
			await capture(shot && shot.replace(/\.png$/, '-3-done.png'));
			filed = filed && /^RC-/.test(receipt ?? '') && /^[A-Z0-9]{6}$/.test(attach ?? '') && loc;
			console.log(`${filed ? 'PASS' : 'FAIL'} report filed: ${receipt} attach code ${attach ? 'shown' : 'missing'}`);
			console.log(`RECEIPT=${receipt}`);
		}
		process.exitCode = verified && filed ? 0 : 1;
		ws.close();
		proc.kill();
		await sleep(300);
		rmSync(profile, { recursive: true, force: true });
		process.exit(process.exitCode ?? 0);
	}
	if (codeCheck) {
		await send('Page.navigate', { url: `${base}/m?lang=ko` });
		if (!(await waitFor(`!!document.querySelector('.boxes[data-ready]')`, 60000))) throw new Error('code input never became interactive');
		// Type like a person: lower case, a hyphen and a space mixed in, one key at a time.
		const typed = codeCheck.slice(0, 3).toLowerCase() + '- ' + codeCheck.slice(3).toLowerCase();
		let boxes = '';
		for (const [k, ch] of [...typed].entries()) {
			if (k === typed.length - 1) boxes = await evalJs(`[...document.querySelectorAll('.boxes .box')].map(b => b.innerText).join('')`);
			if (k === typed.length - 1 && shot) {
				const { data } = await send('Page.captureScreenshot', { format: 'png' });
				writeFileSync(shot, Buffer.from(data, 'base64'));
			}
			await evalJs(`(() => { const i = document.querySelector('.boxes input'); i.focus(); i.value = i.value + ${JSON.stringify('__C__')}.replace('__C__', ${JSON.stringify(ch)}); i.dispatchEvent(new InputEvent('input', { bubbles: true })); return true })()`);
			await sleep(60);
		}
		const verified = await waitFor(`!!document.querySelector('.notice')`, 15000);
		let locOk = true;
		if (process.argv.includes('--send-location') && verified) {
			// Phone location fallback: grant geolocation and fake a fix at Jeju airport.
			await send('Browser.grantPermissions', { permissions: ['geolocation'], origin: new URL(base).origin });
			await send('Emulation.setGeolocationOverride', { latitude: 33.5066, longitude: 126.4929, accuracy: 25 });
			await evalJs(`[...document.querySelectorAll('section.loc button')][0].click()`);
			locOk = await waitFor(`/위치를 보냈습니다|Location sent|이미 있어|already there/.test(document.querySelector('section.loc')?.innerText ?? '')`, 20000);
			console.log(`${locOk ? 'PASS' : 'FAIL'} phone location: ${(await evalJs(`document.querySelector('section.loc [role]')?.innerText`)) ?? 'no message'}`);
		}
		const pass = boxes === codeCheck.slice(0, 5) && verified && locOk;
		console.log(`${pass ? 'PASS' : 'FAIL'} 6-box code: typed '${typed}' → boxes before last key=${boxes}, auto-verified on 6th=${verified}`);
		process.exitCode = pass ? 0 : 1;
		ws.close();
		proc.kill();
		await sleep(300);
		rmSync(profile, { recursive: true, force: true });
		process.exit(process.exitCode ?? 0);
	}
	await send('Page.navigate', { url: `${base}/vendor/login` });
	if (!(await waitFor(`!!document.querySelector('input[name=token]')`))) throw new Error('login form not found');
	await evalJs(`(() => { const f = document.querySelector('form'); f.querySelector('input[name=token]').value = ${JSON.stringify(token)};
		const n = f.querySelector('input[name=name]'); if (n) n.value = 'browser-check'; f.requestSubmit(); return true })()`);
	const ok = await waitFor(`location.pathname === '/vendor' && !document.body.innerText.includes('Cross-site')`, 20000);
	const text = (await evalJs('document.body.innerText'))?.slice(0, 120).replace(/\s+/g, ' ');
	console.log(ok ? `PASS browser login → ${await evalJs('location.pathname')}` : `FAIL browser login: ${await evalJs('location.pathname')} | ${text}`);
	if (ok && listCheck) {
		// Vendor list (TanStack): search, status filter, sorting work in the browser.
		if (!(await waitFor(`!!document.querySelector('[data-ready]')`, 30000))) throw new Error('vendor list never became interactive');
		const rows = () => evalJs(`document.querySelectorAll('tbody tr').length`);
		const typed = async (q) => evalJs(`(() => { const i = document.querySelector('input[type=search]'); i.value = ${JSON.stringify('__Q__')}.replace('__Q__', ${JSON.stringify(q)}); i.dispatchEvent(new Event('input', { bubbles: true })); return true })()`);
		const before = await rows();
		const total = await evalJs(`document.querySelector('.result')?.innerText`);
		await typed('타이어');
		await waitFor(`document.querySelector('.result')?.innerText !== ${JSON.stringify('__T__')}.replace('__T__', ${JSON.stringify(total ?? '')})`, 5000);
		const tireRows = await evalJs(`[...document.querySelectorAll('tbody tr td:nth-child(3)')].map(td => td.innerText.trim())`);
		await typed(''); await sleep(200);
		await evalJs(`[...document.querySelectorAll('.seg button')].find(b => b.innerText.startsWith('확인함')).click()`); await sleep(300);
		const ackOnly = await evalJs(`[...document.querySelectorAll('tbody tr td:last-child')].every(td => td.innerText.includes('확인함'))`);
		await evalJs(`[...document.querySelectorAll('.seg button')].find(b => b.innerText.startsWith('전체')).click()`); await sleep(200);
		await evalJs(`[...document.querySelectorAll('th button.sort')].find(b => b.innerText.startsWith('접수 번호')).click()`); await sleep(300);
		const nums = await evalJs(`[...document.querySelectorAll('tbody tr td:nth-child(2) a')].map(a => a.innerText)`);
		const sortedAsc = nums.every((n, k) => k === 0 || nums[k - 1] <= n);
		const pass = before > 0 && tireRows.length > 0 && tireRows.every((t) => t === '타이어 이상') && ackOnly && sortedAsc;
		console.log(`${pass ? 'PASS' : 'FAIL'} vendor list: rows=${before}, search 타이어 → ${tireRows.length} rows all tire=${tireRows.every((t) => t === '타이어 이상')}, status 확인함 only=${ackOnly}, receipt sort asc=${sortedAsc}`);
		if (!pass) process.exitCode = 1;
	}
	if (ok && actionCheck) {
		// Staff log: record a tow request in a real browser and see it in the timeline.
		await send('Page.navigate', { url: `${base}${actionCheck}` });
		if (!(await waitFor(`!!document.querySelector('form.log-form') && !!document.querySelector('[data-ready]')`, 30000))) throw new Error('log form not ready');
		const marker = `e2e-${Date.now()}`;
		await evalJs(`(() => { const f = document.querySelector('form.log-form');
			f.querySelector('input[name=kind][value=ACTION]').click();
			f.querySelector('input[name=actionType][value=TOW]').click();
			const t = f.querySelector('textarea'); t.value = ${JSON.stringify('__M__')}.replace('__M__', ${JSON.stringify(marker)}); t.dispatchEvent(new Event('input', { bubbles: true }));
			setTimeout(() => f.requestSubmit(), 50); return true })()`);
		const saved = await waitFor(`[...document.querySelectorAll('.timeline li')].some(li => li.innerText.includes('견인 요청') && li.innerText.includes(${JSON.stringify('__M__')}.replace('__M__', ${JSON.stringify(marker)})))`, 15000);
		const cleared = await evalJs(`document.querySelector('form.log-form textarea').value === ''`);
		console.log(`${saved && cleared ? 'PASS' : 'FAIL'} staff log: tow action saved=${saved}, form cleared=${cleared}`);
		if (!(saved && cleared)) process.exitCode = 1;
	}
	if (ok && shot) {
		if (path) {
			await send('Page.navigate', { url: `${base}${path}` });
			await waitFor(`location.pathname === ${JSON.stringify(path)} && document.readyState === 'complete'`, 30000);
		}
		await sleep(2500); // let map tiles load
		if (hover) {
			const pt = await evalJs(`(() => { const r = document.querySelector('tbody tr:nth-child(3)')?.getBoundingClientRect(); return r ? [r.x + r.width / 2, r.y + r.height / 2] : null })()`);
			if (pt) await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: pt[0], y: pt[1] });
			await sleep(300);
		}
		const h = await evalJs('document.documentElement.scrollHeight');
		await send('Emulation.setDeviceMetricsOverride', { width, height: Math.min(h, 4000), deviceScaleFactor: 1, mobile: width < 600 });
		await sleep(800);
		const { data } = await send('Page.captureScreenshot', { format: 'png' });
		writeFileSync(shot, Buffer.from(data, 'base64'));
	}
	ws.close();
	process.exitCode = ok ? 0 : 1;
} finally {
	proc.kill();
	await sleep(300);
	rmSync(profile, { recursive: true, force: true });
}
