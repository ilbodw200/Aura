// Install with: npm install --prefix .tools xcode
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const xcode = require(path.join(root, '.tools/node_modules/xcode'));
const project = xcode.project(path.join(root, 'Aura.xcodeproj/project.pbxproj'));
project.parseSync();
const objects = project.hash.project.objects;
const targets = Object.values(objects.PBXNativeTarget).filter(x => typeof x === 'object');
assert.equal(targets.length, 3);
assert.deepEqual(targets.map(x => x.name).sort(), ['Aura', 'AuraTests', 'AuraTunnel']);
for (const reference of Object.values(objects.PBXFileReference).filter(x => typeof x === 'object')) {
  if (reference.sourceTree === 'BUILT_PRODUCTS_DIR') continue;
  const file = reference.path.replace(/^"|"$/g, '');
  assert.ok(fs.existsSync(path.join(root, file)), `Missing project file: ${file}`);
}
for (const build of Object.values(objects.PBXBuildFile).filter(x => typeof x === 'object')) {
  assert.ok(objects.PBXFileReference[build.fileRef], `Unknown build file reference: ${build.fileRef}`);
}
assert.ok(fs.existsSync(path.join(root, 'Aura.xcodeproj/xcshareddata/xcschemes/Aura.xcscheme')));
console.log('PASS: parsed Xcode project, 3 targets, file/build references, shared scheme. Not an Xcode build.');
