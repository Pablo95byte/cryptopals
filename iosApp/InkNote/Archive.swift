import Foundation
import InkNoteKit

/// L'archivio su iOS (D48): il core di Kotlin su una coda sua, mai sul thread
/// dell'interfaccia (D14).
final class ArchiveBackend: @unchecked Sendable {

    static let shared = ArchiveBackend()

    private let queue = DispatchQueue(label: "app.inknote.archivio")
    private var archive: InkArchive?

    /// Esegue [work] sulla coda dell'archivio e consegna il risultato sul thread principale.
    func run<T>(_ work: @escaping (InkArchive) -> T, then: @escaping (T) -> Void = { _ in }) {
        queue.async {
            let archive = self.archive ?? InkArchive(store: IosPlatform.shared.openStore(name: "inknote.db"))
            self.archive = archive
            let result = work(archive)
            DispatchQueue.main.async { then(result) }
        }
    }
}

/// Una nota come la vede l'elenco.
struct NoteItem: Identifiable {
    let id: String
    let note: Note
    let date: Date
    let caption: String?
}

/// Lo stato dell'archivio: le note, la ricerca, e l'assorbimento del giornale.
final class ArchiveModel: ObservableObject {
    @Published private(set) var notes: [NoteItem] = []
    @Published private(set) var loaded = false
    @Published var lostStroke = false
    @Published var query = "" { didSet { reload() } }

    /// Il giornale entra in archivio, poi l'elenco si rilegge (D22). Da chiamare quando
    /// l'app torna davanti e quando il foglio si chiude.
    func refresh() {
        ArchiveBackend.shared.run({ archive in
            archive.ingest(journalSink: FileJournalSink.shared)
        }, then: { [weak self] torn in
            if torn { self?.lostStroke = true }
            self?.reload()
        })
    }

    func reload() {
        let term = query
        ArchiveBackend.shared.run({ archive -> [NoteItem] in
            let notes = term.trimmingCharacters(in: .whitespaces).isEmpty
                ? archive.recent(limit: 500)
                : archive.search(term: term, limit: 200)
            return notes.map { note in
                NoteItem(
                    id: archive.idOf(note: note),
                    note: note,
                    date: Date(timeIntervalSince1970: TimeInterval(note.updatedAt) / 1000),
                    caption: note.typedText ?? note.recognizedText
                )
            }
        }, then: { [weak self] items in
            self?.notes = items
            self?.loaded = true
        })
    }

    /// Un tombstone, non una cancellazione (D8, D26).
    func delete(_ item: NoteItem) {
        notes.removeAll { $0.id == item.id }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        ArchiveBackend.shared.run({ archive in archive.delete(id: item.id, nowMillis: now) })
    }
}
