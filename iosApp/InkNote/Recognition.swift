import Foundation
import InkNoteKit
import Speech
import UIKit
import Vision

/// Il lavoro che rende le note cercabili (D2, D62, D63): leggere l'inchiostro e trascrivere
/// le registrazioni. **Tutto sul dispositivo**: Vision per la scrittura, il riconoscimento
/// vocale del sistema solo in modalità locale. Se il telefono non sa riconoscere il parlato
/// senza la rete, la registrazione aspetta: non si manda niente a nessuno (D12, D61).
///
/// Parte dopo l'assorbimento del giornale, quando si apre l'archivio: mai sul foglio, che
/// deve restare leggero (D20). Lavora a lotti piccoli, così una nota appena scritta diventa
/// cercabile in pochi secondi anche con un archivio lungo da recuperare.
enum Recognition {

    private static let queue = DispatchQueue(label: "app.inknote.riconoscimento", qos: .utility)

    /// Letto e scritto solo sul thread principale.
    nonisolated(unsafe) private static var running = false

    /// Quante note leggere per apertura dell'archivio, al massimo: il resto alla prossima.
    private static let maxNotesPerRun = 40
    private static let batch = 8

    /// Legge e trascrive ciò che aspetta. [onChange] arriva sul thread principale, una
    /// volta sola, se almeno una nota è cambiata.
    static func runPending(onChange: @escaping () -> Void) {
        guard !running else { return }
        running = true
        recognizeInk(done: 0, changed: false) { inkChanged in
            transcribe { voiceChanged in
                running = false
                if inkChanged || voiceChanged { onChange() }
            }
        }
    }

    // MARK: Scrittura (D62)

    private static func recognizeInk(done: Int, changed: Bool, finish: @escaping (Bool) -> Void) {
        guard done < maxNotesPerRun else { finish(changed); return }
        ArchiveBackend.shared.run({ archive -> [(String, Int64, Note)] in
            archive.toRecognize(limit: Int32(batch)).map { (archive.idOf(note: $0), $0.revision, $0) }
        }, then: { pending in
            guard !pending.isEmpty else { finish(changed); return }
            queue.async {
                // Disegnare e leggere: secondi di lavoro, fuori dall'archivio e dall'interfaccia.
                let results = pending.map { id, revision, note in (id, revision, read(note)) }
                ArchiveBackend.shared.run({ archive -> Bool in
                    var wrote = false
                    for (id, revision, text) in results {
                        // Se la nota è cresciuta nel frattempo, il testo non si scrive e la
                        // nota resta in coda: la prossima passata la rilegge intera.
                        if archive.setRecognized(id: id, text: text, revision: revision) { wrote = true }
                    }
                    return wrote
                }, then: { wrote in
                    // Nessuna scrittura riuscita vuol dire che le stesse note tornerebbero
                    // subito: meglio fermarsi e riprovare alla prossima apertura.
                    guard wrote else { finish(changed); return }
                    recognizeInk(done: done + pending.count, changed: true, finish: finish)
                })
            }
        })
    }

    /// Il testo scritto a mano in una nota, riga per riga, dall'alto in basso.
    ///
    /// Una nota senza parole riconoscibili dà una stringa vuota, che è un esito: la nota
    /// esce dalla coda e non ci si rigira sopra a ogni apertura. L'inchiostro resta la nota;
    /// il testo è solo un indice (D2).
    static func read(_ note: Note) -> String {
        // Carta chiara, inchiostro scuro, lettere grandi: quello che Vision legge meglio.
        guard let image = ShareNote.inkImage(of: note, width: 1400), let cgImage = image.cgImage else { return "" }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        request.recognitionLanguages = languages(for: request)
        do {
            try VNImageRequestHandler(cgImage: cgImage, orientation: .up).perform([request])
        } catch {
            return ""
        }
        return lines(of: request.results ?? []).joined(separator: "\n")
    }

    /// Le lingue di chi usa il telefono, fra quelle che Vision sa leggere; l'inglese se
    /// nessuna. Scegliere la lingua conta: la correzione usa il suo dizionario.
    private static func languages(for request: VNRecognizeTextRequest) -> [String] {
        let supported = (try? request.supportedRecognitionLanguages()) ?? []
        var chosen: [String] = []
        for preferred in Locale.preferredLanguages {
            let code = String(preferred.prefix(2))
            let match = supported.first { $0 == preferred } ?? supported.first { $0.hasPrefix(code) }
            if let match, !chosen.contains(match) { chosen.append(match) }
        }
        return chosen.isEmpty ? ["en-US"] : chosen
    }

