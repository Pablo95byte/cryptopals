"""
Il segno di Instink e tutti i suoi formati (D66, D71).

L'icona è un foglio bianco, un po' inclinato, su un campo vermiglio, con sopra il ricciolo
scritto a inchiostro e la goccia (D71): il foglio è quello del widget (D30), e si capisce
senza leggere nessuna lingua.

Il ricciolo è lo stesso del widget e del sito (D30), ma disegnato come inchiostro: sottile
all'attacco, pieno nel corpo, un po' più stretto alla fine — la stessa idea dello spessore
che segue la mano nell'app (D6). Una goccia calda accanto è l'"istinto": l'unico colore.

Uso, dalla radice del repository:

    python3 tools/brand/icons.py            # SVG in design/brand/, icona Android, jobs.json
    node tools/brand/render.mjs             # i PNG, col Chromium di Playwright
    python3 tools/brand/icons.py flatten    # via il canale alfa dove gli store lo rifiutano
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "design", "brand")

PAPER = "#FBF8F1"
PAPER_DEEP = "#F1EADB"
INK = "#1F2430"
NIGHT = "#15171D"
NIGHT_DEEP = "#0C0D11"
CREAM = "#F3ECDD"
SPARK = "#E4572E"
# Il campo dell'icona (D71): il vermiglio del marchio, un filo più chiaro in alto.
FIELD = "#EA5F35"
FIELD_DEEP = "#D9481F"

# Il ricciolo, in coordinate 24×24: lo stesso percorso di Brand.swift e del sito.
SEGMENTS = [
    ((4, 19.5), (7, 18.5), (9, 13), (12, 8)),
    ((12, 8), (14.2, 4.3), (16, 3.7), (16.8, 4.6)),
    ((16.8, 4.6), (17.7, 5.6), (16.4, 7.8), (14.4, 10.2)),
    ((14.4, 10.2), (12.8, 12.1), (11.4, 13.2), (11.4, 14.5)),
    ((11.4, 14.5), (11.4, 15.5), (12.2, 16.0), (13.2, 15.7)),
    ((13.2, 15.7), (14.6, 15.3), (15.8, 14.1), (17.0, 12.5)),
]
# La goccia: dove andrebbe il punto di una "i", in alto a destra della curva finale.
SPARK_AT = (19.2, 9.4)


def bezier(p0, p1, p2, p3, t):
    u = 1 - t
    return (
        u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
        u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
    )


def samples(n_per=60):
    pts = []
    for i, seg in enumerate(SEGMENTS):
        for k in range(n_per + (1 if i == len(SEGMENTS) - 1 else 0)):
            pts.append(bezier(*seg, k / n_per))
    return pts


def width_at(s):
    """Spessore relativo lungo il tratto (s da 0 a 1): attacco sottile, coda un po' stretta.

    Rampe morbide (smoothstep): una rampa lineare lascia uno scalino visibile dove finisce.
    """
    def smooth(x):
        x = min(1.0, max(0.0, x))
        return x * x * (3 - 2 * x)

    attack = 0.22 + 0.78 * smooth(s / 0.32)
    tail = 1.0 - 0.25 * smooth((s - 0.72) / 0.28)
    return attack * tail


def outline(width):
    """Il contorno del tratto come poligono chiuso, più le due punte tonde."""
    pts = samples()
    lengths = [0.0]
    for a, b in zip(pts, pts[1:]):
        lengths.append(lengths[-1] + math.dist(a, b))
    total = lengths[-1]
    left, right = [], []
    for i, p in enumerate(pts):
        a = pts[max(0, i - 1)]
        b = pts[min(len(pts) - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        norm = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / norm, dx / norm
        w = width * width_at(lengths[i] / total) / 2
        left.append((p[0] + nx * w, p[1] + ny * w))
        right.append((p[0] - nx * w, p[1] - ny * w))
    poly = left + right[::-1]
    d = "M" + " L".join(f"{x:.3f},{y:.3f}" for x, y in poly) + " Z"
    start_r = width * width_at(0) / 2
    end_r = width * width_at(1) / 2
    return d, (pts[0], start_r), (pts[-1], end_r)


def mark(width, ink, spark, spark_r):
    d, (s, sr), (e, er) = outline(width)
    parts = [
        f'<path d="{d}" fill="{ink}"/>',
        f'<circle cx="{s[0]:.3f}" cy="{s[1]:.3f}" r="{sr:.3f}" fill="{ink}"/>',
        f'<circle cx="{e[0]:.3f}" cy="{e[1]:.3f}" r="{er:.3f}" fill="{ink}"/>',
    ]
    if spark:
        parts.append(f'<circle cx="{SPARK_AT[0]}" cy="{SPARK_AT[1]}" r="{spark_r}" fill="{spark}"/>')
    return "".join(parts)


# Il segno occupa il centro del quadrato: il ricciolo sta fra x 4..19 e y 3.7..19.5.
CENTER = (11.6, 11.6)


def icon_svg(size, background, ink, spark, scale=0.58, stroke=2.05, spark_r=1.25, radius=0, gradient=None):
    k = size * scale / 15.8  # 15.8 unità: l'altezza del ricciolo
    tx = size / 2 - CENTER[0] * k
    ty = size / 2 - CENTER[1] * k
    defs = ""
    bg = ""
    if background:
        fill = background
        if gradient:
            defs = (
                f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">'
                f'<stop offset="0" stop-color="{gradient[0]}"/><stop offset="1" stop-color="{gradient[1]}"/>'
                f"</linearGradient></defs>"
            )
            fill = "url(#g)"
        bg = f'<rect width="{size}" height="{size}" rx="{radius}" fill="{fill}"/>'
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
        f"{defs}{bg}<g transform=\"translate({tx:.3f},{ty:.3f}) scale({k:.4f})\">"
        f"{mark(stroke, ink, spark, spark_r)}</g></svg>"
    )


# Il foglio, in un quadrato da 1024: 620×660, angoli da 56, inclinato di 7 gradi.
SHEET = dict(x=202, y=182, w=620, h=660, r=56, angle=-7)
# Il ricciolo sul foglio: 27 unità di disegno per 1024 pixel.
SHEET_MARK = 27 * 15.8 / 1024


def sheet_icon_svg(size, field, sheet, ink, spark, stroke=2.05, spark_r=1.25, radius=0, gid="g"):
    """L'icona di D71. [field] è un colore, una coppia per il gradiente, o None (trasparente)."""
    f = size / 1024
    defs, bg = "", ""
    if field:
        fill = field
        if isinstance(field, tuple):
            defs = (
                f'<defs><linearGradient id="{gid}" x1="0" y1="0" x2="0" y2="1">'
                f'<stop offset="0" stop-color="{field[0]}"/><stop offset="1" stop-color="{field[1]}"/>'
                f"</linearGradient></defs>"
            )
            fill = f"url(#{gid})"
        bg = f'<rect width="{size}" height="{size}" rx="{radius}" fill="{fill}"/>'
    c = size / 2
    paper = (
        f'<g transform="rotate({SHEET["angle"]} {c:.2f} {c:.2f})">'
        f'<rect x="{SHEET["x"] * f:.2f}" y="{SHEET["y"] * f:.2f}" width="{SHEET["w"] * f:.2f}" '
        f'height="{SHEET["h"] * f:.2f}" rx="{SHEET["r"] * f:.2f}" fill="{sheet}"/></g>'
    )
    k = size * SHEET_MARK / 15.8
    tx, ty = c - CENTER[0] * k, c - CENTER[1] * k
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
        f"{defs}{bg}{paper}<g transform=\"translate({tx:.3f},{ty:.3f}) scale({k:.4f})\">"
        f"{mark(stroke, ink, spark, spark_r)}</g></svg>"
    )


