import SwiftUI
import UIKit

/// I colori e il segno di InkNote, condivisi fra app e widget (D46).
///
/// La carta resta chiara anche di notte: l'inchiostro è scuro, e un foglio scuro lo
/// renderebbe invisibile. Di notte si scurisce solo la scrivania.
enum Brand {
    static let paper = Color(red: 0xFB / 255, green: 0xF8 / 255, blue: 0xF1 / 255)
    static let card = Color(red: 0xFF / 255, green: 0xFD / 255, blue: 0xF8 / 255)
    static let ink = Color(red: 0x1F / 255, green: 0x24 / 255, blue: 0x30 / 255)
    static let inkMuted = Color(red: 0x8B / 255, green: 0x83 / 255, blue: 0x74 / 255)
    static let scribbleTint = Color(red: 0xD9 / 255, green: 0xCE / 255, blue: 0xB8 / 255)

    /// La goccia vermiglia del marchio (D66, D71). Dentro l'app ha un solo significato:
    /// "questa nota aspetta lo smistamento" (D72). Non è un colore da decorazione.
    static let spark = Color(red: 0xE4 / 255, green: 0x57 / 255, blue: 0x2E / 255)

    /// La scrivania: chiara di giorno, scura di notte.
    static let desk = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0x14 / 255, green: 0x13 / 255, blue: 0x11 / 255, alpha: 1)
            : UIColor(red: 0xF3 / 255, green: 0xEF / 255, blue: 0xE7 / 255, alpha: 1)
    })

    static let outline = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0x33 / 255, green: 0x30 / 255, blue: 0x2A / 255, alpha: 1)
            : UIColor(red: 0xE0 / 255, green: 0xD8 / 255, blue: 0xC8 / 255, alpha: 1)
    })

    /// Il foglio di scrittura (D52): di notte si scurisce e l'inchiostro diventa chiaro.
    ///
    /// Chi scrive un'idea a letto al buio non deve essere accecato da un rettangolo bianco.
    /// Vale solo per il foglio: nell'archivio i bigliettini restano carta (D46). Ed è solo
    /// un modo di mostrare: i tratti salvati hanno sempre l'inchiostro del giorno (D7).
    static let sheetPaper = UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0x1B / 255, green: 0x1A / 255, blue: 0x17 / 255, alpha: 1)
            : UIColor(red: 0xFB / 255, green: 0xF8 / 255, blue: 0xF1 / 255, alpha: 1)
    }

    static let sheetInk = UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0xEC / 255, green: 0xE5 / 255, blue: 0xD6 / 255, alpha: 1)
            : UIColor(red: 0x1F / 255, green: 0x24 / 255, blue: 0x30 / 255, alpha: 1)
    }

    static let sheetMuted = UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0x8F / 255, green: 0x88 / 255, blue: 0x7A / 255, alpha: 1)
            : UIColor(red: 0x8B / 255, green: 0x83 / 255, blue: 0x74 / 255, alpha: 1)
    }

    /// L'indirizzo che apre il foglio: widget, Centro di Controllo, scorciatoie.
    static let captureURL = URL(string: "inknote://capture")!
}

/// Il ricciolo del widget (D30): "qui si scrive", senza dire altro. Lo stesso disegno
/// dell'icona e del widget Android, in coordinate 24×24.
struct Scribble: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 24
        let ox = rect.midX - 12 * s
        let oy = rect.midY - 12 * s
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: ox + x * s, y: oy + y * s) }
        var path = Path()
        path.move(to: p(4, 19.5))
        path.addCurve(to: p(12, 8), control1: p(7, 18.5), control2: p(9, 13))
        path.addCurve(to: p(16.8, 4.6), control1: p(14.2, 4.3), control2: p(16, 3.7))
        path.addCurve(to: p(14.4, 10.2), control1: p(17.7, 5.6), control2: p(16.4, 7.8))
        path.addCurve(to: p(11.4, 14.5), control1: p(12.8, 12.1), control2: p(11.4, 13.2))
        path.addCurve(to: p(13.2, 15.7), control1: p(11.4, 15.5), control2: p(12.2, 16.0))
        path.addCurve(to: p(17.0, 12.5), control1: p(14.6, 15.3), control2: p(15.8, 14.1))
        return path
    }
}
