"""
I dati degli screenshot dell'App Store (D70, D73): didascalie, testi dell'interfaccia e
contenuti delle note, lingua per lingua. Scrive `tools/shots/shots.json`, che `render.mjs`
passa alla pagina `page.html`.

Uso, dalla radice del repository:

    python3 tools/shots/build.py
    (cd tools/shots && npm install)      # solo la prima volta: i caratteri
    PLAYWRIGHT_MODULE=… CHROME=… node tools/shots/render.mjs

Le didascalie vengono da `tools/brand/store_texts.py`, i testi dell'interfaccia dal
catalogo di Xcode: lo screenshot dice le stesse parole dell'app, parola per parola. Qui si
scrivono solo i contenuti delle note, che devono sembrare scritti da una persona di quella
lingua e non tradotti.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "brand"))
from store_texts import SHOT_LANGS, SHOTS  # noqa: E402

CATALOG = os.path.join(ROOT, "iosApp", "Shared", "Localizable.xcstrings")
UI_KEYS = {
    "done": "Done",
    "notes": "Notes",
    "search": "Search",
    "write": "Write",
    "sort": "Sort %lld",
    "send_to": "Send to…",
    "send": "Send",
    "keep": "Keep",
    "delete": "Delete",
    "to_sort": "%lld to sort",
    "close": "Close",
    "saved": "Saved to Instink.",
}

# I contenuti delle note. Ogni nota è una lista di righe scritte a mano; `idea` dice se la
# ricerca dello screenshot 5 la trova. Niente nomi veri, numeri o indirizzi (D70).
CONTENT = {
    "en": dict(
        s1=["call mum", "re: Sunday"],
        s3=["podcast idea:", "slow mornings"],
        s4=["bike to work", "on Fridays"],
        query="idea",
        hits=[["idea: bike to work", "on Fridays"], ["podcast idea:", "slow mornings"],
              ["gift idea for Anna", "→ a book?"], ["app idea: a note", "on my wrist"]],
        rel=["2 min. ago", "1 hr. ago", "yesterday", "2 days ago"],
        lock_date="Thursday, September 24",
        siri_said="“Buy flowers for Saturday”",
        apps=["Calendar", "Photos", "Camera", "Maps", "Weather", "Clock", "Music", "Instink"],
        share_apps=["Notes", "Notion", "Keep", "Mail"],
        share_rows=["Copy", "Save to Files"],
        share_kind="Text and image",
    ),
    "it": dict(
        s1=["chiamare mamma", "per domenica"],
        s3=["idea podcast:", "mattine lente"],
        s4=["in bici al lavoro", "il venerdì"],
        query="idea",
        hits=[["idea: in bici al", "lavoro il venerdì"], ["idea podcast:", "mattine lente"],
              ["idea regalo Anna", "→ un libro?"], ["idea app: una nota", "sul polso"]],
        rel=["2 min fa", "1 ora fa", "ieri", "2 giorni fa"],
        lock_date="giovedì 24 settembre",
        siri_said="«Comprare fiori per sabato»",
        apps=["Calendario", "Foto", "Fotocamera", "Mappe", "Meteo", "Orologio", "Musica", "Instink"],
        share_apps=["Note", "Notion", "Keep", "Mail"],
        share_rows=["Copia", "Salva su File"],
        share_kind="Testo e immagine",
    ),
    "es": dict(
        s1=["llamar a mamá", "por el domingo"],
        s3=["idea de podcast:", "mañanas lentas"],
        s4=["en bici al trabajo", "los viernes"],
        query="idea",
        hits=[["idea: en bici al", "trabajo los viernes"], ["idea de podcast:", "mañanas lentas"],
              ["idea de regalo", "para Ana → ¿libro?"], ["idea de app: una", "nota en la muñeca"]],
        rel=["hace 2 min", "hace 1 h", "ayer", "hace 2 días"],
        lock_date="jueves, 24 de septiembre",
        siri_said="«Comprar flores para el sábado»",
        apps=["Calendario", "Fotos", "Cámara", "Mapas", "Tiempo", "Reloj", "Música", "Instink"],
        share_apps=["Notas", "Notion", "Keep", "Mail"],
        share_rows=["Copiar", "Guardar en Archivos"],
        share_kind="Texto e imagen",
    ),
    "de": dict(
        s1=["Mama anrufen", "wegen Sonntag"],
        s3=["Podcast-Idee:", "langsame Morgen"],
        s4=["freitags mit dem", "Rad zur Arbeit"],
        query="Idee",
        hits=[["Idee: freitags mit", "dem Rad zur Arbeit"], ["Podcast-Idee:", "langsame Morgen"],
              ["Geschenk-Idee für", "Anna → ein Buch?"], ["App-Idee: Notiz", "am Handgelenk"]],
        rel=["vor 2 Min.", "vor 1 Std.", "gestern", "vor 2 Tagen"],
        lock_date="Donnerstag, 24. September",
        siri_said="„Blumen für Samstag kaufen“",
        apps=["Kalender", "Fotos", "Kamera", "Karten", "Wetter", "Uhr", "Musik", "Instink"],
        share_apps=["Notizen", "Notion", "Keep", "Mail"],
        share_rows=["Kopieren", "In „Dateien“ sichern"],
        share_kind="Text und Bild",
    ),
    "fr": dict(
        s1=["appeler maman", "pour dimanche"],
        s3=["idée de podcast :", "matins lents"],
        s4=["au travail à vélo", "le vendredi"],
        query="idée",
        hits=[["idée : au travail", "à vélo le vendredi"], ["idée de podcast :", "matins lents"],
              ["idée cadeau Anna", "→ un livre ?"], ["idée d’app : une", "note au poignet"]],
        rel=["il y a 2 min", "il y a 1 h", "hier", "il y a 2 jours"],
        lock_date="jeudi 24 septembre",
        siri_said="« Acheter des fleurs pour samedi »",
        apps=["Calendrier", "Photos", "Appareil photo", "Plans", "Météo", "Horloge", "Musique", "Instink"],
        share_apps=["Notes", "Notion", "Keep", "Mail"],
        share_rows=["Copier", "Enregistrer dans Fichiers"],
        share_kind="Texte et image",
    ),
    "pt-BR": dict(
        s1=["ligar pra mãe", "sobre domingo"],
        s3=["ideia de podcast:", "manhãs lentas"],
        s4=["de bike pro", "trabalho na sexta"],
        query="ideia",
        hits=[["ideia: de bike pro", "trabalho na sexta"], ["ideia de podcast:", "manhãs lentas"],
              ["ideia de presente", "pra Ana → livro?"], ["ideia de app: nota", "no pulso"]],
        rel=["há 2 min", "há 1 h", "ontem", "há 2 dias"],
        lock_date="quinta-feira, 24 de setembro",
        siri_said="“Comprar flores pro sábado”",
        apps=["Calendário", "Fotos", "Câmera", "Mapas", "Tempo", "Relógio", "Música", "Instink"],
        share_apps=["Notas", "Notion", "Keep", "Mail"],
        share_rows=["Copiar", "Salvar em Arquivos"],
        share_kind="Texto e imagem",
    ),
    "ja": dict(
        s1=["母に電話", "日曜のこと"],
        s3=["ポッドキャスト案", "ゆっくりな朝"],
        s4=["金曜は", "自転車で出勤"],
        query="アイデア",
        hits=[["アイデア：金曜は", "自転車で出勤"], ["番組のアイデア：", "ゆっくりな朝"],
              ["プレゼントの", "アイデア→本？"], ["アプリのアイデア：", "手首のメモ"]],
        rel=["2分前", "1時間前", "昨日", "2日前"],
        lock_date="9月24日 木曜日",
        siri_said="「土曜日に花を買う」",
        apps=["カレンダー", "写真", "カメラ", "マップ", "天気", "時計", "ミュージック", "Instink"],
        share_apps=["メモ", "Notion", "Keep", "メール"],
        share_rows=["コピー", "“ファイル”に保存"],
        share_kind="テキストと画像",
    ),
}


def catalog_lookup():
    with open(CATALOG) as f:
        strings = json.load(f)["strings"]

    def lookup(key, lang):
        if lang == "en":
            return key
        value = strings[key]["localizations"][lang]["stringUnit"]["value"]
        return value

    return lookup


def main():
    lookup = catalog_lookup()
    out = {}
    for index, lang in enumerate(SHOT_LANGS):
        content = CONTENT[lang]
        for name in ("s1", "s3", "s4"):
            for line in content[name]:
                if len(line) > 20:
                    sys.exit(f"{lang}.{name}: «{line}» è troppo lunga per una riga scritta a mano")
        out[lang] = dict(
            captions=[s["caption"][index] for s in SHOTS],
            subs=[s["sub"][index] for s in SHOTS],
            ui={name: lookup(key, lang) for name, key in UI_KEYS.items()},
            **content,
        )
    path = os.path.join(ROOT, "tools", "shots", "shots.json")
    with open(path, "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("tools/shots/shots.json:", ", ".join(out))


if __name__ == "__main__":
    main()
