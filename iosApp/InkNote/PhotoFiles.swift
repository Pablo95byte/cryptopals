import UIKit

/// Le foto delle note su disco (D38), e con loro le registrazioni (D63): stessa cartella,
/// stesse regole sui percorsi.
///
/// Il modello conosce solo percorsi **relativi** ("photos/<id>.jpg", "voice/<id>.m4a"): la cartella vera è una
/// faccenda della piattaforma, e su iOS sta in Application Support, che va nel backup di
/// iCloud insieme all'archivio (D49).
enum PhotoFiles {

    static let base: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]

    private static let queue = DispatchQueue(label: "app.inknote.foto", qos: .userInitiated)

    /// Un percorso nuovo, prima che il file esista: la foto entra nella nota subito.
    static func newRelativePath() -> String { "photos/\(UUID().uuidString.lowercased()).jpg" }

    /// Il file di un percorso relativo, o `nil` se il percorso prova a uscire dalla cartella.
    static func url(of relativePath: String) -> URL? {
        guard !relativePath.contains(".."), !relativePath.hasPrefix("/") else { return nil }
        return base.appendingPathComponent(relativePath)
    }

    /// Scrive la foto su un'altra coda: comprimere e scrivere non tocca il thread del foglio
    /// (invariante 20). Lato lungo al massimo 2048 punti: basta per una lavagna o uno
    /// scontrino, e il backup resta leggero.
    static func write(_ image: UIImage, to relativePath: String) {
        queue.async {
            guard let url = url(of: relativePath) else { return }
            let scaled = image.scaledToFit(maxSide: 2048)
            guard let data = scaled.jpegData(compressionQuality: 0.82) else { return }
            try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }

    static func load(_ relativePath: String) -> UIImage? {
        guard let url = url(of: relativePath) else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    static func delete(_ relativePaths: [String]) {
        for path in relativePaths {
            if let url = url(of: path) { try? FileManager.default.removeItem(at: url) }
        }
    }
}

extension UIImage {
    /// La stessa immagine con il lato lungo al massimo [maxSide], già raddrizzata: la
    /// fotocamera salva l'orientamento a parte, e altri programmi lo ignorano.
    func scaledToFit(maxSide: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        let factor = longest > maxSide ? maxSide / longest : 1
        let target = CGSize(width: (size.width * factor).rounded(), height: (size.height * factor).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
