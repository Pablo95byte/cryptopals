"""
Il segno di Instink e tutti i suoi formati (D66).

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


def feature_svg():
    """La grafica in evidenza del Play Store, 1024×500: il segno, il nome, la frase."""
    k = 280 / 15.8
    tx, ty = 245 - CENTER[0] * k, 250 - CENTER[1] * k
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">'
        '<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{PAPER}"/><stop offset="1" stop-color="{PAPER_DEEP}"/></linearGradient>'
        '<style>@font-face{font-family:IS;src:url("../../site/fonts/InstrumentSans.ttf")}</style></defs>'
        '<rect width="1024" height="500" fill="url(#g)"/>'
        f'<g transform="translate({tx:.2f},{ty:.2f}) scale({k:.3f})">{mark(2.05, INK, SPARK, 1.25)}</g>'
        f'<text x="470" y="238" font-family="IS" font-weight="700" font-size="104" fill="{INK}" letter-spacing="-3">Instink</text>'
        f'<text x="474" y="306" font-family="IS" font-weight="500" font-size="38" fill="#6F685B">Write on instinct.</text>'
        "</svg>"
    )


def og_svg(title, line):
    """L'immagine delle anteprime dei link (1200×630): WhatsApp, Messaggi, social."""
    k = 330 / 15.8
    tx, ty = 290 - CENTER[0] * k, 315 - CENTER[1] * k
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">'
        '<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{PAPER}"/><stop offset="1" stop-color="{PAPER_DEEP}"/></linearGradient>'
        '<style>@font-face{font-family:IS;src:url("../../site/fonts/InstrumentSans.ttf")}</style></defs>'
        '<rect width="1200" height="630" fill="url(#g)"/>'
        f'<g transform="translate({tx:.2f},{ty:.2f}) scale({k:.3f})">{mark(2.05, INK, SPARK, 1.25)}</g>'
        f'<text x="540" y="300" font-family="IS" font-weight="700" font-size="112" fill="{INK}" letter-spacing="-3">{title}</text>'
        f'<text x="545" y="372" font-family="IS" font-weight="500" font-size="40" fill="#6F685B">{line}</text>'
        "</svg>"
    )


def android_foreground_xml(monochrome=False):
    """Il primo piano dell'icona adattiva: il segno nei 66 dp centrali dei 108 (D33)."""
    d, (s, sr), (e, er) = outline(2.05)
    k = 60 / 15.8  # un po' meno dei 66 dp sicuri: il launcher ritaglia a cerchio
    tx, ty = 54 - CENTER[0] * k, 54 - CENTER[1] * k
    ink = "#FFFFFFFF" if monochrome else "#FF1F2430"
    spark = "#FFFFFFFF" if monochrome else "#FFE4572E"

    def circle(cx, cy, r):
        return f"M{cx - r:.3f},{cy:.3f}a{r:.3f},{r:.3f} 0 1,0 {2 * r:.3f},0a{r:.3f},{r:.3f} 0 1,0 {-2 * r:.3f},0"

    body = d + " " + circle(s[0], s[1], sr) + " " + circle(e[0], e[1], er)
    note = "monocromatica: il sistema la colora, e la goccia resta un punto" if monochrome else "a colori"
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!--
    L'icona dell'app, {note} (D66). Generata da tools/brand/icons.py: non si modifica a mano.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
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
        # L'icona di iOS e dello store: quadrato pieno, senza angoli (li mette il sistema).
        "icon-light.svg": icon_svg(1024, PAPER, INK, SPARK, gradient=(PAPER, PAPER_DEEP)),
        # iOS 18, icona scura: fondo trasparente, il sistema mette il suo nero.
        "icon-dark.svg": icon_svg(1024, None, CREAM, SPARK),
        # iOS 18, icona colorata dall'utente: scala di grigi su trasparente.
        "icon-tinted.svg": icon_svg(1024, None, "#FFFFFF", "#9A9A9A"),
        # Il segno da solo, per il sito e le presentazioni.
        "mark.svg": icon_svg(512, None, INK, SPARK),
        # Favicon: il quadrato arrotondato, visibile anche sulle schede scure.
        "favicon.svg": icon_svg(64, PAPER, INK, SPARK, scale=0.74, stroke=2.4, spark_r=1.5, radius=14),
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
