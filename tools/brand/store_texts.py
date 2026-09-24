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

# Le altre lingue (D69). Solo App Store per intero, perché iOS esce prima (D48); per il Play
# Store titolo e descrizione breve, e la descrizione completa si traduce prima di pubblicare
# Android. Le parole chiave non ripetono quelle già nel nome e nel sottotitolo: lo store le
# conta comunque, e ripeterle spreca caratteri.
ES = dict(
    name="Instink: Notas a mano",
    subtitle="Apunta tus ideas al instante",
    keywords="instinto,rápido,widget,apuntes,memo,libreta,bloc,escribir,lápiz,garabatos,diario,voz,dictar,idea",
    promo=(
        "Nuevo: dicta una nota sin desbloquear, solo pídeselo a Siri. "
        "Y ahora puedes buscar en tu propia letra, leída directamente en tu iPhone."
    ),
    description="""Se te ocurre una idea. Entre desbloquear el móvil, buscar la app de notas y abrir una página nueva, ya se ha ido.

Instink es una hoja en blanco en tu pantalla de inicio. La tocas y ya estás escribiendo, a mano, con tu propia letra. Tocas Listo y vuelves a donde estabas. Un segundo.

POR QUÉ NO LA QUITARÁS
• Lista en menos de medio segundo. Sin cuenta, sin registro, nada que elegir antes de escribir.
• Tu letra, no una fuente. Con el dedo o con el Apple Pencil, con una tinta que sigue tu mano.
• Guardada desde el primer trazo. Guarda el móvil a mitad de frase: la nota ya está a salvo.
• ¿Manos ocupadas? Escribe con el teclado, haz una foto o graba tu voz desde la misma hoja.
• ¿Móvil bloqueado? Pide a Siri que añada una nota a Instink y díctala. Sin desbloquear.

TODAS LAS ENTRADAS
Widgets de la pantalla de inicio y de bloqueo, Centro de control, botón de Acción, Toque posterior y Atajos. Usa la que tengas más cerca del pulgar.

LUEGO, CUANDO TENGAS UN MINUTO
• Ordena las notas nuevas como cartas: a la derecha para enviar, a la izquierda para conservar, abajo para eliminar.
• Envía cualquier nota a Notion, Notas, Google Keep, Obsidian, Mail o Mensajes, como texto y con tu letra adjunta.
• Busca en tu letra. Instink la lee en tu dispositivo.
• ¿Escribiste "dentista mañana a las 10"? Instink te propone añadirlo al calendario.
• Una vez al día vuelve una nota antigua, "hoy hace una semana", para que las buenas ideas no se queden enterradas.
• De noche la hoja se oscurece y no te deslumbra en la cama.

PRIVADA POR DISEÑO
Sin cuenta. Sin anuncios. Sin rastreo. Tus notas se quedan en tu dispositivo y nunca pasan por un servidor nuestro. La letra y la voz se reconocen en tu iPhone.

LO QUE INSTINK NO ES
No es un cuaderno con carpetas y páginas. Es el bolígrafo del sistema de notas que ya usas.

Preguntas o ideas: hello@instink.app. Cada mensaje lo lee una persona.""",
    whats_new="""• Dicta una nota a Siri, incluso con el móvil bloqueado.
• Graba notas de voz desde la hoja; se transcriben en tu dispositivo.
• Ahora puedes buscar en tu letra.""",
    play_title="Instink: Notas a mano",
    play_short="Una hoja en blanco en tu pantalla de inicio. Toca, escribe a mano, listo.",
)

