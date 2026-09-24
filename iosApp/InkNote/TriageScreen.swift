import InkNoteKit
import SwiftUI

/// Lo smistamento a carte (D51, D52): le note nuove una alla volta, a tutto schermo.
/// A destra "manda", a sinistra "tieni", in basso "butta".
///
/// "Cattura adesso, smista quando hai un minuto" (D31) diventa un gesto da dieci secondi.
/// Una nota esce dalla coda quando è stata tenuta, mandata o buttata; la coda la decide
/// l'archivio (`notesToSort`), non questa schermata.
struct TriageScreen: View {
    let onChange: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var queue: [NoteItem] = []
    @State private var loaded = false
    @State private var offset: CGSize = .zero
    @State private var share: SharePayload?

    private let threshold: CGFloat = 110

    var body: some View {
        NavigationStack {
            ZStack {
                Brand.desk.ignoresSafeArea()

                if let current = queue.first {
                    VStack(spacing: 22) {
                        card(for: current)
                            .offset(offset)
                            .rotationEffect(.degrees(Double(offset.width / 22)))
                            .gesture(drag(current))
                            .padding(.horizontal, 20)

                        actions(current)
                    }
                    .padding(.bottom, 12)
                } else if loaded {
                    VStack(spacing: 10) {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 44, weight: .light))
                            .foregroundStyle(.secondary)
                        Text("All sorted")
                            .font(.title3.weight(.semibold))
                        Text("New notes will wait here until you have a minute.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(40)
                }
            }
            .navigationTitle(queue.isEmpty ? "" : String(localized: "\(queue.count) to sort"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .sheet(item: $share) { payload in
            ShareSheet(items: payload.items) { sent in
                share = nil
                if sent {
                    SharePayload.markSent(payload.id, then: onChange)
                    advance()
                } else {
                    // Non mandata: la carta torna al centro e resta in coda.
                    withAnimation(.spring) { offset = .zero }
                }
            }
            .presentationDetents([.medium, .large])
        }
        .onAppear(perform: load)
    }

    private func card(for item: NoteItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if item.note.hasInk {
                InkThumbnail(note: item.note, padding: 20)
            } else {
                Text(item.caption ?? "")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(Brand.ink)
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
            HStack {
                Text(item.date, format: .relative(presentation: .named))
                Spacer()
                if item.note.hasInk, let caption = item.caption {
                    Text(caption).lineLimit(1)
                }
            }
            .font(.footnote)
            .foregroundStyle(Brand.inkMuted)
            .padding(18)
        }
        .frame(maxWidth: 520)
        .frame(maxHeight: 520)
        .background(Brand.card)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(Brand.outline, lineWidth: 1))
        .overlay(alignment: .top) { hint }
    }

    /// Mentre si trascina, la parola dice cosa succederà lasciando andare.
    @ViewBuilder private var hint: some View {
        let label: String? = offset.height > threshold ? String(localized: "Delete")
            : offset.width > threshold ? String(localized: "Send")
            : offset.width < -threshold ? String(localized: "Keep")
            : nil
        if let label {
            Text(label)
                .font(.headline)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(.thinMaterial, in: Capsule())
                .padding(.top, 16)
        }
    }

    /// Gli stessi tre gesti come pulsanti: non tutti trascinano, e VoiceOver non trascina.
    private func actions(_ item: NoteItem) -> some View {
        HStack(spacing: 28) {
            roundButton("trash", label: "Delete") { delete(item) }
            roundButton("tray.and.arrow.down", label: "Keep") { keep(item) }
            roundButton("square.and.arrow.up", label: "Send", primary: true) { send(item) }
        }
    }

    private func roundButton(_ symbol: String, label: LocalizedStringKey, primary: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .semibold))
                .frame(width: 60, height: 60)
                .foregroundStyle(primary ? Color(uiColor: .systemBackground) : Color.primary)
                .background(primary ? Color.primary : Color.primary.opacity(0.07), in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
    }

    private func drag(_ item: NoteItem) -> some Gesture {
        DragGesture()
            .onChanged { offset = $0.translation }
            .onEnded { value in
                let t = value.translation
                if t.height > threshold && abs(t.height) > abs(t.width) {
                    delete(item)
                } else if t.width > threshold {
                    send(item)
                } else if t.width < -threshold {
                    keep(item)
                } else {
                    withAnimation(.spring) { offset = .zero }
                }
            }
    }

    private func keep(_ item: NoteItem) {
        let now = Now.millis
        ArchiveBackend.shared.run({ archive in archive.keep(id: item.id, nowMillis: now) }, then: { _ in onChange() })
        advance()
    }

    private func delete(_ item: NoteItem) {
        let now = Now.millis
        ArchiveBackend.shared.run({ archive in archive.delete(id: item.id, nowMillis: now) }, then: { _ in onChange() })
        advance()
    }

    private func send(_ item: NoteItem) {
        SharePayload.prepare(noteId: item.id) { payload in
            if let payload {
                share = payload
            } else {
                withAnimation(.spring) { offset = .zero }
            }
        }
    }

    private func advance() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            offset = .zero
            if !queue.isEmpty { queue.removeFirst() }
        }
    }

    private func load() {
        ArchiveBackend.shared.run({ archive in
            archive.toSort(limit: 100).map { NoteItem(archive: archive, note: $0) }
        }, then: { items in
            queue = items
            loaded = true
        })
    }
}
