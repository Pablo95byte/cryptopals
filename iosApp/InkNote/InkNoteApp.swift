import SwiftUI

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
                .fullScreenCover(isPresented: Binding(get: { router.isCapturing }, set: { if !$0 { router.closeCapture() } })) {
                    CaptureScreen(requestedAt: router.requestedAt) { router.closeCapture() }
                        .ignoresSafeArea()
                }
                .onOpenURL { url in
                    if url.scheme == "inknote", url.host == "capture" { router.openCapture() }
                }
                .onReceive(NotificationCenter.default.publisher(for: CaptureRequests.name)) { _ in
                    if CaptureRequests.consumePending() { router.openCapture() }
                }
                .onAppear {
                    // Il tasto Azione ad app chiusa: la richiesta è arrivata prima che
                    // qualcuno ascoltasse.
                    if CaptureRequests.consumePending() { router.openCapture() }
                }
        }
    }
}

/// Chi decide se il foglio è aperto.
final class Router: ObservableObject {
    @Published private(set) var isCapturing = false
    private(set) var requestedAt = Date()

    func openCapture() {
        requestedAt = Date()
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { isCapturing = true }
    }

    func closeCapture() {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { isCapturing = false }
    }
}
