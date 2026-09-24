import InkNoteKit
import SwiftUI

/// Una nota aperta (D39, D46): l'inchiostro in grande, il testo, le foto, e le due cose che
/// si fanno con una nota dopo averla scritta — mandarla dove si tengono le note (D31) o
/// buttarla. Il solo pulsante pieno è "Manda a…".
struct NoteScreen: View {
    let noteId: String
    let onChange: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var note: Note?
    @State private var photos: [UIImage] = []
    @State private var reminder: ReminderSuggestion?
    @State private var editingReminder: ReminderSuggestion?
    @State private var share: SharePayload?
    @State private var confirmDelete = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Brand.desk.ignoresSafeArea()

            ScrollView {
                if let note {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(Date(timeIntervalSince1970: TimeInterval(note.createdAt) / 1000), format: .dateTime.weekday(.wide).day().month(.wide).hour().minute())
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .padding(.leading, 4)

                        if note.hasInk {
                            InkThumbnail(note: note, padding: 16)
                                .frame(height: inkHeight(note, width: columnWidth))
                                .background(Brand.card)
                                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Brand.outline, lineWidth: 1))
                        }

                        if let text = note.typedText {
                            Text(text)
                                .font(.system(size: 18))
                                .foregroundStyle(Brand.ink)
                                .textSelection(.enabled)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 18)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Brand.card)
                                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Brand.outline, lineWidth: 1))
                        }

                        ForEach(Array(photos.enumerated()), id: \.offset) { _, photo in
                            Image(uiImage: photo)
                                .resizable()
                                .scaledToFit()
                                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                        }

                        if let reminder {
                            Button { editingReminder = reminder } label: {
                                Label("Remind me · \(reminder.label)", systemImage: "bell")
                                    .font(.callout.weight(.semibold))
                                    .padding(.horizontal, 18)
                                    .padding(.vertical, 12)
                                    .background(Color.primary.opacity(0.07), in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 120)
                    .frame(maxWidth: 680)
                    .frame(maxWidth: .infinity)
                }
            }

            if note != nil {
                Button { openShare() } label: {
                    Label("Send to…", systemImage: "square.and.arrow.up")
                        .font(.body.weight(.semibold))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 16)
                        .foregroundStyle(Color(uiColor: .systemBackground))
                        .background(Color.primary, in: Capsule())
                }
                .padding(.bottom, 12)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) { confirmDelete = true } label: { Image(systemName: "trash") }
                    .accessibilityLabel("Delete")
            }
        }
        .confirmationDialog("Delete this note?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { delete() }
        }
        .sheet(item: $share) { payload in
            ShareSheet(items: payload.items) { sent in
                share = nil
                if sent { SharePayload.markSent(payload.id, then: onChange) }
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $editingReminder) { suggestion in
            ReminderEditor(suggestion: suggestion) { editingReminder = nil }
                .ignoresSafeArea()
        }
        .onAppear(perform: load)
    }

    /// Larghezza della colonna: tutto lo schermo sul telefono, al massimo 680 punti altrove.
    private var columnWidth: CGFloat {
        min(UIScreen.main.bounds.width, 680) - 40
    }

    private func inkHeight(_ note: Note, width: CGFloat) -> CGFloat {
        CGFloat(InkPreview.shared.heightFor(note: note, width: Float(width), min: 180, max: 560))
    }

    private func load() {
        let now = Now.millis
        let offset = Now.utcOffsetMillis
        ArchiveBackend.shared.run({ archive -> (Note, [String], ReminderSuggestion?)? in
            guard let note = archive.note(id: noteId), !note.isDeleted else { return nil }
            let hint = archive.dateHint(note: note, nowMillis: now, utcOffsetMillis: offset)
            return (note, archive.photoPaths(note: note), hint.map { ReminderSuggestion(hint: $0, note: note) })
        }, then: { loaded in
            guard let loaded else { dismiss(); return }
            let (note, paths, reminder) = loaded
            self.note = note
            self.reminder = reminder
            DispatchQueue.global(qos: .userInitiated).async {
                let images = paths.compactMap { PhotoFiles.load($0)?.scaledToFit(maxSide: 1600) }
                DispatchQueue.main.async { photos = images }
            }
        })
    }

    private func openShare() {
        SharePayload.prepare(noteId: noteId) { payload in share = payload }
    }

    private func delete() {
        // Un tombstone, non una cancellazione: la nota resta trenta giorni (D8, D26).
        let now = Now.millis
        ArchiveBackend.shared.run({ archive in archive.delete(id: noteId, nowMillis: now) }, then: { _ in
            onChange()
            dismiss()
        })
    }
}