DE = dict(
    name="Instink: Handschrift-Notizen",
    subtitle="Ideen blitzschnell festhalten",
    keywords="instinkt,schnell,widget,notizblock,memo,zettel,stift,schreiben,kritzeln,tagebuch,stimme,diktieren",
    promo=(
        "Neu: Diktiere eine Notiz, ohne zu entsperren – sag einfach Siri Bescheid. "
        "Und deine Handschrift ist jetzt durchsuchbar, gelesen direkt auf deinem iPhone."
    ),
    description="""Dir kommt eine Idee. Bis du das Handy entsperrt, die Notizen-App gefunden und eine neue Seite geöffnet hast, ist sie weg.

Instink ist ein leeres Blatt auf deinem Home-Bildschirm. Tippen, und du schreibst schon – von Hand, in deiner eigenen Schrift. Tippe auf Fertig, und du bist wieder da, wo du warst. Eine Sekunde.

WARUM MAN ES BEHÄLT
• Bereit in unter einer halben Sekunde. Kein Konto, keine Anmeldung, nichts zu wählen, bevor du schreibst.
• Deine Handschrift, keine Schriftart. Mit dem Finger oder dem Apple Pencil, mit Tinte, die deiner Hand folgt.
• Gesichert ab dem ersten Strich. Steck das Handy mitten im Satz weg: Die Notiz ist schon sicher.
• Hände voll? Tippe, mach ein Foto oder nimm deine Stimme auf – vom selben Blatt.
• Handy gesperrt? Bitte Siri, eine Notiz zu Instink hinzuzufügen, und diktiere sie. Ohne Entsperren.

ALLE WEGE HINEIN
Widgets auf dem Home- und dem Sperrbildschirm, Kontrollzentrum, Aktionstaste, Rückseite tippen und Kurzbefehle. Nimm den, der deinem Daumen am nächsten ist.

UND WENN DU EINE MINUTE HAST
• Sortiere neue Notizen wie Karten: nach rechts senden, nach links behalten, nach unten löschen.
• Sende jede Notiz an Notion, Notizen, Google Keep, Obsidian, Mail oder Nachrichten – als Text, mit deiner Handschrift im Anhang.
• Durchsuche deine Handschrift. Instink liest sie auf deinem Gerät.
• „Zahnarzt morgen um 10“ geschrieben? Instink schlägt vor, es in den Kalender einzutragen.
• Einmal am Tag taucht eine alte Notiz wieder auf – „heute vor einer Woche“ –, damit gute Ideen nicht verschüttet werden.
• Nachts wird das Blatt dunkel und blendet dich im Bett nicht.

PRIVAT VON GRUND AUF
Kein Konto. Keine Werbung. Kein Tracking. Deine Notizen bleiben auf deinem Gerät und laufen nie über einen Server von uns. Handschrift und Stimme werden auf deinem iPhone erkannt.

WAS INSTINK NICHT IST
Kein Notizbuch mit Ordnern und Seiten. Es ist der Stift für das Notizsystem, das du schon benutzt.

Fragen oder Ideen: hello@instink.app – jede Nachricht liest ein Mensch.""",
    whats_new="""• Diktiere Siri eine Notiz, auch bei gesperrtem Handy.
• Nimm Sprachnotizen vom Blatt auf; sie werden auf deinem Gerät transkribiert.
• Deine Handschrift ist jetzt durchsuchbar.""",
    play_title="Instink: Handschrift-Notizen",
    play_short="Ein leeres Blatt auf dem Startbildschirm. Tippen, von Hand schreiben, fertig.",
)

FR = dict(
    name="Instink : notes manuscrites",
    subtitle="Notez vos idées en un éclair",
    keywords="instinct,rapide,widget,mémo,bloc,carnet,stylo,écrire,griffonner,journal,voix,dicter,rappel,post-it",
    promo=(
        "Nouveau : dictez une note sans déverrouiller, il suffit de le demander à Siri. "
        "Et votre écriture est désormais consultable, lue directement sur votre iPhone."
    ),
    description="""Une idée arrive. Le temps de déverrouiller le téléphone, de trouver l’app de notes et d’ouvrir une nouvelle page, elle est partie.

Instink est une feuille blanche sur votre écran d’accueil. Touchez-la et vous écrivez déjà, à la main, avec votre propre écriture. Touchez Terminé et vous revenez là où vous étiez. Une seconde.

POURQUOI ON LA GARDE
• Prête en moins d’une demi-seconde. Pas de compte, pas d’inscription, rien à choisir avant d’écrire.
• Votre écriture, pas une police. Au doigt ou à l’Apple Pencil, avec une encre qui suit votre main.
• Enregistrée dès le premier trait. Rangez le téléphone au milieu d’une phrase : la note est déjà en sécurité.
• Les mains prises ? Tapez, prenez une photo ou enregistrez votre voix depuis la même feuille.
• Téléphone verrouillé ? Demandez à Siri d’ajouter une note à Instink et dictez-la. Sans déverrouiller.

TOUTES LES ENTRÉES
Widgets de l’écran d’accueil et de l’écran verrouillé, Centre de contrôle, bouton Action, Toucher le dos et Raccourcis. Prenez celle qui est la plus proche du pouce.

ENSUITE, QUAND VOUS AVEZ UNE MINUTE
• Triez les nouvelles notes comme des cartes : à droite pour envoyer, à gauche pour garder, vers le bas pour supprimer.
• Envoyez n’importe quelle note vers Notion, Notes, Google Keep, Obsidian, Mail ou Messages, en texte, avec votre écriture en pièce jointe.
• Cherchez dans votre écriture. Instink la lit sur votre appareil.
• Vous avez écrit « dentiste demain à 10 h » ? Instink propose de l’ajouter au calendrier.
• Une fois par jour, une ancienne note refait surface, « il y a une semaine, jour pour jour », pour que les bonnes idées ne restent pas enfouies.
• La nuit, la feuille s’assombrit et ne vous éblouit pas au lit.

PRIVÉE PAR CONCEPTION
Pas de compte. Pas de pub. Pas de pistage. Vos notes restent sur votre appareil et ne passent jamais par un serveur à nous. L’écriture et la voix sont reconnues sur votre iPhone.

CE QU’INSTINK N’EST PAS
Ce n’est pas un cahier avec des dossiers et des pages. C’est le stylo du système de notes que vous utilisez déjà.

Questions ou idées : hello@instink.app. Chaque message est lu par une personne.""",
    whats_new="""• Dictez une note à Siri, même téléphone verrouillé.
• Enregistrez des notes vocales depuis la feuille ; elles sont transcrites sur votre appareil.
• Votre écriture est désormais consultable.""",
    play_title="Instink : notes manuscrites",
    play_short="Une page blanche sur votre écran d’accueil. Touchez, écrivez, c’est noté.",
)

