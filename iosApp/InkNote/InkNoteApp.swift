import SwiftUI
import UIKit

/// L'app: l'archivio, e il foglio che ci si apre sopra (D39).
///
/// Il foglio si apre da ovunque — widget, Centro di Controllo, tasto Azione, scorciatoie,
/// il pulsante "Scrivi" — e sempre **senza animazione**: ogni animazione di apertura è
/// tempo che l'utente aspetta prima di poter scrivere (D19).
@main
struct InkNoteApp: App {
    @StateObject private var router = Router()

    var body: some Scene {
        WindowGroup {
            ArchiveScreen()
                .environmentObject(router)
                .onOpenURL { url in
                    if url.scheme == "inknote", url.host == "capture" { router.openCapture() }
                }
                .onReceive(NotificationCenter.default.publisher(for: CaptureRequests.name)) { _ in
                    if CaptureRequests.consumePending() { router.openCapture() }
                }
                .onAppear {
                    // Il tasto Azione ad app chiusa: la richiesta è arrivata prima che
                    // qualcuno ascoltasse. Al giro successivo, quando la finestra c'è già e
                    // il foglio ha un controller sopra cui presentarsi.
                    DispatchQueue.main.async {
                        if CaptureRequests.consumePending() { router.openCapture() }
                    }
                }
        }
    }
}

/// Chi decide se il foglio è aperto.
///
/// Il foglio si presenta con UIKit **sopra qualunque cosa sia aperta** — lo smistamento, una
/// nota, il foglio di condivisione — e non con una presentazione di SwiftUI: SwiftUI ne
/// ammette una sola per livello, e una richiesta dal widget arrivata con un foglio di
/// condivisione aperto andrebbe persa. La cattura vince sempre (§1).
final class Router: ObservableObject {
    @Published private(set) var isCapturing = false
    private weak var capture: UIViewController?

    func openCapture() {
        guard capture == nil, let top = Self.topController() else { return }
        let controller = CaptureViewController(requestedAt: Date())
        controller.onDone = { [weak self] in self?.closeCapture() }
        controller.modalPresentationStyle = .fullScreen
        // Senza animazione: ogni animazione di apertura è tempo prima di poter scrivere (D19).
        top.present(controller, animated: false)
        capture = controller
        isCapturing = true
    }

    func closeCapture() {
        capture?.dismiss(animated: false)
        capture = nil
        isCapturing = false
    }

    private static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first { $0.isKeyWindow } ?? scenes.first?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController, !presented.isBeingDismissed { top = presented }
        return top
    }
}
