import AppIntents
import Foundation

/// "Scrivi una nota": l'azione che apre il foglio da Comandi rapidi, Siri, il tasto
/// Azione degli iPhone 15 Pro e successivi, e il doppio tocco sul retro (§2).
///
/// Su iPhone nessun ingresso scrive sopra il blocco (D17): tutti portano a "sblocca e
/// apri". Il meglio che si può fare è che sblocco e apertura siano un gesto solo, e che
/// il foglio sia pronto quando l'app si apre.
struct WriteNoteIntent: AppIntent {
    static let title: LocalizedStringResource = "Write a note"
    static let description = IntentDescription("Opens a blank sheet, ready for your handwriting.")
    static let openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        CaptureRequests.post()
        return .result()
    }
}

/// Il ponte fra un'azione di sistema e l'app: una notifica che il router ascolta, più un
/// segno che resta se l'app si stava ancora aprendo e nessuno ascoltava. Senza il segno,
/// il tasto Azione ad app chiusa aprirebbe l'archivio invece del foglio.
enum CaptureRequests {
    static let name = Notification.Name("app.inknote.capture")
    private static let lock = NSLock()
    nonisolated(unsafe) private static var pending = false

    static func post() {
        lock.lock()
        pending = true
        lock.unlock()
        NotificationCenter.default.post(name: name, object: nil)
    }

    /// `true` se c'era una richiesta in attesa; la consuma.
    static func consumePending() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let was = pending
        pending = false
        return was
    }
}