PT = dict(
    name="Instink: Notas escritas à mão",
    subtitle="Anote suas ideias na hora",
    keywords="instinto,rápido,widget,anotação,memo,bloco,caderno,caneta,escrever,rabisco,diário,voz,ditar,lembrete",
    promo=(
        "Novo: dite uma nota sem desbloquear, é só pedir à Siri. "
        "E agora dá para buscar na sua própria letra, lida direto no seu iPhone."
    ),
    description="""Você tem uma ideia. Até desbloquear o celular, achar o app de notas e abrir uma página nova, ela já foi embora.

O Instink é uma folha em branco na sua Tela de Início. Você toca e já está escrevendo, à mão, com a sua letra. Toca em Pronto e volta para onde estava. Um segundo.

POR QUE AS PESSOAS FICAM COM ELE
• Pronto em menos de meio segundo. Sem conta, sem cadastro, nada para escolher antes de escrever.
• A sua letra, não uma fonte. Com o dedo ou com o Apple Pencil, com uma tinta que acompanha a sua mão.
• Salvo desde o primeiro traço. Guarde o celular no meio da frase: a nota já está segura.
• Mãos ocupadas? Digite, tire uma foto ou grave a sua voz na mesma folha.
• Celular bloqueado? Peça à Siri para adicionar uma nota ao Instink e dite. Sem desbloquear.

TODAS AS ENTRADAS
Widgets da Tela de Início e da Tela Bloqueada, Central de Controle, botão de Ação, Toque Traseiro e Atalhos. Use a que estiver mais perto do polegar.

DEPOIS, QUANDO VOCÊ TIVER UM MINUTO
• Organize as notas novas como cartas: para a direita envia, para a esquerda mantém, para baixo apaga.
• Envie qualquer nota para Notion, Notas, Google Keep, Obsidian, Mail ou Mensagens, como texto, com a sua letra anexada.
• Busque na sua letra. O Instink lê no seu aparelho.
• Escreveu "dentista amanhã às 10"? O Instink sugere colocar no calendário.
• Uma vez por dia uma nota antiga volta, "há uma semana, hoje", para as boas ideias não ficarem enterradas.
• À noite a folha escurece e não ofusca você na cama.

PRIVADO DESDE A ORIGEM
Sem conta. Sem anúncios. Sem rastreamento. Suas notas ficam no seu aparelho e nunca passam por um servidor nosso. Letra e voz são reconhecidas no seu iPhone.

O QUE O INSTINK NÃO É
Não é um caderno com pastas e páginas. É a caneta do sistema de notas que você já usa.

Dúvidas ou ideias: hello@instink.app. Cada mensagem é lida por uma pessoa.""",
    whats_new="""• Dite uma nota para a Siri, mesmo com o celular bloqueado.
• Grave notas de voz na folha; elas são transcritas no seu aparelho.
• Agora dá para buscar na sua letra.""",
    play_title="Instink: Notas escritas à mão",
    play_short="Uma folha em branco na tela inicial. Toque, escreva à mão, pronto.",
)

