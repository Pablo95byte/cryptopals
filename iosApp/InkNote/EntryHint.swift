import SwiftUI
import WidgetKit

/// "Metti il foglio sulla Home" (D76): il bigliettino che fa sapere che il widget esiste.
///
/// Un'abitudine nasce da un segnale, e il nostro è il widget: chi non lo mette usa Instink
/// come un'app di note qualunque, e la abbandona come le altre (D75). Il bigliettino compare
/// in cima all'archivio **solo finché il widget non c'è** — sulla Home o sulla schermata di
/// blocco, WidgetKit lo dice all'app. Non è un onboarding (D12): non blocca niente e non
/// chiede niente. "Non ora" lo nasconde per una settimana; dopo due volte, per sempre, perché
/// un consiglio ripetuto diventa un compito (D51).
@MainActor
final class EntryHint: ObservableObject {
    @Published private(set) var visible = false

    private static let widgetKind = "app.inknote.sheet"
    private static let dismissCountKey = "entryHint.dismissCount"
    private static let dismissedAtKey = "entryHint.dismissedAt"
    private static let pause: TimeInterval = 7 * 86_400

    func refresh() {
        // La versione con il completamento c'è da iOS 14; quella `async` solo da iOS 18.
        WidgetCenter.shared.getCurrentConfigurations { result in
            let kinds = (try? result.get())?.map(\.kind) ?? []
            Task { @MainActor in
                self.visible = !kinds.contains(Self.widgetKind) && self.allowedNow()
            }
        }
    }

    func dismiss() {
        let defaults = UserDefaults.standard
        defaults.set(defaults.integer(forKey: Self.dismissCountKey) + 1, forKey: Self.dismissCountKey)
        defaults.set(Date().timeIntervalSince1970, forKey: Self.dismissedAtKey)
        visible = false
    }

    private func allowedNow() -> Bool {
        let defaults = UserDefaults.standard
        guard defaults.integer(forKey: Self.dismissCountKey) < 2 else { return false }
        let last = defaults.double(forKey: Self.dismissedAtKey)
        return last == 0 || Date().timeIntervalSince1970 - last > Self.pause
    }
}

/// Il bigliettino: un piccolo foglio col ricciolo, com'è il widget, e i passi per metterlo.
struct EntryHintCard: View {
    let onClose: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            // Il widget in miniatura: carta e ricciolo tenue, come sulla Home (D30).
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Brand.paper)
                .overlay(
                    Scribble()
                        .stroke(Brand.scribbleTint, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                        .frame(width: 24, height: 24)
                )
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Brand.outline, lineWidth: 1))
                .frame(width: 58, height: 58)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 5) {
                Text("Put the sheet on your Home Screen")
                    .font(.system(size: 15.5, weight: .semibold))
                    .foregroundStyle(Brand.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text("One tap and you're writing. Touch and hold the Home Screen, tap Edit, then Add Widget, and search for Instink.")
                    .font(.system(size: 13.5))
                    .foregroundStyle(Brand.ink.opacity(0.78))
                    .fixedSize(horizontal: false, vertical: true)
                Text("It's also in Control Center and on the Action button.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Brand.inkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Brand.inkMuted)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Not now"))
        }
        .padding(14)
        .background(Brand.card)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Brand.outline, lineWidth: 1))
    }
}
