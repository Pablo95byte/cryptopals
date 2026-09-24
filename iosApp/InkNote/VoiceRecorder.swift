import AVFoundation
import Speech

/// Il registratore del foglio (D18, D63): un tocco per cominciare, uno per finire.
///
/// L'audio è la nota, la trascrizione il suo indice (D25): il file resta, e il testo arriva
/// dopo, nell'archivio, riconosciuto sul dispositivo. Il file sta accanto alle foto, in
/// Application Support, con un percorso relativo ("voice/<id>.m4a") come vuole il modello.
///
/// Sessione audio e file si toccano su una coda sua: attivare la sessione può bloccare, e
/// sul thread del foglio non si aspetta il disco (invariante 20).
final class VoiceRecorder {

    private let queue = DispatchQueue(label: "app.inknote.voce", qos: .userInitiated)
    private var recorder: AVAudioRecorder?
    private var path: String?

    /// Sul thread principale: se si sta registrando adesso.
    private(set) var isRecording = false

    /// Da quando si registra, per il contatore sul foglio: letto sul thread principale,
    /// senza chiedere niente alla coda del registratore.
    private(set) var startedAt: Date?

    /// Una registrazione più corta di così è un tocco sbagliato, non una nota.
    private static let minimumDuration: TimeInterval = 0.6

    /// Chiede il microfono e, insieme, la trascrizione sul dispositivo: due domande, una
    /// volta sola, al primo tocco sul microfono e non all'apertura dell'app.
    static func requestAccess(_ completion: @escaping (Bool) -> Void) {
        AVAudioApplication.requestRecordPermission { granted in
            DispatchQueue.main.async {
                guard granted else { completion(false); return }
                guard SFSpeechRecognizer.authorizationStatus() == .notDetermined else { completion(true); return }
                // Il permesso di trascrivere non condiziona la registrazione: se è negato,
                // l'audio resta e non viene trascritto.
                SFSpeechRecognizer.requestAuthorization { _ in
                    DispatchQueue.main.async { completion(true) }
                }
            }
        }
    }

    /// Comincia a registrare. [completion] sul thread principale, con `nil` se la
    /// registrazione è partita, o il motivo per cui non è partita: il foglio lo deve dire,
    /// non tacere.
    func start(_ completion: @escaping (Error?) -> Void) {
        let relative = "voice/\(UUID().uuidString.lowercased()).m4a"
        queue.async { [self] in
            guard let url = PhotoFiles.url(of: relative) else {
                DispatchQueue.main.async { completion(CocoaError(.fileNoSuchFile)) }
                return
            }
            do {
                try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
                let session = AVAudioSession.sharedInstance()
                // Modo `.default`, non `.spokenAudio`: quello è un modo di riproduzione, e
                // con la categoria `.record` iOS lo rifiuta. Era il guasto della prima prova
                // su iPhone: il registratore non partiva e il foglio non diceva niente (D63).
                try session.setCategory(.record, mode: .default)
                try session.setActive(true)
                // AAC mono a 22 kHz: la voce resta chiara, e un minuto pesa meno di mezzo
                // megabyte nel backup (D49).
                let settings: [String: Any] = [
                    AVFormatIDKey: kAudioFormatMPEG4AAC,
                    AVSampleRateKey: 22_050,
                    AVNumberOfChannelsKey: 1,
                    AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
                ]
                let recorder = try AVAudioRecorder(url: url, settings: settings)
                guard recorder.record() else { throw CocoaError(.fileWriteUnknown) }
                self.recorder = recorder
                path = relative
                DispatchQueue.main.async {
                    self.isRecording = true
                    self.startedAt = Date()
                    completion(nil)
                }
            } catch {
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                DispatchQueue.main.async { completion(error) }
            }
        }
    }

    /// Ferma la registrazione. [completion] sul thread principale, con percorso relativo e
    /// durata in millisecondi, o `nil` se non c'era niente da tenere.
    func stop(_ completion: @escaping ((path: String, durationMs: Int)?) -> Void) {
        guard isRecording else { completion(nil); return }
        isRecording = false
        startedAt = nil
        queue.async { [self] in
            let result = finish()
            DispatchQueue.main.async { completion(result) }
        }
    }

    /// Come [stop], ma aspetta: quando il foglio sta per chiudersi (D34) il giornale va
    /// scritto adesso, e l'attesa è quella di `awaitWrites` sul giornale — una volta,
    /// all'uscita, mai mentre si scrive.
    func stopNow() -> (path: String, durationMs: Int)? {
        guard isRecording else { return nil }
        isRecording = false
        startedAt = nil
        return queue.sync { finish() }
    }

    /// Sulla coda del registratore.
    private func finish() -> (path: String, durationMs: Int)? {
        guard let recorder, let path else { return nil }
        // La durata prima di fermare: dopo, il registratore dice zero.
        let duration = recorder.currentTime
        recorder.stop()
        self.recorder = nil
        self.path = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        guard duration >= Self.minimumDuration else {
            PhotoFiles.delete([path])
            return nil
        }
        if let url = PhotoFiles.url(of: path) {
            // Leggibile a telefono bloccato dopo il primo sblocco, come il giornale.
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: url.path
            )
        }
        return (path, Int(duration * 1000))
    }
}