def sheet_group(size, x, y):
    """L'icona di D71 come gruppo da mettere dentro un'altra immagine, in alto a sinistra in (x, y)."""
    inner = sheet_icon_svg(size, (FIELD, FIELD_DEEP), PAPER, INK, SPARK, radius=size * 0.2237, gid="gi")
    body = inner.split(">", 1)[1].rsplit("</svg>", 1)[0]
    return f'<g transform="translate({x},{y})">{body}</g>'


def feature_svg():
    """La grafica in evidenza del Play Store, 1024×500: l'icona, il nome, la frase (D71)."""
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">'
        '<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{FIELD}"/><stop offset="1" stop-color="{FIELD_DEEP}"/></linearGradient>'
        '<style>@font-face{font-family:IS;src:url("../../site/fonts/InstrumentSans.ttf")}</style></defs>'
        '<rect width="1024" height="500" fill="url(#g)"/>'
        + sheet_group(260, 110, 120).replace(f'fill="url(#gi)"', 'fill="none"') +
        f'<text x="440" y="238" font-family="IS" font-weight="700" font-size="104" fill="{PAPER}" letter-spacing="-3">Instink</text>'
        f'<text x="444" y="306" font-family="IS" font-weight="500" font-size="38" fill="{PAPER}" fill-opacity="0.82">Write on instinct.</text>'
        "</svg>"
    )


