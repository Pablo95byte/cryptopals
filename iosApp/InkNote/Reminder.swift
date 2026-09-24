import EventKit
import EventKitUI
import InkNoteKit
import SwiftUI

/// Una data trovata in una nota, proposta come promemoria (D52).
///
/// "Dentista domani alle 10" scritto sul foglio diventa, **nell'archivio**, un suggerimento:
/// mai sul foglio, dove sarebbe una decisione chiesta mentre si scrive (D21).
struct ReminderSuggestion: Identifiable {
    let id = UUID()
    let date: Date
    let hasTime: Bool
    let title: String

    init(hint: DateHint, note: Note) {
        date = Date(timeIntervalSince1970: TimeInterval(hint.atMillis) / 1000)
        hasTime = hint.hasTime
        // Il titolo è la prima riga del testo: la nota stessa, non un nome inventato.
        let text = note.typedText ?? note.recognizedText ?? ""
        let firstLine = text.split(whereSeparator: \.isNewline).first.map(String.init) ?? ""
        title = firstLine.isEmpty ? "InkNote" : String(firstLine.prefix(80))
    }

    var label: String {
        let style: Date.FormatStyle = hasTime
            ? .dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute()
            : .dateTime.weekday(.abbreviated).day().month(.abbreviated)
        return date.formatted(style)
    }
}

/// L'editor di eventi del sistema, già compilato.
///
/// Da iOS 17 un'app che **aggiunge** eventi con questo editor non chiede nessun permesso
/// sul calendario: l'utente vede l'evento e decide lui se salvarlo. Non leggiamo niente
/// del suo calendario, coerentemente con D12.
struct ReminderEditor: UIViewControllerRepresentable {
    let suggestion: ReminderSuggestion
    let onFinish: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let store = EKEventStore()
        let event = EKEvent(eventStore: store)
        event.title = suggestion.title
        event.startDate = suggestion.date
        event.endDate = suggestion.date.addingTimeInterval(suggestion.hasTime ? 30 * 60 : 60 * 60)
        event.isAllDay = !suggestion.hasTime
        event.notes = String(localized: "From a note written in InkNote.")
        // L'avviso all'ora scritta: è la ragione per cui la data è stata annotata.
        event.addAlarm(EKAlarm(relativeOffset: 0))

        let controller = EKEventEditViewController()
        controller.eventStore = store
        controller.event = event
        controller.editViewDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: EKEventEditViewController, context: Context) {}

    final class Coordinator: NSObject, EKEventEditViewDelegate {
        let onFinish: () -> Void
        init(onFinish: @escaping () -> Void) { self.onFinish = onFinish }

        func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
            onFinish()
        }
    }
}
