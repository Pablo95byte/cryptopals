import InkNoteKit
import SwiftUI
import UIKit

/// Il foglio (D20, D46): tutta la carta dello schermo, in basso a destra "Fatto", in basso a
/// sinistra tastiera, fotocamera e microfono, tenui (D38, D63).
///
/// **Un foglio vive finché è sullo schermo** (D34): se l'app va in secondo piano il foglio
/// si chiude, e prima ancora si copre, così l'istantanea che iOS mostra nel selettore
/// delle app è carta bianca e non la nota.
final class CaptureViewController: UIViewController, UITextViewDelegate,
    UIImagePickerControllerDelegate, UINavigationControllerDelegate {

    var onDone: () -> Void = {}

    private let requestedAt: Date
    private var canvas: InkCanvasView?
    private let cover = UIView()
    private let meter = UILabel()
    private var firstFrameAt: Date?

    /// Il testo digitato (D38): una scheda sotto la barra di stato, che compare solo se la si chiede.
    private let textCard = UIView()
    private let textView = UITextView()
    private var textCommit: DispatchWorkItem?

    /// Le miniature delle foto scattate e delle registrazioni, sopra gli strumenti.
    private let photoStrip = UIStackView()

    /// La voce (D63): un tocco sul microfono comincia, un tocco sulla pillola finisce.
    private let recorder = VoiceRecorder()
    private var recordingPill: UIButton?
    private var recordingTimer: Timer?
    private var startingRecording = false

    init(requestedAt: Date) {
        self.requestedAt = requestedAt
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("non usata da storyboard") }

    override func viewDidLoad() {
        super.viewDidLoad()
        // Di notte il foglio si scurisce (D52): colore dinamico, deciso dal sistema.
        view.backgroundColor = Brand.sheetPaper

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
        config.baseBackgroundColor = Brand.sheetInk
        config.baseForegroundColor = Brand.sheetPaper
        config.contentInsets = NSDirectionalEdgeInsets(top: 14, leading: 22, bottom: 14, trailing: 22)
        let done = UIButton(configuration: config, primaryAction: UIAction { [weak self] _ in self?.finish() })
        done.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(done)

        // Gli strumenti: due icone sulla carta, senza pillola e senza ombra (D46). Si possono
        // ignorare senza pensarci, quindi non sono una decisione (D21).
        let keyboard = toolButton("keyboard", label: String(localized: "Type")) { [weak self] in self?.showTextCard() }
        let camera = toolButton("camera", label: String(localized: "Photo")) { [weak self] in self?.takePhoto() }
        let microphone = toolButton("mic", label: String(localized: "Record")) { [weak self] in self?.toggleRecording() }
        let tools = UIStackView(arrangedSubviews: [keyboard, camera, microphone])
        tools.spacing = 4
        tools.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tools)

        photoStrip.spacing = 8
        photoStrip.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(photoStrip)

        installTextCard()

        let pill = recordingButton()
        view.addSubview(pill)
        recordingPill = pill

        NSLayoutConstraint.activate([
            pill.trailingAnchor.constraint(equalTo: done.trailingAnchor),
            pill.bottomAnchor.constraint(equalTo: done.topAnchor, constant: -14),
            done.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            done.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -12),
            tools.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 8),
            tools.centerYAnchor.constraint(equalTo: done.centerYAnchor),
            photoStrip.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            photoStrip.bottomAnchor.constraint(equalTo: done.topAnchor, constant: -14),
        ])

        #if DEBUG
        installMeter(canvas: canvas)
        #endif

        cover.backgroundColor = Brand.sheetPaper
        cover.frame = view.bounds
        cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        cover.isHidden = true
        view.addSubview(cover)
    }

    private func toolButton(_ symbol: String, label: String, action: @escaping () -> Void) -> UIButton {
        var config = UIButton.Configuration.plain()
        config.image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .regular))
        config.baseForegroundColor = Brand.sheetMuted
        config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)
        let button = UIButton(configuration: config, primaryAction: UIAction { _ in action() })
        button.accessibilityLabel = label
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(greaterThanOrEqualToConstant: 48),
            button.heightAnchor.constraint(greaterThanOrEqualToConstant: 48),
        ])
        return button
    }

    private func finish() {
        canvas?.commitIfDrawing()
        commitTextNow()
        keepRecordingNow()
        // Un tocco breve: la nota è al sicuro, senza bisogno di guardare (D52).
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onDone()
    }

    // MARK: Testo digitato (D38)

    private func installTextCard() {
        textCard.backgroundColor = UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 0x26 / 255, green: 0x24 / 255, blue: 0x20 / 255, alpha: 1)
                : UIColor(red: 0xFF / 255, green: 0xFD / 255, blue: 0xF8 / 255, alpha: 1)
        }
        textCard.layer.cornerRadius = 20
        textCard.layer.cornerCurve = .continuous
        textCard.layer.borderWidth = 1
        textCard.layer.borderColor = UIColor(Brand.outline).cgColor
        textCard.isHidden = true
        textCard.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(textCard)

        textView.font = .systemFont(ofSize: 18)
        textView.textColor = Brand.sheetInk
        textView.backgroundColor = .clear
        textView.delegate = self
        textView.textContainerInset = UIEdgeInsets(top: 14, left: 12, bottom: 14, right: 12)
        textView.isScrollEnabled = true
        textView.translatesAutoresizingMaskIntoConstraints = false
        textCard.addSubview(textView)

        NSLayoutConstraint.activate([
            textCard.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            textCard.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            textCard.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            textCard.heightAnchor.constraint(equalToConstant: 150),
            textView.topAnchor.constraint(equalTo: textCard.topAnchor),
            textView.bottomAnchor.constraint(equalTo: textCard.bottomAnchor),
            textView.leadingAnchor.constraint(equalTo: textCard.leadingAnchor),
            textView.trailingAnchor.constraint(equalTo: textCard.trailingAnchor),
        ])
    }

    private func showTextCard() {
        textCard.isHidden = false
        textView.becomeFirstResponder()
    }

    func textViewDidChange(_ textView: UITextView) {
        // Nel giornale dopo una breve pausa nella digitazione: non a ogni lettera, che
        // sarebbe un pezzo nuovo e un tombstone per ogni tasto (D38).
        textCommit?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.commitTextNow() }
        textCommit = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8, execute: work)
    }

    func textViewDidEndEditing(_ textView: UITextView) {
        commitTextNow()
    }

    private func commitTextNow() {
        textCommit?.cancel()
        textCommit = nil
        guard let sheet = canvas?.sheet, !textCard.isHidden else { return }
        sheet.commitText(text: textView.text ?? "")
    }

    // MARK: Foto (D38)

    private func takePhoto() {
        let picker = UIImagePickerController()
        // Il simulatore non ha fotocamera: lì si prende dalla libreria, che non chiede permessi.
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = self
        // Il mirino si apre sopra il foglio, dentro l'app: niente cambio d'app, niente
        // foglio chiuso come succedeva su Android con la fotocamera di sistema (D45).
        present(picker, animated: true)
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage, let sheet = canvas?.sheet else { return }
        // La foto entra nella nota subito, il file si scrive su un'altra coda (D45).
        let path = PhotoFiles.newRelativePath()
        sheet.addPhoto(relativePath: path)
        PhotoFiles.write(image, to: path)
        addThumbnail(image)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }

    private func addThumbnail(_ image: UIImage) {
        let thumb = UIImageView(image: image.scaledToFit(maxSide: 200))
        thumb.contentMode = .scaleAspectFill
        thumb.clipsToBounds = true
        thumb.layer.cornerRadius = 12
        thumb.layer.cornerCurve = .continuous
        thumb.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            thumb.widthAnchor.constraint(equalToConstant: 56),
            thumb.heightAnchor.constraint(equalToConstant: 56),
        ])
        photoStrip.addArrangedSubview(thumb)
    }

    // MARK: Voce (D18, D63)

    /// La pillola che dice "si sta registrando" e che, toccata, ferma. Piena, perché mentre
    /// il microfono è acceso deve essere la cosa più evidente del foglio.
    private func recordingButton() -> UIButton {
        var config = UIButton.Configuration.filled()
        config.image = UIImage(systemName: "stop.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 13, weight: .bold))
        config.imagePadding = 8
        config.cornerStyle = .capsule
        config.baseBackgroundColor = .systemRed
        config.baseForegroundColor = .white
        config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 18, bottom: 12, trailing: 18)
        let button = UIButton(configuration: config, primaryAction: UIAction { [weak self] _ in self?.stopRecording() })
        button.accessibilityLabel = String(localized: "Stop recording")
        button.isHidden = true
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }

    private func toggleRecording() {
        if recorder.isRecording { stopRecording(); return }
        guard !startingRecording else { return }
        startingRecording = true
        VoiceRecorder.requestAccess { [weak self] granted in
            guard let self else { return }
            guard granted else {
                self.startingRecording = false
                self.explainMicrophoneOff()
                return
            }
            self.recorder.start { error in
                self.startingRecording = false
                if let error {
                    self.explainRecordingFailed(error)
                    return
                }
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                self.recordingPill?.isHidden = false
                self.updateRecordingPill()
                self.recordingTimer = Timer.scheduledTimer(
                    timeInterval: 0.5,
                    target: self,
                    selector: #selector(self.updateRecordingPill),
                    userInfo: nil,
                    repeats: true
                )
            }
        }
    }

    @objc private func updateRecordingPill() {
        let seconds = Int(Date().timeIntervalSince(recorder.startedAt ?? Date()))
        recordingPill?.configuration?.title = String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func hideRecordingPill() {
        recordingTimer?.invalidate()
        recordingTimer = nil
        recordingPill?.isHidden = true
    }

    private func stopRecording() {
        hideRecordingPill()
        // Il foglio del core si tiene forte: se il foglio si chiude mentre il file si chiude,
        // la registrazione entra nel giornale lo stesso.
        guard let sheet = canvas?.sheet else { return }
        recorder.stop { [weak self] result in
            guard let result else { return }
            sheet.addVoice(relativePath: result.path, durationMs: Int32(result.durationMs))
            self?.addVoiceChip(durationMs: result.durationMs)
        }
    }

    /// Il foglio sta per chiudersi: la registrazione in corso finisce qui, ed entra nel
    /// giornale prima che il sistema possa chiudere l'app (D34).
    private func keepRecordingNow() {
        guard recorder.isRecording else { return }
        hideRecordingPill()
        guard let result = recorder.stopNow(), let sheet = canvas?.sheet else { return }
        sheet.addVoice(relativePath: result.path, durationMs: Int32(result.durationMs))
        addVoiceChip(durationMs: result.durationMs)
    }

    private func addVoiceChip(durationMs: Int) {
        let seconds = max(1, durationMs / 1000)
        var config = UIButton.Configuration.plain()
        config.image = UIImage(systemName: "waveform", withConfiguration: UIImage.SymbolConfiguration(pointSize: 14, weight: .medium))
        config.title = String(format: "%d:%02d", seconds / 60, seconds % 60)
        config.imagePadding = 6
        config.baseForegroundColor = Brand.sheetInk
        config.background.backgroundColor = Brand.sheetMuted.withAlphaComponent(0.16)
        config.cornerStyle = .capsule
        let chip = UIButton(configuration: config)
        chip.isUserInteractionEnabled = false
        chip.accessibilityLabel = String(localized: "Voice note")
        photoStrip.addArrangedSubview(chip)
    }

    /// Un microfono che non parte deve dirlo: un tocco senza risposta sembra un'app rotta.
    private func explainRecordingFailed(_ error: Error) {
        let alert = UIAlertController(
            title: String(localized: "Can't record right now"),
            message: String(localized: "Another app may be using the microphone. Try again in a moment."),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: String(localized: "OK"), style: .default))
        present(alert, animated: true)
    }

    private func explainMicrophoneOff() {
        let alert = UIAlertController(
            title: String(localized: "Microphone is off"),
            message: String(localized: "To record voice notes, allow Instink to use the microphone in Settings."),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: String(localized: "Settings"), style: .default) { _ in
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        })
        alert.addAction(UIAlertAction(title: String(localized: "Not now"), style: .cancel))
        present(alert, animated: true)
    }

    // MARK: Ciclo di vita (D34)

    @objc private func willResignActive() {
        // Prima dell'istantanea del selettore delle app: carta, non la nota.
        canvas?.commitIfDrawing()
        commitTextNow()
        // Una chiamata, il Centro di Controllo: la registrazione si ferma e resta.
        keepRecordingNow()
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
        meter.textColor = Brand.sheetMuted
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
