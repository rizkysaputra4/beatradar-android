"""Exports the app's English strings as a language-pack template for translators.

    python tools/export_strings.py > en.json                 # every string, English
    python tools/export_strings.py --merge id.json > id.json  # keep id.json's translations, add strings new since
    python tools/export_strings.py --prompt "Indonesian" > prompt.txt   # tools/translate-prompt.txt with the strings in
    python tools/export_strings.py --check id.json           # a translated pack: keys, placeholders, plurals, JSON
    python tools/export_strings.py --sql id id.json | psql "$DATABASE_URL"   # checks it, then stores it as language `id`
    python tools/export_strings.py --inventory               # Markdown tables for docs/LANGUAGES.md: prefixes, packs

Translate the values (keep %1$s / %2$d placeholders, and plural keys like `name#one`, `name#other`; add the forms the
language needs, e.g. `name#few`), set "name" and "nativeName", then upload it in the backoffice (Languages) or with
PUT /v1/admin/languages/<code>. A string left out of a pack shows in English.
"""
import argparse
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "values"
# Shown by the system (launcher, widget picker, Quick Settings), not through the app's packs.
SKIP = {"app_name", "widget_description", "tile_label"}
PROMPT = pathlib.Path(__file__).resolve().parent / "translate-prompt.txt"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]|%%")
PLURAL_CATEGORIES = {"zero", "one", "two", "few", "many", "other"}
LANGUAGE_SYNC = RES.parent.parent / "java" / "com" / "boimin" / "beatradar" / "i18n" / "LanguageSync.kt"
PACKS = pathlib.Path(__file__).resolve().parent / "packs"


def android_text(el):
    """The string as the app sees it: Android's escapes undone (\\' \\" \\n \\@ \\?), outer quotes dropped."""
    text = "".join(el.itertext())
    if len(text) >= 2 and text[0] == text[-1] == '"':
        text = text[1:-1]
    text = re.sub(r"\\(['\"@?])", r"\1", text)
    return text.replace("\\n", "\n").replace("\\t", "\t")


def english():
    strings = {}
    for f in sorted(RES.glob("strings*.xml")):
        root = ET.parse(f).getroot()
        for el in root:
            name = el.get("name")
            if name in SKIP or el.get("translatable") == "false":
                continue
            if el.tag == "string":
                strings[name] = android_text(el)
            elif el.tag == "plurals":
                for item in el.findall("item"):
                    strings[f"{name}#{item.get('quantity')}"] = android_text(item)
    return strings


def check(path):
    """Problems in a translated pack, compared with the app's English. Empty when it's fine to upload."""
    source = english()
    try:
        pack = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        return [f"not valid JSON: {e}"]
    problems = []
    for field in ("name", "nativeName"):
        if not str(pack.get(field, "")).strip():
            problems.append(f'"{field}" is empty')
    strings = pack.get("strings")
    if not isinstance(strings, dict) or not strings:
        return problems + ['"strings" is missing or empty']
    plural_names = {k.split("#")[0] for k in source if "#" in k}
    for key, text in strings.items():
        name, _, category = key.partition("#")
        if not isinstance(text, str):
            problems.append(f"{key}: not a string")
            continue
        if category:
            if name not in plural_names:
                problems.append(f"{key}: no such plural in the app")
                continue
            if category not in PLURAL_CATEGORIES:
                problems.append(f"{key}: unknown plural category '{category}'")
                continue
            english_text = source.get(f"{name}#other")
        else:
            if key not in source:
                problems.append(f"{key}: no such string in the app")
                continue
            english_text = source[key]
        if sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(english_text)):
            problems.append(f"{key}: placeholders {PLACEHOLDER.findall(text)} should be {PLACEHOLDER.findall(english_text)}")
    for name in sorted(plural_names):
        if f"{name}#other" not in strings and any(k.split("#")[0] == name for k in strings):
            problems.append(f"{name}#other: missing (every plural needs it)")
    missing = [k for k in source if "#" not in k and k not in strings]
    missing += [f"{n}#other" for n in sorted(plural_names) if not any(k.split("#")[0] == n for k in strings)]
    if missing:
        problems.append(f"{len(missing)} strings not translated (they show in English): " + ", ".join(missing[:10]) + (" …" if len(missing) > 10 else ""))
    return problems


