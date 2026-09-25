import InkNoteKit
import SwiftUI
import UIKit

/// "Manda a…" (D31): il foglio di condivisione del sistema, con il testo, l'immagine
/// dell'inchiostro, le foto e le registrazioni. Da lì la nota arriva a Notes, Notion, Keep, Obsidian, una
/// mail — e ad app che ancora non esistono.
enum ShareNote {

    /// Quello che si manda. Il testo serve a leggerla e ritrovarla dove arriva;
    /// l'immagine è l'originale, e non sbaglia parole.
    static func items(for note: Note, archive: InkArchive) -> [Any] {
        var items: [Any] = []
        let date = DateFormatter.localizedString(
            from: Date(timeIntervalSince1970: TimeInterval(note.createdAt) / 1000),
            dateStyle: .medium,
            timeStyle: .short
        )
        // La firma è l'anello di crescita (D68): chi riceve la nota scopre da dove viene.
        let signature = String(localized: "Written with Instink · instink.app")
        if let text = archive.shareText(note: note, dateLabel: date, signature: signature) { items.append(text) }
        if let ink = inkImage(of: note, marked: true) { items.append(ink) }
        for path in archive.photoPaths(note: note) {
            if let photo = PhotoFiles.load(path) { items.append(photo) }
        }
        // Le registrazioni come file: la trascrizione è già nel testo, l'audio è l'originale
        // (D25). Chi riceve può riascoltare ciò che il riconoscimento ha capito male.
        for voice in archive.voiceItems(note: note) {
            if let path = voice.path, let url = PhotoFiles.url(of: path),
               FileManager.default.fileExists(atPath: url.path) {
                items.append(url)
            }
        }
        return items
    }

    /// L'inchiostro su carta, largo 1080 pixel: si legge su qualunque schermo e pesa poco.
    /// Sempre su carta chiara, anche di notte: la nota andrà a vivere altrove (D52).
    ///
    /// [marked] aggiunge in fondo, in una fascia sua, la goccia e il nome (D76): la nota
    /// nei messaggi viaggia come immagine, e la firma del testo lì si perde. Solo per ciò
    /// che esce dall'app: l'immagine che legge Vision (D62) resta senza, altrimenti ogni
    /// nota conterrebbe la parola "Instink".
    static func inkImage(of note: Note, width: CGFloat = 1080, marked: Bool = false) -> UIImage? {
        guard note.hasInk else { return nil }
        let inkHeight = CGFloat(InkPreview.shared.heightFor(note: note, width: Float(width), min: 360, max: 2400))
        let shapes = InkPreview.shared.shapes(note: note, width: Float(width), height: Float(inkHeight), padding: 48)
        guard !shapes.isEmpty else { return nil }
        let band: CGFloat = marked ? width * 0.06 : 0
        let height = inkHeight + band
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor(Brand.paper).setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
            for shape in shapes {
                context.cgContext.addPath(InkCanvasView.path(of: shape))
                context.cgContext.setFillColor(InkCanvasView.color(of: shape).cgColor)
                context.cgContext.fillPath()
            }
            if marked { drawMark(width: width, bottom: height, band: band) }
        }
    }

    /// La goccia vermiglia e "Instink", tenui, in basso a destra: si riconosce, non disturba.
    private static func drawMark(width: CGFloat, bottom: CGFloat, band: CGFloat) {
        let size = band * 0.38
        let font = UIFont.systemFont(ofSize: size, weight: .semibold)
        let text = NSAttributedString(string: "Instink", attributes: [
            .font: font,
            .foregroundColor: UIColor(red: 0xA8 / 255, green: 0x9F / 255, blue: 0x8F / 255, alpha: 1),
        ])
        let textSize = text.size()
        let margin = band * 0.55
        let origin = CGPoint(x: width - margin - textSize.width, y: bottom - band / 2 - textSize.height / 2)
        text.draw(at: origin)
        let dot = size * 0.42
        UIColor(Brand.spark).setFill()
        UIBezierPath(ovalIn: CGRect(
            x: origin.x - dot - size * 0.35,
            y: bottom - band / 2 - dot / 2,
            width: dot,
            height: dot
        )).fill()
    }
}

/// Il foglio di condivisione in SwiftUI.
///
/// L'invio si registra **solo se l'utente ha scelto una destinazione** (invariante 18):
/// aprire il foglio e chiuderlo non è mandare, e registrarlo lo toglierebbe dalla coda da
/// smistare senza che sia arrivato da nessuna parte.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    let onFinish: (_ sent: Bool) -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.completionWithItemsHandler = { _, completed, _, _ in onFinish(completed) }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Una nota pronta da mandare: gli oggetti si preparano sulla coda dell'archivio, perché
/// leggere le foto e disegnare l'inchiostro non va fatto sul thread dell'interfaccia.
struct SharePayload: Identifiable {
    let id: String
    let items: [Any]

    static func prepare(noteId: String, then: @escaping (SharePayload?) -> Void) {
        ArchiveBackend.shared.run({ archive -> SharePayload? in
            guard let note = archive.note(id: noteId), !note.isDeleted else { return nil }
            let items = ShareNote.items(for: note, archive: archive)
            return items.isEmpty ? nil : SharePayload(id: noteId, items: items)
        }, then: then)
    }

    static func markSent(_ noteId: String, then: @escaping () -> Void = {}) {
        let now = Now.millis
        ArchiveBackend.shared.run({ archive in archive.markShared(id: noteId, nowMillis: now) }, then: { _ in then() })
    }
}
