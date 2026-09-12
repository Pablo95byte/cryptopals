package app.inknote.core.model

/**
 * Fonde due versioni della stessa nota provenienti da dispositivi diversi.
 *
 * Non è codice morto in attesa del sync: è la prova che il modello dati regge il
 * sync. Se questa funzione non si può scrivere senza perdere dati, il modello è
 * sbagliato, e conviene scoprirlo adesso e non dopo aver venduto l'app.
 *
 * ## Regole
 *
 * - **I tratti si uniscono per id.** Sono immutabili, quindi due dispositivi non
 *   possono produrre versioni diverse dello stesso tratto: l'unione è sempre
 *   definita e nessuno dei due perde ciò che ha disegnato offline.
 * - **Le registrazioni vocali si uniscono allo stesso modo** (D25). Anche l'audio è
 *   immutabile; l'unico campo che può comparire dopo è la trascrizione, e fra "c'è" e
 *   "non c'è ancora" vince "c'è".
 * - **La cancellazione di un tratto vince** sulla sua esistenza, con il tombstone
 *   più vecchio a fare da riferimento. Senza questo un tratto cancellato su un
 *   dispositivo riapparirebbe al sync successivo.
 * - **La cancellazione della nota vince** sulle modifiche. È la scelta prudente:
 *   un utente che cancella una nota e la ritrova è un utente che non si fida
 *   dell'app. Il recupero resta possibile finché il tombstone è in archivio.
 * - **L'esito è indipendente dall'ordine.** `merge(a, b)` e `merge(b, a)` danno lo
 *   stesso risultato, ed è verificato dai test: è la proprietà che permette di
 *   fondere in qualunque sequenza senza far divergere i dispositivi.
 */
fun mergeNotes(local: Note, remote: Note): Note {
    require(local.id == remote.id) {
        "non si fondono note diverse: ${local.id.value} != ${remote.id.value}"
    }

    val mergedStrokes = HashMap<StrokeId, Stroke>(local.strokes.size + remote.strokes.size)
    for (stroke in local.strokes) mergedStrokes[stroke.id] = stroke
    for (stroke in remote.strokes) {
        val existing = mergedStrokes[stroke.id]
        mergedStrokes[stroke.id] = if (existing == null) stroke else mergeStrokes(existing, stroke)
    }

    // Il "più recente" decide i campi a valore singolo: prima la revisione, che è
    // monotona per dispositivo, e solo a parità il timestamp.
    val mergedClips = HashMap<VoiceClipId, VoiceClip>(local.voiceClips.size + remote.voiceClips.size)
    for (clip in local.voiceClips) mergedClips[clip.id] = clip
    for (clip in remote.voiceClips) {
        val existing = mergedClips[clip.id]
        mergedClips[clip.id] = if (existing == null) clip else mergeVoiceClips(existing, clip)
    }

    val newest = if (compareVersions(local, remote) >= 0) local else remote
    val oldest = if (newest === local) remote else local

    return Note(
        id = local.id,
        canvas = newest.canvas,
        strokes = orderStrokes(mergedStrokes.values.toList()),
        voiceClips = orderVoiceClips(mergedClips.values.toList()),
        createdAt = minOf(local.createdAt, remote.createdAt),
        updatedAt = maxOf(local.updatedAt, remote.updatedAt),
        revision = maxOf(local.revision, remote.revision),
        // La cancellazione vince, e vale il tombstone più vecchio.
        deletedAt = minOfNullable(local.deletedAt, remote.deletedAt),
        recognizedText = newest.recognizedText ?: oldest.recognizedText,
        recognizedFromRevision = if (newest.recognizedText != null) {
            newest.recognizedFromRevision
        } else {
            oldest.recognizedFromRevision
        },
    )
}

private fun mergeStrokes(a: Stroke, b: Stroke): Stroke {
    val deletedAt = minOfNullable(a.deletedAt, b.deletedAt)
    // I punti sono immutabili: a parità di id il contenuto è lo stesso. Se un
    // dispositivo ne ha meno è perché ha ricevuto il tratto a metà sync, quindi
    // teniamo la versione più completa.
    val richest = if (a.points.size >= b.points.size) a else b
    return richest.copy(deletedAt = deletedAt, createdAt = minOf(a.createdAt, b.createdAt))
}

private fun mergeVoiceClips(a: VoiceClip, b: VoiceClip): VoiceClip = a.copy(
    // Come per i tratti: la cancellazione vince, e vale il tombstone più vecchio.
    deletedAt = minOfNullable(a.deletedAt, b.deletedAt),
    // La trascrizione e l'audio possono essere arrivati su un solo dispositivo: fra
    // "c'è" e "non c'è ancora" vince "c'è", altrimenti un sync farebbe perdere un
    // riconoscimento già fatto.
    transcript = a.transcript ?: b.transcript,
    audioPath = a.audioPath ?: b.audioPath,
    recordedAt = minOf(a.recordedAt, b.recordedAt),
    durationMs = maxOf(a.durationMs, b.durationMs),
)

/** Confronto di versione: revisione, poi timestamp, poi id come ultimo criterio stabile. */
private fun compareVersions(a: Note, b: Note): Int {
    a.revision.compareTo(b.revision).let { if (it != 0) return it }
    a.updatedAt.compareTo(b.updatedAt).let { if (it != 0) return it }
    return 0
}

private fun minOfNullable(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> minOf(a, b)
}
