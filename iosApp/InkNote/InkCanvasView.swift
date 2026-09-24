import InkNoteKit
import UIKit

/// La superficie di scrittura (D20): una `UIView` di UIKit, non SwiftUI, perché serve il
/// controllo dei tocchi che SwiftUI non dà — i campioni intermedi dell'Apple Pencil, la
/// pressione, il palmo appoggiato.
///
/// Non calcola niente della calligrafia: passa i campioni a `InkSheet` e riempie i
/// contorni che riceve. Così il tratto su iPhone è quello di Android (D6).
final class InkCanvasView: UIView {

    let sheet: InkSheet

    /// Tappe della misura (D36): il primo fotogramma, e il primo inchiostro disegnato.
    var onFirstFrame: (() -> Void)?
    var onFirstInk: ((_ touchTimestamp: TimeInterval) -> Void)?

    /// I tratti chiusi, col loro colore **salvato**: quello da mostrare si decide al disegno,
    /// perché di notte cambia (D52).
    private var committed: [(path: CGPath, argb: Int32, alpha: Float)] = []
    private var activeTouch: UITouch?
    private var firstTouchTimestamp: TimeInterval?
    private var reportedFrame = false
    private var reportedInk = false

    init(sheet: InkSheet) {
        self.sheet = sheet
        super.init(frame: .zero)
        backgroundColor = Brand.sheetPaper
        isMultipleTouchEnabled = true
        contentMode = .redraw
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("non usata da storyboard") }

    // MARK: Tocchi

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        // Un secondo dito mentre si scrive è il palmo: non apre un tratto (D8, InkSheet).
        guard activeTouch == nil, let touch = touches.first else { return }
        let pencil = touch.type == .pencil
        guard sheet.begin(pencil: pencil, timestampMs: Self.millis(touch.timestamp)) else { return }
        activeTouch = touch
        if firstTouchTimestamp == nil { firstTouchTimestamp = touch.timestamp }
        addSamples(of: touch, event: event)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        addSamples(of: touch, event: event)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        addSamples(of: touch, event: event)
        finishStroke()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch) else { return }
        finishStroke()
    }

    /// Chiude il tratto in corso, se c'è: quando l'app va in secondo piano col dito sul vetro.
    func commitIfDrawing() {
        if activeTouch != nil { finishStroke() }
    }

    private func addSamples(of touch: UITouch, event: UIEvent?) {
        // I campioni intermedi: l'Apple Pencil ne produce fino a 240 al secondo, lo schermo
        // ne consegna 60 o 120. Ignorarli vuol dire buttare metà del tratto.
        let samples = event?.coalescedTouches(for: touch) ?? [touch]
        for sample in samples {
            let point = sample.preciseLocation(in: self)
            // Pressione solo dal pennino: il dito non ce l'ha, e inventarla è ciò che
            // l'invariante 6 vieta.
            let force: Float = sample.type == .pencil && sample.maximumPossibleForce > 0
                ? Float(sample.force / sample.maximumPossibleForce)
                : -1
            _ = sheet.add(x: Float(point.x), y: Float(point.y), force: force, timestampMs: Self.millis(sample.timestamp))
        }
        setNeedsDisplay()
    }

    private func finishStroke() {
        activeTouch = nil
        if sheet.end(), let shape = sheet.committedShapes.last {
            committed.append((Self.path(of: shape), shape.color, shape.alpha))
        }
        setNeedsDisplay()
    }

    // MARK: Disegno

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let dark = traitCollection.userInterfaceStyle == .dark
        context.setFillColor(Brand.sheetPaper.resolvedColor(with: traitCollection).cgColor)
        context.fill(bounds)

        for (path, argb, alpha) in committed {
            context.addPath(path)
            context.setFillColor(Self.color(argb: argb, alpha: alpha, dark: dark).cgColor)
            context.fillPath()
        }

        if let live = sheet.liveShape() {
            context.addPath(Self.path(of: live))
            context.setFillColor(Self.color(argb: live.color, alpha: live.alpha, dark: dark).cgColor)
            context.fillPath()
            if !reportedInk, let touched = firstTouchTimestamp {
                reportedInk = true
                onFirstInk?(touched)
            }
        }

        if !reportedFrame {
            reportedFrame = true
            onFirstFrame?()
        }
    }

    static func path(of shape: InkShape) -> CGPath {
        let path = CGMutablePath()
        let count = shape.pointCount
        guard count > 0 else { return path }
        path.move(to: CGPoint(x: CGFloat(shape.x(index: 0)), y: CGFloat(shape.y(index: 0))))
        for index in 1..<max(count, 1) {
            path.addLine(to: CGPoint(x: CGFloat(shape.x(index: index)), y: CGFloat(shape.y(index: index))))
        }
        path.closeSubpath()
        return path
    }

    /// L'inchiostro della casa, lo stesso valore di `InkSheet.INK`.
    static let houseInk = Int32(bitPattern: 0xFF1F_2430)

    /// Il colore di un tratto su carta chiara: l'archivio, le anteprime, le immagini mandate fuori.
    static func color(of shape: InkShape) -> UIColor {
        color(argb: shape.color, alpha: shape.alpha, dark: false)
    }

    /// Il colore da mostrare. Di notte l'inchiostro della casa diventa chiaro sul foglio
    /// scuro (D52); gli altri colori restano i loro.
    static func color(argb: Int32, alpha: Float, dark: Bool) -> UIColor {
        if dark && argb == Self.houseInk {
            return Brand.sheetInk.resolvedColor(with: UITraitCollection(userInterfaceStyle: .dark)).withAlphaComponent(CGFloat(alpha))
        }
        let value = UInt32(bitPattern: argb)
        return UIColor(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: CGFloat(alpha)
        )
    }

    override func traitCollectionDidChange(_ previous: UITraitCollection?) {
        super.traitCollectionDidChange(previous)
        if previous?.userInterfaceStyle != traitCollection.userInterfaceStyle { setNeedsDisplay() }
    }

    /// L'istante dei tocchi è in secondi dall'accensione: la calligrafia vuole millisecondi.
    static func millis(_ timestamp: TimeInterval) -> Int64 { Int64(timestamp * 1000) }
}
