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

    init(archive: InkArchive, note: Note) {
        id = archive.idOf(note: note)
        self.note = note
        date = Date(timeIntervalSince1970: TimeInterval(note.updatedAt) / 1000)
        caption = archive.captionOf(note: note)
    }
}

/// La nota di oggi tornata a galla (D52).
struct ResurfacedItem {
    let item: NoteItem
    let daysAgo: Int
    let isAnniversary: Bool

    /// "Un anno fa oggi" tocca più di "365 giorni fa".
    var title: String {
        switch (isAnniversary, daysAgo) {
        case (true, 365): return String(localized: "A year ago today")
        case (true, 90): return String(localized: "Three months ago today")
        case (true, 30): return String(localized: "A month ago today")
        case (true, 7): return String(localized: "A week ago today")
        default: return String(localized: "\(daysAgo) days ago")
        }
    }
}

enum Now {
    static var millis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Lo scarto del fuso adesso: "oggi" e "domani alle 9" sono fatti locali, e il core
    /// non conosce i fusi (invariante 5).
    static var utcOffsetMillis: Int64 { Int64(TimeZone.current.secondsFromGMT()) * 1000 }
}

/// Lo stato dell'archivio: le note, la ricerca, e l'assorbimento del giornale.
final class ArchiveModel: ObservableObject {
    @Published private(set) var notes: [NoteItem] = []
    @Published private(set) var loaded = false
    @Published var lostStroke = false
    @Published var query = "" { didSet { reload() } }
    /// Quante note aspettano lo smistamento (D52).
    @Published private(set) var toSortCount = 0
    @Published private(set) var resurfaced: ResurfacedItem?

    /// La riemersione si toglie per oggi, non per sempre: domani ne arriva un'altra.
    /// È una comodità di chi guarda, quindi sta nelle preferenze e non in archivio.
    private static let dismissedDayKey = "resurface.dismissedDay"

    /// Il giornale entra in archivio, poi l'elenco si rilegge (D22). Da chiamare quando
    /// l'app torna davanti e quando il foglio si chiude.
    func refresh() {
        let now = Now.millis
        ArchiveBackend.shared.run({ archive -> Bool in
            let torn = archive.ingest(journalSink: FileJournalSink.shared)
            // Le note cestinate da più di trenta giorni, con foto e registrazioni (D39, D63).
            PhotoFiles.delete(archive.purge(nowMillis: now))
            return torn
        }, then: { [weak self] torn in
            if torn { self?.lostStroke = true }
            self?.reload()
            // Poi, con calma, si legge la scrittura e si trascrive la voce (D62, D63): una
            // nota appena scritta diventa cercabile qualche secondo dopo.
            Recognition.runPending { self?.reload() }
        })
    }

    func reload() {
        let term = query
        let now = Now.millis
        let offset = Now.utcOffsetMillis
        let today = Int(Resurface.shared.dayOf(millis: now, utcOffsetMillis: offset))
        let dismissed = UserDefaults.standard.object(forKey: Self.dismissedDayKey) as? Int
        ArchiveBackend.shared.run({ archive -> ([NoteItem], Int, ResurfacedItem?) in
            let searching = !term.trimmingCharacters(in: .whitespaces).isEmpty
            let notes = searching ? archive.search(term: term, limit: 200) : archive.recent(limit: 500)
            let items = notes.map { NoteItem(archive: archive, note: $0) }
            let count = Int(archive.toSortCount())
            var resurfaced: ResurfacedItem?
            if !searching, dismissed != today, let pick = archive.resurfaced(nowMillis: now, utcOffsetMillis: offset) {
                resurfaced = ResurfacedItem(
                    item: NoteItem(archive: archive, note: pick.note),
                    daysAgo: Int(pick.daysAgo),
                    isAnniversary: pick.isAnniversary
                )
            }
            return (items, count, resurfaced)
        }, then: { [weak self] result in
            self?.notes = result.0
            self?.toSortCount = result.1
            self?.resurfaced = result.2
            self?.loaded = true
        })
    }

    func dismissResurfaced() {
        let today = Int(Resurface.shared.dayOf(millis: Now.millis, utcOffsetMillis: Now.utcOffsetMillis))
        UserDefaults.standard.set(today, forKey: Self.dismissedDayKey)
        resurfaced = nil
    }

    /// Un tombstone, non una cancellazione (D8, D26).
    func delete(_ item: NoteItem) {
        notes.removeAll { $0.id == item.id }
        let now = Now.millis
        ArchiveBackend.shared.run({ archive in archive.delete(id: item.id, nowMillis: now) }, then: { [weak self] _ in
            self?.reload()
        })
    }
}