def og_svg(title, line):
    """L'immagine delle anteprime dei link (1200×630): WhatsApp, Messaggi, social (D71)."""
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">'
        '<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{FIELD}"/><stop offset="1" stop-color="{FIELD_DEEP}"/></linearGradient>'
        '<style>@font-face{font-family:IS;src:url("../../site/fonts/InstrumentSans.ttf")}</style></defs>'
        '<rect width="1200" height="630" fill="url(#g)"/>'
        + sheet_group(330, 130, 150).replace(f'fill="url(#gi)"', 'fill="none"') +
        f'<text x="540" y="300" font-family="IS" font-weight="700" font-size="112" fill="{PAPER}" letter-spacing="-3">{title}</text>'
        f'<text x="545" y="372" font-family="IS" font-weight="500" font-size="40" fill="{PAPER}" fill-opacity="0.82">{line}</text>'
        "</svg>"
    )


def android_foreground_xml(monochrome=False):
    """Il primo piano dell'icona adattiva (D33, D71).

    A colori: il foglio inclinato col ricciolo, dentro il cerchio sicuro di 66 dp dei 108; il
    vermiglio è lo sfondo, in `ic_launcher_background`. Monocromatica: solo il ricciolo, che il
    sistema colora; un foglio pieno col ricciolo ritagliato darebbe un buco doppio dove il
    tratto si incrocia.
    """
    d, (s, sr), (e, er) = outline(2.05)

    def circle(cx, cy, r):
        return f"M{cx - r:.3f},{cy:.3f}a{r:.3f},{r:.3f} 0 1,0 {2 * r:.3f},0a{r:.3f},{r:.3f} 0 1,0 {-2 * r:.3f},0"

    body = d + " " + circle(s[0], s[1], sr) + " " + circle(e[0], e[1], er)
    if monochrome:
        k = 60 / 15.8
        ink, spark, sheet = "#FFFFFFFF", "#FFFFFFFF", ""
        note = "monocromatica: il sistema la colora, e la goccia resta un punto"
    else:
        # I 72 dp visibili corrispondono ai 1024 pixel dell'icona di iOS, ridotti del 10%:
        # così gli angoli del foglio inclinato restano dentro il cerchio sicuro di 66 dp.
        f = 72 / 1024 * 0.9
        k = 27 * f
        w, h, r = SHEET["w"] * f, SHEET["h"] * f, SHEET["r"] * f
        x, y = 54 + (SHEET["x"] - 512) * f, 54 + (SHEET["y"] - 512) * f
        rect = (
            f"M{x + r:.3f},{y:.3f}h{w - 2 * r:.3f}a{r:.3f},{r:.3f} 0 0,1 {r:.3f},{r:.3f}"
            f"v{h - 2 * r:.3f}a{r:.3f},{r:.3f} 0 0,1 {-r:.3f},{r:.3f}h{-(w - 2 * r):.3f}"
            f"a{r:.3f},{r:.3f} 0 0,1 {-r:.3f},{-r:.3f}v{-(h - 2 * r):.3f}a{r:.3f},{r:.3f} 0 0,1 {r:.3f},{-r:.3f}z"
        )
        ink, spark = "#FF1F2430", "#FFE4572E"
        sheet = f"""    <group
        android:pivotX="54"
        android:pivotY="54"
        android:rotation="{SHEET["angle"]}">
        <path
            android:fillColor="#FFFBF8F1"
            android:pathData="{rect}" />
    </group>
"""
        note = "a colori: il foglio col ricciolo; il vermiglio sta nello sfondo"
    tx, ty = 54 - CENTER[0] * k, 54 - CENTER[1] * k
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!--
    L'icona dell'app, {note} (D66, D71).
    Generata da tools/brand/icons.py: non si modifica a mano.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{sheet}    <group
        android:scaleX="{k:.4f}"
        android:scaleY="{k:.4f}"
        android:translateX="{tx:.3f}"
        android:translateY="{ty:.3f}">
        <path
            android:fillColor="{ink}"
            android:pathData="{body}" />
        <path
            android:fillColor="{spark}"
            android:pathData="{circle(SPARK_AT[0], SPARK_AT[1], 1.25)}" />
    </group>
