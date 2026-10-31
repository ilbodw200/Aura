// Dependency-free deterministic generator for opening the UI project directly.
// XcodeGen is the canonical build configuration; use it for the core variant.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const id = key => crypto.createHash('sha256').update(key).digest('hex').slice(0, 24).toUpperCase();
const quote = value => JSON.stringify(String(value));
const objects = [];
function object(key, type, fields) { objects.push(`${id(key)} = {isa = ${type}; ${fields}};`); return id(key); }
function files(directory) {
  return fs.readdirSync(path.join(root, directory), { withFileTypes: true }).flatMap(entry => entry.isDirectory() && !entry.name.endsWith('.xcassets') ? files(`${directory}/${entry.name}`) : [`${directory}/${entry.name}`]);
}
const shared = files('Shared').filter(x => x.endsWith('.swift'));
const sourceFiles = { Aura: [...files('Aura').filter(x => x.endsWith('.swift')), ...shared], AuraTunnel: [...files('Tunnel').filter(x => x.endsWith('.swift')), ...shared], AuraTests: files('Tests').filter(x => x.endsWith('.swift')), AuraUITests: files('UITests').filter(x => x.endsWith('.swift')) };
const allFiles = [...new Set(Object.values(sourceFiles).flat()), 'Aura/Assets.xcassets', 'Config/App-Info.plist', 'Config/Tunnel-Info.plist', 'Config/App.entitlements', 'Config/Tunnel.entitlements'];
for (const file of allFiles) object(`file:${file}`, 'PBXFileReference', `lastKnownFileType = ${file.endsWith('.swift') ? 'sourcecode.swift' : file.endsWith('.xcassets') ? 'folder.assetcatalog' : 'text.plist.xml'}; path = ${quote(file)}; sourceTree = "<group>";`);
const productTypes = { Aura: ['app', 'wrapper.application', 'com.apple.product-type.application'], AuraTunnel: ['appex', 'wrapper.app-extension', 'com.apple.product-type.app-extension'], AuraTests: ['xctest', 'wrapper.cfbundle', 'com.apple.product-type.bundle.unit-test'], AuraUITests: ['xctest', 'wrapper.cfbundle', 'com.apple.product-type.bundle.ui-testing'] };
for (const [name, [ext, type]] of Object.entries(productTypes)) object(`product:${name}`, 'PBXFileReference', `explicitFileType = ${type}; includeInIndex = 0; path = ${name}.${ext}; sourceTree = BUILT_PRODUCTS_DIR;`);
object('products', 'PBXGroup', `children = (${Object.keys(productTypes).map(x => id(`product:${x}`)).join(',')}); name = Products; sourceTree = "<group>";`);
object('rootgroup', 'PBXGroup', `children = (${allFiles.map(x => id(`file:${x}`)).join(',')},${id('products')}); sourceTree = "<group>";`);
const common = { SWIFT_VERSION: '5.0', IPHONEOS_DEPLOYMENT_TARGET: '17.0', SDKROOT: 'iphoneos', TARGETED_DEVICE_FAMILY: '1,2', CODE_SIGN_STYLE: 'Automatic', DEVELOPMENT_TEAM: '', CURRENT_PROJECT_VERSION: '8', MARKETING_VERSION: '1.0', AURA_BUNDLE_ID: 'app.aura.proxy', AURA_TUNNEL_BUNDLE_ID: '$(AURA_BUNDLE_ID).tunnel', AURA_APP_GROUP: 'group.$(AURA_BUNDLE_ID)', CLANG_ENABLE_MODULES: 'YES', ENABLE_USER_SCRIPT_SANDBOXING: 'YES' };
const settings = { AuraUITests: { PRODUCT_BUNDLE_IDENTIFIER: '$(AURA_BUNDLE_ID).uitests', GENERATE_INFOPLIST_FILE: 'YES', TEST_TARGET_NAME: 'Aura' }, Aura: { PRODUCT_BUNDLE_IDENTIFIER: '$(AURA_BUNDLE_ID)', INFOPLIST_FILE: 'Config/App-Info.plist', CODE_SIGN_ENTITLEMENTS: 'Config/App.entitlements', ASSETCATALOG_COMPILER_APPICON_NAME: 'AppIcon' }, AuraTunnel: { PRODUCT_BUNDLE_IDENTIFIER: '$(AURA_TUNNEL_BUNDLE_ID)', INFOPLIST_FILE: 'Config/Tunnel-Info.plist', CODE_SIGN_ENTITLEMENTS: 'Config/Tunnel.entitlements', APPLICATION_EXTENSION_API_ONLY: 'YES', SKIP_INSTALL: 'YES' }, AuraTests: { PRODUCT_BUNDLE_IDENTIFIER: '$(AURA_BUNDLE_ID).tests', GENERATE_INFOPLIST_FILE: 'YES', TEST_HOST: '$(BUILT_PRODUCTS_DIR)/Aura.app/Aura', BUNDLE_LOADER: '$(TEST_HOST)' } };
function configList(key, extra = {}) {
  for (const mode of ['Debug', 'Release']) {
    const value = { ...common, SWIFT_OPTIMIZATION_LEVEL: mode === 'Debug' ? '-Onone' : '-O', DEBUG_INFORMATION_FORMAT: mode === 'Debug' ? 'dwarf' : 'dwarf-with-dsym', ENABLE_TESTABILITY: mode === 'Debug' ? 'YES' : 'NO', ...extra };
    if (mode === 'Debug') value.SWIFT_ACTIVE_COMPILATION_CONDITIONS = 'DEBUG';
    object(`config:${key}:${mode}`, 'XCBuildConfiguration', `buildSettings = {${Object.entries(value).map(([k,v]) => `${k} = ${quote(v)};`).join('')}}; name = ${mode};`);
  }
  return object(`configs:${key}`, 'XCConfigurationList', `buildConfigurations = (${id(`config:${key}:Debug`)},${id(`config:${key}:Release`)}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;`);
}
configList('project');
object('embed:tunnel', 'PBXBuildFile', `fileRef = ${id('product:AuraTunnel')}; settings = {ATTRIBUTES = (RemoveHeadersOnCopy,);};`);
object('embed:phase', 'PBXCopyFilesBuildPhase', `buildActionMask = 2147483647; dstPath = ""; dstSubfolderSpec = 13; files = (${id('embed:tunnel')}); name = "Embed App Extensions"; runOnlyForDeploymentPostprocessing = 0;`);
for (const [from, to] of [['Aura','AuraTunnel'], ['AuraTests','Aura'], ['AuraUITests','Aura']]) {
  object(`proxy:${from}:${to}`, 'PBXContainerItemProxy', `containerPortal = ${id('project')}; proxyType = 1; remoteGlobalIDString = ${id(`target:${to}`)}; remoteInfo = ${to};`);
  object(`dependency:${from}:${to}`, 'PBXTargetDependency', `target = ${id(`target:${to}`)}; targetProxy = ${id(`proxy:${from}:${to}`)};`);
}
for (const name of Object.keys(productTypes)) {
  configList(name, { PRODUCT_NAME: '$(TARGET_NAME)', ...settings[name] });
  const buildFiles = sourceFiles[name].map(file => object(`build:${name}:${file}`, 'PBXBuildFile', `fileRef = ${id(`file:${file}`)};`));
  object(`sources:${name}`, 'PBXSourcesBuildPhase', `buildActionMask = 2147483647; files = (${buildFiles.join(',')}); runOnlyForDeploymentPostprocessing = 0;`);
  const resources = name === 'Aura' ? [object('assets:build', 'PBXBuildFile', `fileRef = ${id('file:Aura/Assets.xcassets')};`)] : [];
  object(`resources:${name}`, 'PBXResourcesBuildPhase', `buildActionMask = 2147483647; files = (${resources.join(',')}); runOnlyForDeploymentPostprocessing = 0;`);
  object(`frameworks:${name}`, 'PBXFrameworksBuildPhase', 'buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;');
  const dependencies = name === 'Aura' ? [id('dependency:Aura:AuraTunnel')] : name === 'AuraTests' || name === 'AuraUITests' ? [id(`dependency:${name}:Aura`)] : [];
  const phases = ['sources','frameworks','resources'].map(x => id(`${x}:${name}`));
  if (name === 'Aura') phases.push(id('embed:phase'));
  object(`target:${name}`, 'PBXNativeTarget', `buildConfigurationList = ${id(`configs:${name}`)}; buildPhases = (${phases.join(',')}); buildRules = (); dependencies = (${dependencies.join(',')}); name = ${name}; productName = ${name}; productReference = ${id(`product:${name}`)}; productType = ${quote(productTypes[name][2])};`);
}
object('project', 'PBXProject', `attributes = {LastUpgradeCheck = 2600;}; buildConfigurationList = ${id('configs:project')}; compatibilityVersion = "Xcode 14.0"; developmentRegion = ru; hasScannedForEncodings = 0; knownRegions = (ru,en,Base); mainGroup = ${id('rootgroup')}; productRefGroup = ${id('products')}; projectDirPath = ""; projectRoot = ""; targets = (${Object.keys(productTypes).map(x => id(`target:${x}`)).join(',')});`);
const directory = path.join(root, 'Aura.xcodeproj');
fs.mkdirSync(path.join(directory, 'xcshareddata/xcschemes'), { recursive: true });
const sectionNames = [...new Set(objects.map(line => line.match(/isa = (\w+);/)[1]))].sort();
const sectioned = sectionNames.map(type => `/* Begin ${type} section */\n${objects.filter(line => line.includes(`isa = ${type};`)).join('\n')}\n/* End ${type} section */`).join('\n');
const pbx = `// !$*UTF8*$!\n{archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n${sectioned}\n}; rootObject = ${id('project')};}\n`;
fs.writeFileSync(path.join(directory,'project.pbxproj'), pbx.replace(/([A-F0-9]{24})\)/g, '$1,)').replace('(ru,en,Base)', '(ru,en,Base,)'));
const ref = name => `<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="${id(`target:${name}`)}" BuildableName="${name}.${productTypes[name][0]}" BlueprintName="${name}" ReferencedContainer="container:Aura.xcodeproj"/>`;
fs.writeFileSync(path.join(directory,'xcshareddata/xcschemes/Aura.xcscheme'), `<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">${ref('Aura')}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">${ref('AuraTests')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">${ref('Aura')}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">${ref('Aura')}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>`);
fs.writeFileSync(path.join(directory, 'xcshareddata/xcschemes/AuraScrolling.xcscheme'), `<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">${ref('Aura')}</BuildActionEntry><BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO">${ref('AuraUITests')}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB"><Testables><TestableReference skipped="NO">${ref('AuraUITests')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB"><BuildableProductRunnable runnableDebuggingMode="0">${ref('Aura')}</BuildableProductRunnable></LaunchAction>
</Scheme>`);
console.log(`Generated Aura.xcodeproj: ${allFiles.length} references, 4 targets, shared Aura and AuraScrolling schemes. UI configuration; no VPN core.`);