JA = dict(
    name="Instink：手書きメモ",
    subtitle="ひらめきを一瞬で書きとめる",
    keywords="ノート,ウィジェット,付箋,メモ帳,すばやく,アイデア,音声入力,日記,ペン,落書き,ロック画面,簡単,備忘録,instinct",
    promo=(
        "新機能：ロックを解除せずにメモを口述できます。Siriに頼むだけ。"
        "さらに、手書きの文字がiPhone上で読み取られ、検索できるようになりました。"
    ),
    description="""アイデアが浮かんだ。でも、ロックを解除して、メモアプリを探して、新しいページを開くころには、もう消えている。

Instinkは、ホーム画面に置く一枚の白い紙です。タップすれば、もう書き始めています。手書きで、あなた自身の文字で。「完了」をタップすれば、元の画面に戻ります。わずか一秒。

手放せない理由
• 0.5秒以内に準備完了。アカウントも登録も不要。書く前に選ぶものは何もありません。
• フォントではなく、あなたの文字。指でもApple Pencilでも、手の動きについてくるインク。
• 最初のひと筆から保存。文の途中でポケットにしまっても、メモはもう安全です。
• 手がふさがっていたら、同じ紙から入力、写真、音声録音ができます。
• ロック中でも、Siriに「Instinkにメモを追加」と頼んで口述するだけ。解除は不要です。

どこからでも開ける
ホーム画面とロック画面のウィジェット、コントロールセンター、アクションボタン、背面タップ、ショートカット。いちばん親指に近いものを使ってください。

時間ができたら
• 新しいメモをカードのように整理：右で送信、左で残す、下で削除。
• どのメモもNotion、メモ、Google Keep、Obsidian、メール、メッセージへ。テキストに手書きの画像を添えて送れます。
• 手書きの文字を検索。Instinkが端末上で読み取ります。
• 「明日10時 歯医者」と書いたら、カレンダーへの登録を提案します。
• 1日に1回、「ちょうど1週間前」の古いメモが浮かび上がり、良いアイデアが埋もれません。
• 夜は紙が暗くなり、ベッドの中でもまぶしくありません。

プライバシーを最初から
アカウントなし。広告なし。トラッキングなし。メモは端末に残り、私たちのサーバーを通ることはありません。手書きと音声の認識もiPhone上で行われます。

Instinkではないもの
フォルダやページのあるノートではありません。いつも使っているメモの仕組みのための「ペン」です。

ご質問・アイデア：hello@instink.app（すべてのメッセージを人が読んでいます）""",
    whats_new="""• ロック中でも、Siriにメモを口述できます。
• 紙から音声メモを録音。端末上で文字起こしされます。
• 手書きの文字を検索できるようになりました。""",
    play_title="Instink：手書きメモ",
    play_short="ホーム画面に白い紙。タップして手書きするだけ。アカウント不要。",
)

OTHERS = (
    ("es", ES, "Spagnolo — in App Store Connect sia *Spagnolo (Spagna)* sia *Spagnolo (Messico)*"),
    ("de", DE, "Tedesco"),
    ("fr", FR, "Francese — *Francese* e *Francese (Canada)*"),
    ("pt-BR", PT, "Portoghese (Brasile)"),
    ("ja", JA, "Giapponese"),
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
    for lang, texts, _ in OTHERS:
        bad += check(lang, texts)
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
    for lang, texts, title in OTHERS:
        out.append(f"\n---\n\n## App Store — {title}\n")
        out.append(block("Nome", texts["name"], 30))
        out.append(block("Sottotitolo", texts["subtitle"], 30))
        out.append(block("Parole chiave", texts["keywords"], 100))
        out.append(block("Testo promozionale", texts["promo"], 170))
        out.append(block("Descrizione", texts["description"], 4000))
        out.append(block("Novità di questa versione", texts["whats_new"], 4000))
        out.append(f"\n## Play Store — {title}\n")
        out.append(block("Titolo", texts["play_title"], 30))
        out.append(block("Descrizione breve", texts["play_short"], 80))
        out.append("La descrizione completa si traduce prima di pubblicare Android (D69).\n")
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
