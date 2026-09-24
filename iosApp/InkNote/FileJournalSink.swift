import Foundation
import InkNoteKit

/// Il giornale dell'inchiostro su disco, per iOS (D20, D35).
///
/// Tutta la logica sta nel core; qui c'è solo la scrittura vera. Come su Android, ogni
/// operazione passa da **una coda sola**, fuori dal thread dell'interfaccia: `fsync`
/// aspetta la memoria flash, e fatto al sollevamento del dito cadrebbe fra una parola e
/// l'altra. Essere una sola è anche ciò che rende le tre operazioni esclusive fra loro.
///
/// Su iOS solo l'app scrive il giornale — il widget non scrive niente (D30) — quindi non
/// serve un lock fra processi.
final class FileJournalSink: NSObject, InkJournalSink {

    static let shared = FileJournalSink()

    private let url: URL
    private let queue = DispatchQueue(label: "app.inknote.giornale")
    private(set) var failures = 0

    private override init() {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        url = folder.appendingPathComponent("ink-journal.bin")
        super.init()
    }

    func append(record: KotlinByteArray) {
        // La copia si fa qui, sul thread di chi chiama: l'array di Kotlin non va letto da
        // un'altra coda mentre il core potrebbe riusarlo.
        let data = Data(kotlin: record)
        queue.async { [self] in
            do {
                if !FileManager.default.fileExists(atPath: url.path) {
                    FileManager.default.createFile(atPath: url.path, contents: nil)
                }
                let handle = try FileHandle(forWritingTo: url)
                defer { try? handle.close() }
                try handle.seekToEnd()
                try handle.write(contentsOf: data)
                // `synchronize` è `fsync`: senza, i byte restano nei buffer del sistema e
                // un'app chiusa subito dopo li porta via (D20).
                try handle.synchronize()
            } catch {
                failures += 1
            }
        }
    }

    func readAll() -> KotlinByteArray {
        queue.sync { KotlinByteArray.from((try? Data(contentsOf: url)) ?? Data()) }
    }

    /// Toglie dalla testa solo i byte già letti e salvati in archivio (D35).
    func discardPrefix(byteCount: Int32) {
        queue.sync {
            guard let data = try? Data(contentsOf: url) else { return }
            let count = Int(byteCount)
            if count >= data.count {
                try? FileManager.default.removeItem(at: url)
                return
            }
            // Il resto in un file nuovo, poi al posto del vecchio: la sostituzione è
            // atomica, e un'app chiusa a metà lascia il giornale vecchio intero.
            let rest = data.subdata(in: max(0, count)..<data.count)
            try? rest.write(to: url, options: .atomic)
        }
    }

    /// Aspetta che le scritture in coda siano su disco: quando l'app va in secondo piano.
    func awaitWrites() {
        queue.sync {}
    }
}

extension Data {
    /// Da Kotlin a Swift, byte per byte. I record del giornale sono di pochi kilobyte.
    init(kotlin array: KotlinByteArray) {
        let count = Int(array.size)
        var bytes = [UInt8](repeating: 0, count: count)
        for index in 0..<count {
            bytes[index] = UInt8(bitPattern: array.get(index: Int32(index)))
        }
        self.init(bytes)
    }
}

extension KotlinByteArray {
    static func from(_ data: Data) -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return array
    }
}
