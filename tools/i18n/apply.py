"""
Scrive le traduzioni di `translations.py` nei cataloghi di Xcode e nei `values-xx` di
Android (D69). Uso, dalla radice: `python3 tools/i18n/apply.py`.

Controlla, prima di scrivere, che ogni traduzione abbia gli stessi segnaposto
dell'originale (%lld, %@, %d, ${applicationName}): un segnaposto perso manda in crash
l'app nel momento in cui quella stringa compare.
"""
import json
import os
import re
import sys
from xml.sax.saxutils import escape

sys.path.insert(0, os.path.dirname(__file__))
from translations import ANDROID_FROM_SHARED, LANGS, PLURALS, T  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PH = re.compile(r"%lld|%@|%d|\$\{applicationName\}")
ANDROID_DIR = {"es": "values-es", "de": "values-de", "fr": "values-fr", "pt-BR": "values-pt", "ja": "values-ja"}


def check():
    bad = []
    for key, values in T.items():
        if len(values) != len(LANGS):
            bad.append(f"{key}: {len(values)} traduzioni invece di {len(LANGS)}")
            continue
        source = key.split(":", 1)[1] if key.startswith(("siri:", "plist:")) else key
        if key.startswith(("android:", "plist:")):
            continue
        for lang, value in zip(LANGS, values):
            if sorted(PH.findall(source)) != sorted(PH.findall(value)):
                bad.append(f"{lang} {key!r}: segnaposto diversi")
    if bad:
        sys.exit("Traduzioni da correggere:\n" + "\n".join(bad))


def unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def write_catalog(path, keys):
    with open(path) as f:
        catalog = json.load(f)
    for catalog_key, table_key in keys:
        entry = catalog["strings"].setdefault(catalog_key, {})
        localizations = entry.setdefault("localizations", {})
        for lang, value in zip(LANGS, T[table_key]):
            localizations[lang] = unit(value)
    with open(path, "w") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False, sort_keys=True)
        f.write("\n")


def android_escape(value):
    # Android vuole gli apostrofi e le virgolette protetti, e niente @ o ? in testa.
    value = escape(value).replace("'", "\\'").replace('"', '\\"')
    if value[:1] in ("@", "?"):
        value = "\\" + value
    return value


def write_android():
    base = os.path.join(ROOT, "androidApp", "src", "main", "res")
    with open(os.path.join(base, "values", "strings.xml")) as f:
        names = re.findall(r'<string name="([^"]+)"', f.read())
    for index, lang in enumerate(LANGS):
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            "<!-- Generato da tools/i18n/apply.py (D69): le traduzioni si cambiano in",
            "     tools/i18n/translations.py, non qui. -->",
            "<resources>",
        ]
        for name in names:
            if name == "app_name":
                continue
            key = ANDROID_FROM_SHARED.get(name, f"android:{name}")
            if key not in T:
                continue  # resta in inglese: meglio l'originale di una traduzione inventata
            lines.append(f'    <string name="{name}">{android_escape(T[key][index])}</string>')
        for name, forms in PLURALS.items():
            one, other = forms[index]
            lines.append(f'    <plurals name="{name}">')
            if one is not None:
                lines.append(f'        <item quantity="one">{android_escape(one)}</item>')
            lines.append(f'        <item quantity="other">{android_escape(other)}</item>')
            lines.append("    </plurals>")
        lines.append("</resources>")
        folder = os.path.join(base, ANDROID_DIR[lang])
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, "strings.xml"), "w") as f:
            f.write("\n".join(lines) + "\n")


def main():
    check()
    # Le chiavi con un prefisso (android:, plist:, siri:) non vanno nel catalogo comune;
    # un testo inglese con dentro i due punti sì.
    shared = [k for k in T if not k.startswith(("android:", "plist:", "siri:"))]
    write_catalog(os.path.join(ROOT, "iosApp/Shared/Localizable.xcstrings"), [(k, k) for k in shared])
    write_catalog(
        os.path.join(ROOT, "iosApp/InkNote/InfoPlist.xcstrings"),
        [(k.split(":", 1)[1], k) for k in T if k.startswith("plist:")],
    )
    write_catalog(
        os.path.join(ROOT, "iosApp/InkNote/AppShortcuts.xcstrings"),
        [(k.split(":", 1)[1], k) for k in T if k.startswith("siri:")],
    )
    write_android()
    print("Traduzioni scritte:", ", ".join(LANGS))


if __name__ == "__main__":
    main()