    /// Mette in ordine di lettura: righe dall'alto in basso, e dentro una riga da sinistra a
    /// destra. Vision non garantisce l'ordine, e una riga scritta storta può tornare a pezzi.
    private static func lines(of observations: [VNRecognizedTextObservation]) -> [String] {
        // In Vision l'asse y va verso l'alto.
        let sorted = observations.sorted { $0.boundingBox.midY > $1.boundingBox.midY }
        var rows: [[VNRecognizedTextObservation]] = []
        for observation in sorted {
            if let first = rows.last?.first,
               abs(first.boundingBox.midY - observation.boundingBox.midY) < min(first.boundingBox.height, observation.boundingBox.height) / 2 {
                rows[rows.count - 1].append(observation)
            } else {
                rows.append([observation])
            }
        }
        return rows.map { row in
            row.sorted { $0.boundingBox.minX < $1.boundingBox.minX }
                .compactMap { $0.topCandidates(1).first?.string }
                .joined(separator: " ")
        }.filter { !$0.isEmpty }
    }

    // MARK: Voce (D63)

    /// Se su questo telefono la voce si può trascrivere adesso: permesso dato, e la lingua
    /// del telefono riconosciuta senza rete. Se no, la nota aperta lo dice invece di
    /// promettere una trascrizione che non arriverà.
    static var canTranscribe: Bool {
        guard SFSpeechRecognizer.authorizationStatus() == .authorized,
              let recognizer = SFSpeechRecognizer() else { return false }
        return recognizer.supportsOnDeviceRecognition
    }

    private static func transcribe(finish: @escaping (Bool) -> Void) {
        // Solo sul dispositivo. Se non si può, la registrazione aspetta con l'audio intatto.
        guard SFSpeechRecognizer.authorizationStatus() == .authorized,
              let recognizer = SFSpeechRecognizer(),
              recognizer.supportsOnDeviceRecognition else {
            finish(false)
            return
        }
        ArchiveBackend.shared.run({ archive in archive.toTranscribe(limit: 10) }, then: { clips in
            transcribeNext(ArraySlice(clips), recognizer: recognizer, changed: false, finish: finish)
        })
    }

    private static func transcribeNext(
        _ clips: ArraySlice<VoiceItem>,
        recognizer: SFSpeechRecognizer,
        changed: Bool,
        finish: @escaping (Bool) -> Void
    ) {
        guard let clip = clips.first else { finish(changed); return }
        let rest = clips.dropFirst()
        let save: (String?) -> Void = { text in
            guard let text else {
                transcribeNext(rest, recognizer: recognizer, changed: changed, finish: finish)
                return
            }
            ArchiveBackend.shared.run({ archive in archive.setTranscript(clipId: clip.id, text: text) }, then: { wrote in
                transcribeNext(rest, recognizer: recognizer, changed: changed || wrote, finish: finish)
            })
        }

        // Un file che non c'è più non si trascriverà mai: una trascrizione vuota la toglie
        // dalla coda, e l'audio mancante resta visibile nella nota.
        guard let path = clip.path, let url = PhotoFiles.url(of: path), FileManager.default.fileExists(atPath: url.path) else {
            save("")
            return
        }

        let request = SFSpeechURLRecognitionRequest(url: url)
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = false
        request.addsPunctuation = true

        var delivered = false
        _ = recognizer.recognitionTask(with: request) { result, error in
            guard !delivered else { return }
            if let result, result.isFinal {
                delivered = true
                save(result.bestTranscription.formattedString)
            } else if error != nil {
                delivered = true
                // Il riconoscitore c'era e l'errore è sul file: nessun parlato, un audio che
                // non si capisce. È un esito, e la registrazione esce dalla coda con l'audio
                // intatto. Solo un riconoscitore che intanto è sparito la lascia per dopo:
                // altrimenti la nota prometterebbe per sempre una trascrizione.
                save(recognizer.isAvailable ? "" : nil)
            }
        }
    }
}
