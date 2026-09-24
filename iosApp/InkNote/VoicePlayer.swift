import AVFoundation
import InkNoteKit

/// Il riascolto delle registrazioni nella nota aperta (D63). Una alla volta: toccarne
/// un'altra ferma quella in corso.
final class VoicePlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var playingId: String?
    private var player: AVAudioPlayer?

    func toggle(_ voice: VoiceItem) {
        if playingId == voice.id {
            stop()
            return
        }
        stop()
        guard let path = voice.path, let url = PhotoFiles.url(of: path) else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio)
            try session.setActive(true)
            let player = try AVAudioPlayer(contentsOf: url)
            player.delegate = self
            guard player.play() else { throw CocoaError(.fileReadUnknown) }
            self.player = player
            playingId = voice.id
        } catch {
            stop()
        }
    }

    func stop() {
        player?.stop()
        player = nil
        playingId = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { self.stop() }
    }
}

extension VoiceItem {
    /// "0:07", "1:32": la durata come la legge chi guarda.
    var durationLabel: String {
        let seconds = max(1, Int(durationMs) / 1000)
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
