import InkNoteKit
import SwiftUI
import UIKit

/// Il foglio in SwiftUI: un involucro sottile attorno al controller UIKit.
struct CaptureScreen: UIViewControllerRepresentable {
    let requestedAt: Date
    let onDone: () -> Void

    func makeUIViewController(context: Context) -> CaptureViewController {
        let controller = CaptureViewController(requestedAt: requestedAt)
        controller.onDone = onDone
        return controller
    }

    func updateUIViewController(_ controller: CaptureViewController, context: Context) {}
}

/// Il foglio (D20, D46): tutta la carta dello schermo, e in basso a destra "Fatto".
///
/// **Un foglio vive finché è sullo schermo** (D34): se l'app va in secondo piano il foglio
/// si chiude, e prima ancora si copre, così l'istantanea che iOS mostra nel selettore
/// delle app è carta bianca e non la nota.
final class CaptureViewController: UIViewController {

    var onDone: () -> Void = {}

    private let requestedAt: Date
    private var canvas: InkCanvasView?
    private let cover = UIView()
    private let meter = UILabel()
    private var firstFrameAt: Date?

    init(requestedAt: Date) {
        self.requestedAt = requestedAt
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("non usata da storyboard") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(Brand.paper)

        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(willResignActive), name: UIApplication.willResignActiveNotification, object: nil)
        center.addObserver(self, selector: #selector(didBecomeActive), name: UIApplication.didBecomeActiveNotification, object: nil)
        center.addObserver(self, selector: #selector(didEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard canvas == nil, view.bounds.width > 0 else { return }
        installCanvas()
    }

    private func installCanvas() {
        // Il foglio è grande quanto lo schermo, in punti: unità logiche come i dp (D10).
        let sheet = InkSheet(
            canvasWidth: Float(view.bounds.width),
            canvasHeight: Float(view.bounds.height),
            journalSink: FileJournalSink.shared,
            clock: IosPlatform.shared.wallClock
        )
        let canvas = InkCanvasView(sheet: sheet)
        canvas.frame = view.bounds
        canvas.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(canvas)
        self.canvas = canvas

        // "Fatto": l'uscita (D5, D21). Non salva, è già tutto nel giornale.
        var config = UIButton.Configuration.filled()
        config.title = String(localized: "Done")
        config.image = UIImage(systemName: "checkmark")
        config.imagePadding = 8
        config.cornerStyle = .capsule
        config.baseBackgroundColor = UIColor(Brand.ink)
        config.baseForegroundColor = UIColor(Brand.paper)
        config.contentInsets = NSDirectionalEdgeInsets(top: 14, leading: 22, bottom: 14, trailing: 22)
        let done = UIButton(configuration: config, primaryAction: UIAction { [weak self] _ in self?.finish() })
        done.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(done)
        NSLayoutConstraint.activate([
            done.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            done.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
        ])

        #if DEBUG
        installMeter(canvas: canvas)
        #endif

        cover.backgroundColor = UIColor(Brand.paper)
        cover.frame = view.bounds
        cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        cover.isHidden = true
        view.addSubview(cover)
    }

    private func finish() {
        canvas?.commitIfDrawing()
        onDone()
    }

    // MARK: Ciclo di vita (D34)

    @objc private func willResignActive() {
        // Prima dell'istantanea del selettore delle app: carta bianca, non la nota.
        canvas?.commitIfDrawing()
        cover.isHidden = false
    }

    @objc private func didBecomeActive() {
        cover.isHidden = true
    }

    @objc private func didEnterBackground() {
        // Le scritture in coda vanno su disco adesso: il sistema può chiudere l'app quando
        // vuole. Poi il foglio si chiude, e con lui la nota.
        FileJournalSink.shared.awaitWrites()
        onDone()
    }

    // MARK: Misura (D19, D32, D36), solo nelle build di debug

    #if DEBUG
    private func installMeter(canvas: InkCanvasView) {
        meter.font = .systemFont(ofSize: 11, weight: .medium)
        meter.textColor = UIColor(Brand.inkMuted)
        meter.text = "attrito: in misura…"
        meter.translatesAutoresizingMaskIntoConstraints = false
        meter.isUserInteractionEnabled = true
        meter.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(hideMeter)))
        view.addSubview(meter)
        NSLayoutConstraint.activate([
            meter.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            meter.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -76),
        ])

        canvas.onFirstFrame = { [weak self] in self?.firstFrameAt = Date() }
        canvas.onFirstInk = { [weak self] touched in
            guard let self else { return }
            let touchToInk = Int((ProcessInfo.processInfo.systemUptime - touched) * 1000)
            let (start, kind) = Launch.startOfThisOpening(requestedAt: requestedAt)
            let ready = self.firstFrameAt.map { Int($0.timeIntervalSince(start) * 1000) } ?? -1
            let budget = kind == "freddo" ? 400 : 100
            self.meter.text = "pronto \(kind): \(ready)ms (\(ready <= budget ? "entro" : "OLTRE") \(budget)ms) · tocco→inchiostro \(touchToInk)ms"
        }
    }

    @objc private func hideMeter() {
        meter.isHidden = true
    }
    #endif
}

/// Da quando contare un'apertura (D32): a freddo dall'avvio del processo, che il
/// sistema sa dire; a caldo dalla richiesta arrivata all'app, che è ottimista quanto su
/// Android.
enum Launch {
    private static var reported = false

    static func startOfThisOpening(requestedAt: Date) -> (Date, String) {
        defer { reported = true }
        if !reported, let processStart = processStartDate(), Date().timeIntervalSince(processStart) < 10 {
            return (processStart, "freddo")
        }
        return (requestedAt, "caldo")
    }

    private static func processStartDate() -> Date? {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        guard sysctl(&mib, u_int(mib.count), &info, &size, nil, 0) == 0 else { return nil }
        let start = info.kp_proc.p_un.__p_starttime
        return Date(timeIntervalSince1970: TimeInterval(start.tv_sec) + TimeInterval(start.tv_usec) / 1_000_000)
    }
}
