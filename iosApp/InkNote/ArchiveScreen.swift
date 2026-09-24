import InkNoteKit
import StoreKit
import SwiftUI

/// L'archivio (D39, D46): bigliettini di carta su una scrivania, in una griglia che mette
/// tante colonne quante ne stanno. La ricerca è quella di sistema, in cima; il solo
/// pulsante pieno è "Scrivi".
struct ArchiveScreen: View {
    @EnvironmentObject private var router: Router
    @StateObject private var model = ArchiveModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var sorting = false
    @State private var share: SharePayload?
    @Environment(\.requestReview) private var requestReview

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Brand.desk.ignoresSafeArea()

                ScrollView {
                    if model.loaded && model.notes.isEmpty {
                        EmptyState(searching: !model.query.isEmpty)
                    } else {
                        if let resurfaced = model.resurfaced {
                            ResurfacedCard(resurfaced: resurfaced) { model.dismissResurfaced() }
                                .padding(.horizontal, 16)
                                .padding(.bottom, 12)
                        }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 158), spacing: 12)], spacing: 12) {
                            ForEach(model.notes) { item in
                                NavigationLink(value: item.id) { NoteCard(item: item) }
                                    .buttonStyle(.plain)
                                    .contextMenu {
                                        // Tenendo premuto: mandare senza aprire la nota (D59).
                                        Button {
                                            SharePayload.prepare(noteId: item.id) { payload in share = payload }
                                        } label: {
                                            Label("Send to…", systemImage: "square.and.arrow.up")
                                        }
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
            .navigationDestination(for: String.self) { id in
                NoteScreen(noteId: id) { model.reload() }
            }
            .toolbar {
                // Lo smistamento compare solo quando c'è qualcosa da smistare: un pulsante
                // con uno zero sarebbe un compito, e non ne vogliamo dare (D51).
                if model.toSortCount > 0 {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { sorting = true } label: {
                            // La stessa goccia dei bigliettini: il pulsante dice dove vanno (D72).
                            HStack(spacing: 7) {
                                Circle().fill(Brand.spark).frame(width: 8, height: 8)
                                Text("Sort \(model.toSortCount)")
                            }
                            .font(.subheadline.weight(.semibold))
                        }
                    }
                }
            }
        }
        .fullScreenCover(isPresented: $sorting) {
            TriageScreen { model.reload() }
        }
        .sheet(item: $share) { payload in
            // L'invio si registra solo se l'utente sceglie una destinazione (invariante 18).
            ShareSheet(items: payload.items) { sent in
                share = nil
                if sent { SharePayload.markSent(payload.id) { model.reload() } }
            }
            .presentationDetents([.medium, .large])
        }
        .onAppear { model.refresh() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.refresh() } }
        .onChange(of: router.isCapturing) { _, capturing in if !capturing { model.refresh() } }
        .onChange(of: model.notes.count) { _, count in askForReviewIfDeserved(noteCount: count) }
        .alert("The last stroke before the app closed could not be saved.", isPresented: $model.lostStroke) {
            Button("OK", role: .cancel) {}
        }
    }

    /// La recensione si chiede una volta per versione, e solo a chi l'app la usa davvero:
    /// dopo la settima nota, tornando all'archivio da un foglio appena chiuso (D68). Le
    /// stelle decidono il posto nella ricerca dello store più di qualunque testo, e chi ha
    /// già scritto sette note è la persona giusta a cui chiederle. Mai sul foglio.
    private func askForReviewIfDeserved(noteCount: Int) {
        let key = "review.askedForVersion"
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        guard noteCount >= 7, model.query.isEmpty, !router.isCapturing, UserDefaults.standard.string(forKey: key) != version else { return }
        UserDefaults.standard.set(version, forKey: key)
        requestReview()
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
                } else if let caption = item.caption {
                    Text(caption)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Brand.ink)
                        .lineLimit(7)
                        .padding(16)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                } else {
                    // Una registrazione non ancora trascritta, o solo una foto: un segno
                    // tenue invece di un bigliettino vuoto che sembra rotto.
                    Image(systemName: item.note.hasVoice ? "waveform" : "photo")
                        .font(.system(size: 28, weight: .light))
                        .foregroundStyle(Brand.inkMuted)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
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
        .overlay(alignment: .topTrailing) {
            // La goccia dell'icona, con un significato solo: da smistare (D72). Sparisce
            // quando la nota è tenuta o mandata fuori, e dice le stesse note della coda.
            if item.note.awaitsSorting {
                Circle()
                    .fill(Brand.spark)
                    .frame(width: 9, height: 9)
                    .padding(13)
                    .accessibilityLabel(Text("To sort"))
            }
        }
    }
}

/// La riemersione (D51, D52): una nota vecchia al giorno, in cima, senza notifiche. Si
/// toglie per oggi con la crocetta; domani ne arriva un'altra.
private struct ResurfacedCard: View {
    let resurfaced: ResurfacedItem
    let onDismiss: () -> Void

    var body: some View {
        NavigationLink(value: resurfaced.item.id) {
            HStack(spacing: 14) {
                Group {
                    if resurfaced.item.note.hasInk {
                        InkThumbnail(note: resurfaced.item.note, padding: 6)
                    } else {
                        Text(resurfaced.item.caption ?? "")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Brand.ink)
                            .lineLimit(4)
                            .padding(6)
                    }
                }
                .frame(width: 72, height: 72)
                .background(Brand.paper)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(resurfaced.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.ink)
                    Text("You wrote this. Still a good idea?")
                        .font(.footnote)
                        .foregroundStyle(Brand.inkMuted)
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .padding(.trailing, 30)
            .background(Brand.card)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Brand.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .overlay(alignment: .topTrailing) {
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Brand.inkMuted)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Not today")
        }
    }
}

/// L'inchiostro di una nota, inquadrato: si inquadra l'inchiostro, non il foglio.
struct InkThumbnail: View {
    let note: Note
    var padding: Float = 12

    var body: some View {
        Canvas { context, size in
            let shapes = InkPreview.shared.shapes(note: note, width: Float(size.width), height: Float(size.height), padding: padding)
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
            Text(searching ? LocalizedStringKey("Nothing found.") : LocalizedStringKey("Nothing here yet"))
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
