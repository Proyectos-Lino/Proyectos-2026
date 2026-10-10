// Empaquetado Windows SEA. No incluye claves privadas en la carpeta distribuible.
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'deliverables', 'Chatbox-ITIL-1.0.0-windows-x64');
const work = path.join(root, '.tmp', 'sea-release');
const exe = path.join(out, 'chatbox-itil.exe');
(async () => {
  if (process.platform !== 'win32' || process.arch !== 'x64') throw new Error('Construir con Node Windows x64.');
  fs.mkdirSync(work, { recursive: true }); fs.mkdirSync(out, { recursive: true });
  await require('esbuild').build({
    entryPoints: [path.join(root, 'server.js')], bundle: true, platform: 'node', format: 'cjs', target: 'node22', packages: 'external',
    outfile: path.join(work, 'bundle.cjs'),
    banner: { js: "require = require('node:module').createRequire(require('node:path').join(require('node:path').dirname(process.execPath), 'runtime.cjs'));" }
  });
  fs.writeFileSync(path.join(work, 'sea.json'), JSON.stringify({ main: path.join(work, 'bundle.cjs'), output: path.join(work, 'sea.blob'), disableExperimentalSEAWarning: true }));
  execFileSync(process.execPath, ['--experimental-sea-config', path.join(work, 'sea.json')], { windowsHide: true, stdio: 'inherit' });
  fs.copyFileSync(process.execPath, exe);
  await require('postject').inject(exe, 'NODE_SEA_BLOB', fs.readFileSync(path.join(work, 'sea.blob')), { sentinelFuse: 'NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2' });
  const publicOut = path.resolve(out, 'public');
  if (path.dirname(publicOut) !== path.resolve(out)) throw new Error('Destino de recursos fuera de la entrega.');
  fs.rmSync(publicOut, { recursive: true, force: true });
  for (const name of ['public', 'README.txt', 'LICENSE']) fs.cpSync(path.join(root, name), path.join(out, name), { recursive: true });
  fs.mkdirSync(path.join(out, 'data'), { recursive: true });
  fs.rmSync(path.join(out, 'data', 'surveys.json'), { force: true });
  for (const name of ['tickets.json', 'knowledge-base.json', 'knowledge-items.json', 'knowledge-attachments']) {
    const source = path.join(root, 'data', name);
    if (fs.existsSync(source)) fs.cpSync(source, path.join(out, 'data', name), { recursive: true });
  }
  const seen = new Set();
  const notices = [];
  function copyPackage(name, from = root) {
    const search = require('node:module').createRequire(path.join(from, 'package.json')).resolve.paths(name);
    if (!search) return; // Módulo nativo de Node, incluido en el ejecutable.
    const source = search.map(dir => path.join(dir, name)).find(dir => fs.existsSync(path.join(dir, 'package.json')));
    if (!source) throw new Error('Dependencia no encontrada: ' + name);
    if (seen.has(source)) return;
    seen.add(source);
    const relative = path.relative(root, source);
    if (!relative.startsWith('node_modules' + path.sep)) throw new Error('Dependencia fuera del proyecto: ' + name);
    fs.cpSync(source, path.join(out, relative), { recursive: true });
    const pkg = JSON.parse(fs.readFileSync(path.join(source, 'package.json'), 'utf8'));
    notices.push(`${pkg.name} ${pkg.version} — ${pkg.license || 'consultar licencia incluida'}`);
    for (const dep of Object.keys(pkg.dependencies || {})) copyPackage(dep, source);
  }
  for (const name of Object.keys(require('../package.json').dependencies)) copyPackage(name);
  fs.writeFileSync(path.join(out, '.env'), 'PORT=3000\nOPEN_BROWSER=1\nGEMINI_API_KEY=\n');
  fs.writeFileSync(path.join(out, 'DEPENDENCIAS.txt'), 'Dependencias distribuidas con sus avisos de licencia en node_modules.\n\n' + notices.sort().join('\n') + '\n\nInter: SIL Open Font License; ver public/assets/fonts/OFL.txt.\nNode.js: consultar NODE-LICENSE.txt.\n');
  const nodeLicense = path.join(__dirname, 'resources', 'node-' + process.version + '-LICENSE.txt');
  if (!fs.existsSync(nodeLicense)) throw new Error('Falta la licencia del runtime: ' + nodeLicense);
  fs.copyFileSync(nodeLicense, path.join(out, 'NODE-LICENSE.txt'));
  const manifest = { version: '1.0.0', edition: 'sistema-central', node: process.version, platform: process.platform, arch: process.arch, builtAt: new Date().toISOString(), executable: exe, dependencyPackages: seen.size, includesApiKey: false };
  fs.writeFileSync(path.join(root, 'deliverables', 'compilacion.json'), JSON.stringify(manifest, null, 2));
  console.log(JSON.stringify(manifest, null, 2));
})().catch(error => { console.error(error); process.exitCode = 1; });
