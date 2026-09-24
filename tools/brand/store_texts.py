"""
I testi degli store per Instink (D67), con i limiti di caratteri controllati.

Uso: python3 tools/brand/store_texts.py → riscrive lancio/STORE.md, e si ferma se un campo
supera il suo limite. I testi si cambiano qui, non nel file generato.
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

LIMITS = {
    "name": 30, "subtitle": 30, "keywords": 100, "promo": 170, "description": 4000,
    "whats_new": 4000, "play_title": 30, "play_short": 80, "play_full": 4000,
}

EN = dict(
    name="Instink: Handwritten Notes",
    subtitle="Jot ideas fast, by hand",
    keywords="instinct,quick,widget,sticky,memo,notepad,scribble,pen,write,journal,voice,dictate,capture,ocr,idea",
    promo=(
        "New: dictate a note without unlocking. Just ask Siri to add a note to Instink. "
        "And your handwriting is now searchable, read right on your iPhone."
    ),
    description="""An idea shows up. By the time you've unlocked your phone, found your notes app and opened a new page, it's gone.

Instink is a blank sheet on your Home Screen. Tap it and you're already writing — by hand, in your own handwriting. Tap Done and you're back where you were. One second.

WHY PEOPLE KEEP IT
• Ready in under half a second. No account, no sign-up, nothing to choose before you write.
• Your handwriting, not a font. Finger or Apple Pencil, with ink that follows your hand.
• Saved from the very first stroke. Put the phone away mid-sentence: the note is already safe.
• Hands busy? Type, snap a photo or record your voice from the same sheet.
• Phone locked? Ask Siri to add a note to Instink and dictate it. No unlocking.

EVERY WAY IN
Home Screen and Lock Screen widgets, Control Center, the Action button, Back Tap and Shortcuts. Use the one closest to your thumb.

THEN, WHEN YOU HAVE A MINUTE
• Sort new notes like cards: swipe right to send, left to keep, down to delete. Ten seconds.
• Send any note to Notion, Notes, Google Keep, Obsidian, Mail or Messages — as text, with your handwriting attached.
• Search your handwriting. Instink reads it on your device.
• Wrote "dentist tomorrow at 10"? Instink offers to put it in your calendar.
• Once a day an old note resurfaces — "a week ago today" — so good ideas don't get buried.
• At night the sheet turns dark, so it won't blind you in bed.

PRIVATE BY DESIGN
No account. No ads. No tracking. Your notes stay on your device and never pass through a server of ours. Handwriting and voice are recognized on your iPhone.

WHAT INSTINK IS NOT
It's not a notebook with folders and pages. It's the pen for the notes system you already use.

Questions or ideas: hello@instink.app — a real person reads every message.""",
    whats_new="""• Dictate a note to Siri, even with your phone locked.
• Record voice notes from the sheet; they're transcribed on your device.
• Your handwriting is now searchable.
• A fresh icon.""",
    play_title="Instink: Handwritten Notes",
    play_short="A blank sheet on your home screen. Tap, write by hand, done. No account.",
    play_full="""An idea shows up. By the time you've unlocked your phone, found your notes app and opened a new page, it's gone.

Instink is a blank sheet on your home screen. Tap it and you're already writing — by hand, in your own handwriting. Tap Done and you're back where you were. One second.

WRITE WITHOUT UNLOCKING
Add the Instink tile to Quick Settings: swipe down on the lock screen, tap, write. The sheet shows up over the lock screen and is write-only — nobody who picks up your phone can read your notes.

WHY PEOPLE KEEP IT
• Ready in under half a second. No account, no sign-up, nothing to choose before you write.
• Your handwriting, not a font. Ink that follows your finger or stylus.
• Saved from the very first stroke. Put the phone away mid-sentence: the note is already safe.
• Hands busy? Type or snap a photo from the same sheet.
• On Samsung phones, open it with a double press of the side key.

