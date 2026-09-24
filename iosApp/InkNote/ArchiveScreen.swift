import InkNoteKit
import SwiftUI

/// L'archivio (D39, D46): bigliettini di carta su una scrivania, in una griglia che mette
/// tante colonne quante ne stanno. La ricerca è quella di sistema, in cima; il solo
/// pulsante pieno è "Scrivi".
struct ArchiveScreen: View {
    @EnvironmentObject private var router: Router
    @StateObject private var model = ArchiveModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Brand.desk.ignoresSafeArea()

                ScrollView {
                    if model.loaded && model.notes.isEmpty {
                        EmptyState(searching: !model.query.isEmpty)
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 158), spacing: 12)], spacing: 12) {
                            ForEach(model.notes) { item in
                                NoteCard(item: item)
                                    .contextMenu {
                                        Button(role: .destructive) { model.delete(item) } label: {
                                            Label("Delete", systemImage: "trash")
                                        }
                                    }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 4)
                        .padding(.bottom, 110)
                    }
                }
                .scrollDismissesKeyboard(.immediately)

                Button { router.openCapture() } label: {
                    Label("Write", systemImage: "pencil.tip")
                        .font(.body.weight(.semibold))
                        .padding(.horizontal, 22)
                        .padding(.vertical, 16)
                        .foregroundStyle(Color(uiColor: .systemBackground))
                        .background(Color.primary, in: Capsule())
                }
                .padding(.trailing, 20)
                .padding(.bottom, 12)
            }
            .navigationTitle("Notes")
            .searchable(text: $model.query, prompt: "Search")
        }
        .onAppear { model.refresh() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.refresh() } }
        .onChange(of: router.isCapturing) { _, capturing in if !capturing { model.refresh() } }
        .alert("The last stroke before the app closed could not be saved.", isPresented: $model.lostStroke) {
            Button("OK", role: .cancel) {}
        }
    }
}

/// Un bigliettino: l'inchiostro inquadrato, o il testo; sotto, una riga e la data.
private struct NoteCard: View {
    let item: NoteItem

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Group {
                if item.note.hasInk {
                    InkThumbnail(note: item.note)
                } else {
                    Text(item.caption ?? "")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Brand.ink)
                        .lineLimit(7)
                        .padding(16)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            }
            .frame(height: 150)

            VStack(alignment: .leading, spacing: 3) {
                if item.note.hasInk, let caption = item.caption {
                    Text(caption)
                        .font(.system(size: 13.5, weight: .medium))
                        .foregroundStyle(Brand.ink)
                        .lineLimit(1)
                }
                Text(item.date, format: .relative(presentation: .named))
                    .font(.caption)
                    .foregroundStyle(Brand.inkMuted)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 12)
            .padding(.top, 6)
        }
        .background(Brand.card)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Brand.outline, lineWidth: 1))
    }
}

/// L'inchiostro di una nota, inquadrato: si inquadra l'inchiostro, non il foglio.
struct InkThumbnail: View {
    let note: Note

    var body: some View {
        Canvas { context, size in
            let shapes = InkPreview.shared.shapes(note: note, width: Float(size.width), height: Float(size.height), padding: 12)
            for shape in shapes {
                context.fill(Path(InkCanvasView.path(of: shape)), with: .color(Color(uiColor: InkCanvasView.color(of: shape))))
            }
        }
    }
}

private struct EmptyState: View {
    let searching: Bool

    var body: some View {
        VStack(spacing: 10) {
            Scribble()
                .stroke(Color.secondary.opacity(0.6), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                .frame(width: 72, height: 72)
                .padding(.bottom, 6)
            Text(searching ? "Nothing found." : "Nothing here yet")
                .font(.title3.weight(.semibold))
            if !searching {
                Text("Tap Write, or add the widget to your Home Screen: an idea takes a second.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 40)
        .padding(.top, 90)
        .frame(maxWidth: .infinity)
    }
}
