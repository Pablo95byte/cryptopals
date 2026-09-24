import AppIntents
import SwiftUI
import WidgetKit

/// I widget e il Controllo (D30, §2): un foglio bianco, e basta.
///
/// Non leggono l'archivio e non sanno che esistono delle note: dalla home e dalla
/// schermata di blocco si aggiunge, non si rilegge (invariante 11). Non si aggiornano mai.
@main
struct InkNoteWidgets: WidgetBundle {
    var body: some Widget {
        SheetWidget()
        if #available(iOSApplicationExtension 18.0, *) {
            WriteControl()
        }
    }
}

struct SheetEntry: TimelineEntry {
    let date: Date
}

struct SheetProvider: TimelineProvider {
    func placeholder(in context: Context) -> SheetEntry { SheetEntry(date: .now) }

    func getSnapshot(in context: Context, completion: @escaping (SheetEntry) -> Void) {
        completion(SheetEntry(date: .now))
    }

    /// Un solo momento, per sempre: un foglio bianco non ha niente da aggiornare.
    func getTimeline(in context: Context, completion: @escaping (Timeline<SheetEntry>) -> Void) {
        completion(Timeline(entries: [SheetEntry(date: .now)], policy: .never))
    }
}

/// Il foglio sulla home (D30), e la sua versione per la schermata di blocco.
struct SheetWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "app.inknote.sheet", provider: SheetProvider()) { _ in
            SheetWidgetView()
        }
        .configurationDisplayName("Blank sheet")
        .description("Tap anywhere and write.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryCircular, .accessoryRectangular])
        .contentMarginsDisabled()
    }
}

struct SheetWidgetView: View {
    @Environment(\.widgetFamily) private var family

    private var isAccessory: Bool {
        family == .accessoryCircular || family == .accessoryRectangular
    }

    var body: some View {
        content
            // Tutto il riquadro è il bersaglio: nessun punto da centrare col pollice.
            .widgetURL(Brand.captureURL)
            .containerBackground(for: .widget) {
                if isAccessory { Color.clear } else { Brand.paper }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "scribble.variable").font(.title2)
            }
        case .accessoryRectangular:
            HStack(spacing: 8) {
                Image(systemName: "scribble.variable").font(.title3)
                Text("Write").font(.headline)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        default:
            // Il segno tenue (D30, D33): dice "qui si scrive" senza dire altro.
            Scribble()
                .stroke(Brand.scribbleTint, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                .frame(width: 38, height: 38)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

/// Il pulsante nel Centro di Controllo, da iOS 18: il gesto più vicino alla cattura sopra
/// il blocco che iPhone permette (D17). Scorri, tocca, sblocca col viso, scrivi.
@available(iOSApplicationExtension 18.0, *)
struct WriteControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "app.inknote.ios.write") {
            ControlWidgetButton(action: WriteNoteIntent()) {
                Label("Write", systemImage: "scribble.variable")
            }
        }
        .displayName("Write a note")
        .description("Opens a blank sheet.")
    }
}