THEN, WHEN YOU HAVE A MINUTE
• Sort new notes like cards: swipe right to send, left to keep, down to delete.
• Send any note to Google Keep, Notion, Obsidian, Gmail or any app — as text, with your handwriting attached.
• Once a day an old note resurfaces — "a week ago today" — so good ideas don't get buried.
• At night the sheet turns dark, so it won't blind you in bed.

PRIVATE BY DESIGN
No account. No ads. No tracking. Your notes stay on your phone and never pass through a server of ours.

WHAT INSTINK IS NOT
It's not a notebook with folders and pages. It's the pen for the notes system you already use.

Questions or ideas: hello@instink.app""",
)

IT = dict(
    name="Instink: Note scritte a mano",
    subtitle="Fissa le idee al volo, a mano",
    keywords="istinto,veloce,widget,appunti,memo,blocco,penna,scrivere,scarabocchi,diario,voce,dettare,promemoria",
    promo=(
        "Novità: detta una nota senza sbloccare, basta chiedere a Siri di aggiungerla a Instink. "
        "E la tua scrittura ora si cerca, letta direttamente sull'iPhone."
    ),
    description="""Ti viene un'idea. Il tempo di sbloccare il telefono, cercare l'app delle note e aprire una pagina nuova, e non c'è più.

Instink è un foglio bianco sulla schermata Home. Lo tocchi e stai già scrivendo — a mano, con la tua calligrafia. Tocchi Fatto e sei di nuovo dove eri. Un secondo.

PERCHÉ NON LA TOGLI PIÙ
• Pronta in meno di mezzo secondo. Nessun account, nessuna registrazione, niente da scegliere prima di scrivere.
• La tua calligrafia, non un font. Col dito o con l'Apple Pencil, con un inchiostro che segue la mano.
• Salvata dal primo tratto. Rimetti il telefono in tasca a metà frase: la nota è già al sicuro.
• Mani occupate? Digita, scatta una foto o registra la voce dallo stesso foglio.
• Telefono bloccato? Chiedi a Siri di aggiungere una nota a Instink e dettala. Senza sbloccare.

TUTTI GLI INGRESSI
Widget della schermata Home e di quella di blocco, Centro di Controllo, tasto Azione, Tocca il retro e Comandi rapidi. Usa quello più vicino al pollice.

POI, QUANDO HAI UN MINUTO
• Smista le note nuove come carte: a destra le mandi, a sinistra le tieni, in basso le butti. Dieci secondi.
• Manda qualunque nota a Notion, Note, Google Keep, Obsidian, Mail o Messaggi — come testo, con la tua scrittura allegata.
• Cerca nella tua scrittura. Instink la legge sul dispositivo.
• Hai scritto "dentista domani alle 10"? Instink ti propone di metterlo in calendario.
• Una volta al giorno torna a galla una nota vecchia — "una settimana fa, oggi" — perché le idee buone non finiscano sepolte.
• Di notte il foglio si scurisce, e a letto non ti acceca.

PRIVATA PER COSTRUZIONE
Nessun account. Nessuna pubblicità. Nessun tracciamento. Le note restano sul dispositivo e non passano mai da un nostro server. Scrittura e voce si riconoscono sull'iPhone.

COSA NON È
Non è un quaderno con cartelle e pagine. È la penna del sistema di note che usi già.

Domande o idee: hello@instink.app — ogni messaggio lo legge una persona.""",
    whats_new="""• Detta una nota a Siri, anche a telefono bloccato.
