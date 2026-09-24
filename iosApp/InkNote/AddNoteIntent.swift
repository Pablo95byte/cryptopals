import AppIntents
import Foundation
import InkNoteKit

/// "Ehi Siri, aggiungi una nota a Instink" (D18, D63): si detta, e la nota è salvata
/// **senza sbloccare e senza aprire l'app**.
///
/// Su iPhone nessuna superficie di scrittura sta sopra il blocco (D17), ma Siri sì: è
/// l'unica cattura a telefono bloccato che Apple concede, ed è per questo che la voce su
/// iPhone non è un extra ma il percorso più corto che esista.
///
/// Rispetta la regola del blocco (invariante 11): si può **aggiungere**, mai leggere. La
/// risposta di Siri dice "salvata" e nient'altro, nemmeno il testo appena dettato.
///
/// Il testo lo trascrive Siri, secondo le impostazioni dell'utente: è l'unico punto
/// dell'app in cui il riconoscimento non è nostro, e l'informativa lo dice (D61). Arriva
/// come testo digitato, un pezzo della nota come gli altri (D38), e passa dal giornale
/// come tutto il resto: al sicuro subito, in archivio alla prossima apertura.
struct AddNoteIntent: AppIntent {
    static let title: LocalizedStringResource = "Add a note"
    static let description = IntentDescription("Dictate a note to Instink without unlocking your iPhone.")
    static let openAppWhenRun: Bool = false
    // Funziona a telefono bloccato: il giornale sta in un file leggibile dopo il primo
    // sblocco (D24), e questa azione non mostra niente di ciò che è già scritto.
    static let authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "Note", requestValueDialog: "What's the note?")
    var text: String

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return .result(dialog: "Nothing to save.")
        }
        // Un foglio usato solo per il testo: la misura del foglio non conta, ma serve.
        let sheet = InkSheet(
            canvasWidth: 390,
            canvasHeight: 844,
            journalSink: FileJournalSink.shared,
            clock: IosPlatform.shared.wallClock
        )
        sheet.commitText(text: trimmed)
        // Su disco prima di rispondere: dopo la risposta il sistema può chiudere l'app.
        FileJournalSink.shared.awaitWrites()
        return .result(dialog: "Saved to Instink.")
    }
}