def inventory():
    """Markdown tables: what the app has to translate, by key prefix (= download chunk), and how far each pack is."""
    sync = LANGUAGE_SYNC.read_text(encoding="utf-8")
    chunk_list = lambda name: re.findall(r'"([a-z]+)"', re.search(rf"val {name} = listOf\(([^)]*)\)", sync).group(1))
    core, order = chunk_list("CORE"), chunk_list("ORDER")
    rows = {}
    for f in sorted(RES.glob("strings*.xml")):
        for el in ET.parse(f).getroot():
            name = el.get("name")
            if name in SKIP or el.get("translatable") == "false":
                continue
            r = rows.setdefault(name.split("_")[0], {"strings": 0, "plurals": 0, "files": set()})
            r["strings" if el.tag == "string" else "plurals"] += 1
            r["files"].add(f.name)
    print("| Prefix (chunk) | Strings | Plurals | Source file | Download |")
    print("|---|---:|---:|---|---|")
    for prefix in sorted(rows, key=lambda k: -(rows[k]["strings"] + rows[k]["plurals"])):
        r = rows[prefix]
        when = "CORE" if prefix in core else f"ORDER #{order.index(prefix) + 1}" if prefix in order else "**last (not listed)**"
        print(f"| `{prefix}_` | {r['strings']} | {r['plurals']} | {', '.join(sorted(r['files']))} | {when} |")
    total_s, total_p = sum(r["strings"] for r in rows.values()), sum(r["plurals"] for r in rows.values())
    print(f"| **total** | **{total_s}** | **{total_p}** | | |")
    print()
    source = english()
    names = {k.split("#")[0] for k in source}
    print("| Code | Language | Native name | Plural forms | Missing names |")
    print("|---|---|---|---|---:|")
    for f in sorted(PACKS.glob("*.json")):
        pack = json.loads(f.read_text(encoding="utf-8"))
        have = {k.split("#")[0] for k in pack.get("strings", {})}
        forms = sorted({k.split("#")[1] for k in pack.get("strings", {}) if "#" in k}, key=["zero", "one", "two", "few", "many", "other"].index)
        print(f"| `{f.stem}` | {pack.get('name')} | {pack.get('nativeName')} | {' '.join(forms)} | {len(names - have)} |")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--merge", help="an existing pack: its translations are kept, strings the app no longer has are dropped")
    p.add_argument("--prompt", nargs="?", const="", metavar="LANGUAGE", help="print the translation prompt for LANGUAGE (blank: any)")
    p.add_argument("--check", metavar="PACK", help="check a translated pack before uploading it")
    p.add_argument("--sql", nargs=2, metavar=("CODE", "PACK"), help="check PACK, then print SQL that stores it as language CODE")
    p.add_argument("--inventory", action="store_true", help="print the key-prefix and pack tables kept in docs/LANGUAGES.md")
    args = p.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    if args.inventory:
        inventory()
        return

    if args.check:
        problems = check(args.check)
        for line in problems:
            print(line)
        if not problems:
            print("OK: ready to upload")
        sys.exit(1 if any("not translated" not in line for line in problems) else 0)

    if args.sql:
        code, path = args.sql
        if not re.fullmatch(r"[A-Za-z0-9-]{2,12}", code) or code.lower() == "en":
            sys.exit("CODE must be a language tag like id or pt-BR (English is built into the app)")
        problems = [line for line in check(path) if "not translated" not in line]
        if problems:
            sys.exit("Not stored, fix these first:\n" + "\n".join(problems))
        pack = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        strings = json.dumps(pack["strings"], ensure_ascii=False, sort_keys=True)
        tag = "$beatradar_pack$"
        if tag in strings or tag in pack["name"] or tag in pack["nativeName"]:
            sys.exit(f"the pack contains {tag}, which this script uses for quoting")
        q = lambda v: tag + v + tag
        # Same as the backoffice upload: insert, or replace and raise the version so phones fetch it again.
        print("\\set ON_ERROR_STOP on")
        print("begin;")
        print(
            "insert into language_packs (code, name, native_name, strings, published)\n"
            f"values ({q(code)}, {q(pack['name'].strip())}, {q(pack['nativeName'].strip())}, {q(strings)}::jsonb, true)\n"
            "on conflict (code) do update set name = excluded.name, native_name = excluded.native_name,\n"
            "  strings = excluded.strings, published = true, version = language_packs.version + 1, updated_at = now()\n"
            "returning code, native_name, version, (select count(*) from jsonb_object_keys(strings)) as strings;"
        )
        print("commit;")
        return

    if args.prompt is not None:
        source = json.dumps({"name": "", "nativeName": "", "strings": english()}, ensure_ascii=False, indent=2, sort_keys=True)
        language = args.prompt.strip()
        text = PROMPT.read_text(encoding="utf-8").replace("{{SOURCE_JSON}}", source)
        if language:
            text = text.replace("{{LANGUAGE}}", language)
        else:
            header = "TARGET LANGUAGE: ________________  (write the language here, e.g. Indonesian)\n\n"
            text = header + text.replace("{{LANGUAGE}}", "the target language")
        print(text)
        return

    source = english()
    pack = {"name": "", "nativeName": "", "strings": source}
    if args.merge:
        old = json.loads(pathlib.Path(args.merge).read_text(encoding="utf-8"))
        names = {k.split("#")[0] for k in source}
        kept = {k: v for k, v in old.get("strings", {}).items() if k.split("#")[0] in names}
        pack = {"name": old.get("name", ""), "nativeName": old.get("nativeName", ""), "strings": {**source, **kept}}
        missing = [k for k in source if k not in kept]
        print(f"{len(kept)} translated, {len(missing)} still in English", file=sys.stderr)
    json.dump(pack, sys.stdout, ensure_ascii=False, indent=2, sort_keys=True)
    print()


if __name__ == "__main__":
    main()