• Registra note vocali dal foglio: si trascrivono sul dispositivo.
• La tua scrittura ora si può cercare.
• Un'icona nuova.""",
    play_title="Instink: Note scritte a mano",
    play_short="Un foglio bianco sulla home. Tocchi, scrivi a mano, fatto. Senza account.",
    play_full="""Ti viene un'idea. Il tempo di sbloccare il telefono, cercare l'app delle note e aprire una pagina nuova, e non c'è più.

Instink è un foglio bianco sulla home. Lo tocchi e stai già scrivendo — a mano, con la tua calligrafia. Tocchi Fatto e sei di nuovo dove eri. Un secondo.

SCRIVI SENZA SBLOCCARE
Aggiungi il riquadro di Instink alle impostazioni rapide: scorri giù dalla schermata di blocco, tocchi, scrivi. Il foglio compare sopra il blocco ed è in sola scrittura — chi raccoglie il telefono non può leggere le tue note.

PERCHÉ NON LA TOGLI PIÙ
• Pronta in meno di mezzo secondo. Nessun account, nessuna registrazione, niente da scegliere prima di scrivere.
• La tua calligrafia, non un font. Un inchiostro che segue il dito o il pennino.
• Salvata dal primo tratto. Rimetti il telefono in tasca a metà frase: la nota è già al sicuro.
• Mani occupate? Digita o scatta una foto dallo stesso foglio.
• Sui Samsung la apri con una doppia pressione del tasto laterale.

POI, QUANDO HAI UN MINUTO
• Smista le note nuove come carte: a destra le mandi, a sinistra le tieni, in basso le butti.
• Manda qualunque nota a Google Keep, Notion, Obsidian, Gmail o a qualsiasi app — come testo, con la tua scrittura allegata.
• Una volta al giorno torna a galla una nota vecchia — "una settimana fa, oggi" — perché le idee buone non finiscano sepolte.
• Di notte il foglio si scurisce, e a letto non ti acceca.

PRIVATA PER COSTRUZIONE
Nessun account. Nessuna pubblicità. Nessun tracciamento. Le note restano sul telefono e non passano mai da un nostro server.

COSA NON È
Non è un quaderno con cartelle e pagine. È la penna del sistema di note che usi già.

Domande o idee: hello@instink.app""",
)

# Da aggiungere in fondo alla descrizione quando Instink Pro sarà in vendita (D67, D68). Apple
# chiede che un abbonamento dica prezzo, durata, rinnovo e dove si disdice.
PRO_EN = """INSTINK PRO
Capturing notes is free, forever. Pro is for when your notes pile up:
• Search everything you've ever written — free search covers the last 30 days (if you installed Instink before Pro existed, you keep unlimited search)
• Turn handwritten dates into reminders
• Send notes without the "Written with Instink" line
• Every new Pro feature as it arrives

Instink Pro is available as a monthly or yearly subscription — the yearly one with a 14-day free trial — or as a one-time lifetime purchase. The subscription renews automatically unless cancelled at least 24 hours before the end of the period, and is charged to your Apple Account. Manage or cancel it in Settings › Apple Account › Subscriptions.
Terms of Use: https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Privacy: https://instink.app/privacy.html"""

PRO_IT = """INSTINK PRO
Catturare le note è gratis, per sempre. Pro è per quando le note diventano tante:
• Cerca in tutto quello che hai scritto — la ricerca gratuita copre gli ultimi 30 giorni (chi ha installato Instink prima che Pro esistesse tiene la ricerca illimitata)
• Trasforma le date scritte a mano in promemoria
• Manda le note senza la riga "Scritta con Instink"
• Ogni funzione Pro nuova, appena arriva

Instink Pro è disponibile come abbonamento mensile o annuale — quello annuale con 14 giorni di prova gratuita — oppure come acquisto unico a vita. L'abbonamento si rinnova automaticamente se non viene disdetto almeno 24 ore prima della scadenza, ed è addebitato sul tuo Account Apple. Si gestisce o si disdice in Impostazioni › Account Apple › Abbonamenti.
Condizioni d'uso: https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Privacy: https://instink.app/it/privacy.html"""

