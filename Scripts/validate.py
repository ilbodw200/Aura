"""Source checks usable on Windows. This is NOT an Xcode build or an XCTest run.

pip install --target .tools/python tree-sitter tree-sitter-swift pyyaml
py Scripts/validate.py
"""
import json
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / ".tools/python"))
import yaml
from tree_sitter import Language, Parser
import tree_sitter_swift

parser = Parser(Language(tree_sitter_swift.language()))
failures = []
files = list(ROOT.glob("Aura/**/*.swift")) + list(ROOT.glob("Shared/**/*.swift")) + list(ROOT.glob("Tunnel/**/*.swift")) + list(ROOT.glob("Tests/**/*.swift"))
for path in files:
    source = path.read_bytes()
    tree = parser.parse(source)
    def visit(node):
        if node.type == "ERROR" or node.is_missing:
            # A parser grammar is not a compiler. Report every parse error.
            failures.append(f"{path.relative_to(ROOT)}:{node.start_point.row + 1}: {node.type}: {source[node.start_byte:node.end_byte][:100]!r}")
        for child in node.children:
            visit(child)
    visit(tree.root_node)

for path in ROOT.glob("Config/*"):
    try:
        ET.parse(path)
    except ET.ParseError as error:
        failures.append(f"{path}: {error}")

spec = yaml.safe_load((ROOT / "project.yml").read_text(encoding="utf-8"))
for name, target in spec["targets"].items():
    for directory in target.get("sources", []):
        if not (ROOT / directory).is_dir():
            failures.append(f"{name}: missing sources: {directory}")
    settings = target.get("settings", {}).get("base", {})
    for key in ("INFOPLIST_FILE", "CODE_SIGN_ENTITLEMENTS"):
        if key in settings and not (ROOT / settings[key]).is_file():
            failures.append(f"{name}: missing {settings[key]}")
for path in ROOT.glob("Aura/**/*.json"):
    json.loads(path.read_text(encoding="utf-8"))

if failures:
    print("\n".join(failures))
    sys.exit(1)
print(f"PASS: Swift syntax trees ({len(files)} files), XML property lists, assets JSON, XcodeGen source paths.")
print("Not checked here: Swift type checking, generated Libbox API compatibility, linking, XCTest, simulator UI, device VPN.")