</vector>
"""


def main():
    os.makedirs(OUT, exist_ok=True)
    svgs = {
        # L'icona di iOS e degli store (D71): quadrato pieno, senza angoli (li mette il sistema).
        "icon-light.svg": sheet_icon_svg(1024, (FIELD, FIELD_DEEP), PAPER, INK, SPARK),
        # iOS 18, icona scura: fondo trasparente, il sistema mette il suo nero. Il vermiglio
        # passa sul foglio, che su nero non abbaglia; il ricciolo e il punto in crema.
        "icon-dark.svg": sheet_icon_svg(1024, None, FIELD, CREAM, CREAM),
        # iOS 18, icona colorata dall'utente: scala di grigi su trasparente. Il foglio prende
        # il colore scelto, il ricciolo resta scuro.
        "icon-tinted.svg": sheet_icon_svg(1024, None, "#FFFFFF", "#3A3A3A", "#8A8A8A"),
        # Il segno da solo, per il sito e le presentazioni.
        "mark.svg": icon_svg(512, None, INK, SPARK),
        # Favicon: l'icona in piccolo, col tratto più spesso perché si legga a 16 pixel.
        "favicon.svg": sheet_icon_svg(64, (FIELD, FIELD_DEEP), PAPER, INK, SPARK, stroke=2.6, spark_r=1.6, radius=14),
        "feature-graphic.svg": feature_svg(),
        "og-en.svg": og_svg("Instink", "Write on instinct."),
        "og-it.svg": og_svg("Instink", "Scrivi d'istinto."),
    }
    for name, svg in svgs.items():
        with open(os.path.join(OUT, name), "w") as f:
            f.write(svg)

    res = os.path.join(ROOT, "androidApp", "src", "main", "res", "drawable")
    with open(os.path.join(res, "ic_launcher_foreground.xml"), "w") as f:
        f.write(android_foreground_xml())
    with open(os.path.join(res, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(android_foreground_xml(monochrome=True))

    ios = "iosApp/InkNote/Assets.xcassets/AppIcon.appiconset"
    jobs = [
        # iOS: una sola immagine da 1024, più le due varianti di iOS 18.
        ("icon-light.svg", f"{ios}/AppIcon.png", 1024, 1024, False),
        ("icon-dark.svg", f"{ios}/AppIcon-Dark.png", 1024, 1024, True),
        ("icon-tinted.svg", f"{ios}/AppIcon-Tinted.png", 1024, 1024, True),
        # Gli store.
        ("icon-light.svg", "design/brand/store/app-store-1024.png", 1024, 1024, False),
        ("icon-light.svg", "design/brand/store/play-store-512.png", 512, 512, False),
        ("feature-graphic.svg", "design/brand/store/play-feature-graphic-1024x500.png", 1024, 500, False),
        # Il sito.
        ("icon-light.svg", "site/apple-touch-icon.png", 180, 180, False),
        ("icon-light.svg", "site/icon-512.png", 512, 512, False),
        ("og-en.svg", "site/og.png", 1200, 630, False),
        ("og-it.svg", "site/it/og.png", 1200, 630, False),
    ]
    with open(os.path.join(OUT, "jobs.json"), "w") as f:
        json.dump([dict(svg=f"design/brand/{a}", out=b, w=w, h=h, alpha=t) for a, b, w, h, t in jobs], f, indent=1)
    # La favicon del sito è l'SVG stesso.
    with open(os.path.join(ROOT, "site", "favicon.svg"), "w") as f:
        f.write(svgs["favicon.svg"])


def flatten():
    """Dopo il disegno: via il canale alfa dove gli store lo rifiutano."""
    from PIL import Image

    with open(os.path.join(OUT, "jobs.json")) as f:
        for job in json.load(f):
            if job["alpha"]:
                continue
            path = os.path.join(ROOT, job["out"])
            Image.open(path).convert("RGB").save(path, optimize=True)


if __name__ == "__main__":
    import sys

    flatten() if sys.argv[1:] == ["flatten"] else main()