SHOTS = [
    ("A blank sheet, one tap away.", "Un foglio bianco, a un tocco.", "La home con il widget di Instink fra le altre app. Il dito lo sta toccando."),
    ("Write the way you think.", "Scrivi come pensi.", "Il foglio con due parole scritte a mano, grandi, e un'idea cerchiata."),
    ("Saved before you say Done.", "Salvata prima di dire Fatto.", "\"Fatto\" in basso a destra; sopra, un tratto appena finito."),
    ("Siri takes it, even locked.", "Ci pensa Siri, anche bloccato.", "La schermata di blocco con la risposta di Siri \"Salvata in Instink\"."),
    ("Every idea, in your hand.", "Ogni idea, nella tua scrittura.", "L'archivio: bigliettini nella propria calligrafia, la ricerca con una parola trovata."),
    ("Then send it where it belongs.", "Poi mandala dove serve.", "Lo smistamento a carte, una carta che va a destra verso Notion."),
]


def check(lang, texts):
    bad = []
    for key, value in texts.items():
        if len(value) > LIMITS[key]:
            bad.append(f"{lang}.{key}: {len(value)} > {LIMITS[key]}")
    if " " in texts["keywords"]:
        bad.append(f"{lang}.keywords: niente spazi, solo virgole")
    return bad


def block(title, value, limit):
    return f"**{title}** — {len(value)}/{limit}\n\n```\n{value}\n```\n"


def main():
    bad = check("EN", EN) + check("IT", IT)
    if len(PRO_EN) + len(EN["description"]) + 2 > 4000:
        bad.append("EN: descrizione più Pro oltre 4000")
    if len(PRO_IT) + len(IT["description"]) + 2 > 4000:
        bad.append("IT: descrizione più Pro oltre 4000")
    if bad:
        sys.exit("Testi troppo lunghi:\n" + "\n".join(bad))

    out = ["""# Instink — i testi degli store

> Generato da `tools/brand/store_texts.py`: i testi si cambiano lì, e lo script controlla i
> limiti di caratteri. Le ragioni stanno in [`CLAUDE.md`](../CLAUDE.md), D67; la storia da
> cui nascono in [`STORIA.md`](STORIA.md).

Ogni campo si copia così com'è, dentro il riquadro. Il numero accanto al titolo è la
lunghezza sul limite dello store.

**Categorie:** Produttività (principale), Utility (secondaria). **Età:** 4+.
**Prezzo:** gratuita. **Privacy dell'app:** "Nessun dato raccolto".
"""]
    for lang, texts, pro, title in (("en", EN, PRO_EN, "Inglese (lingua principale)"), ("it", IT, PRO_IT, "Italiano")):
        out.append(f"\n---\n\n## App Store — {title}\n")
        out.append(block("Nome", texts["name"], 30))
        out.append(block("Sottotitolo", texts["subtitle"], 30))
        out.append(block("Parole chiave", texts["keywords"], 100))
        out.append(block("Testo promozionale", texts["promo"], 170))
        out.append(block("Descrizione", texts["description"], 4000))
        out.append(block("Novità di questa versione", texts["whats_new"], 4000))
        out.append(block("Da aggiungere alla descrizione quando Pro è in vendita (D67, D68): non al lancio", pro, 4000 - len(texts["description"]) - 2))
        out.append(f"\n## Play Store — {title}\n")
        out.append(block("Titolo", texts["play_title"], 30))
        out.append(block("Descrizione breve", texts["play_short"], 80))
        out.append(block("Descrizione completa", texts["play_full"], 4000))
    out.append("\n---\n\n## Gli screenshot: sei frasi, una storia\n")
    out.append("Il primo è quello che decide: nei risultati di ricerca se ne vedono tre.\n")
    out.append("| # | Inglese | Italiano | Cosa si vede |\n|---|---|---|---|")
    for i, (en, it, what) in enumerate(SHOTS, 1):
        out.append(f"| {i} | {en} | {it} | {what} |")
    out.append("")
    with open(os.path.join(ROOT, "lancio", "STORE.md"), "w") as f:
        f.write("\n".join(out))
    print("lancio/STORE.md: tutti i campi entro i limiti")


if __name__ == "__main__":
    main()
