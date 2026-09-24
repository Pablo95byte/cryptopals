import InkNoteKit
import SwiftUI

/// Una nota aperta (D39, D46): l'inchiostro in grande, il testo, le registrazioni (D63), le
/// foto, e le due cose che
/// si fanno con una nota dopo averla scritta — mandarla dove si tengono le note (D31) o
/// buttarla. Il solo pulsante pieno è "Manda a…".
struct NoteScreen: View {
    let noteId: String
    let onChange: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var note: Note?
    @State private var photos: [UIImage] = []
    @State private var voices: [VoiceItem] = []
    @StateObject private var player = VoicePlayer()
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

                        ForEach(voices, id: \.id) { voice in
                            voiceRow(voice)
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
        .onDisappear { player.stop() }
    }

    /// Una registrazione: il tasto per riascoltarla, la durata, e ciò che è stato capito.
    /// Sul bigliettino, che resta carta anche di notte: colori della carta (D46).
    private func voiceRow(_ voice: VoiceItem) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Button { player.toggle(voice) } label: {
                Image(systemName: player.playingId == voice.id ? "pause.fill" : "play.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(width: 44, height: 44)
                    .foregroundStyle(Brand.ink)
                    .background(Brand.ink.opacity(0.08), in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(voice.path == nil)
            .accessibilityLabel(player.playingId == voice.id ? Text("Pause") : Text("Play voice note"))

            VStack(alignment: .leading, spacing: 4) {
                Text(voice.durationLabel)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Brand.inkMuted)
                if let transcript = voice.transcript, !transcript.isEmpty {
                    Text(transcript)
                        .font(.system(size: 17))
                        .foregroundStyle(Brand.ink)
                        .textSelection(.enabled)
                } else if voice.transcript == nil {
                    // Non un errore: la trascrizione arriva da sola, sul dispositivo.
                    Text("Transcript on its way")
                        .font(.callout)
                        .foregroundStyle(Brand.inkMuted)
                }
            }
            .padding(.top, 2)
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.card)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Brand.outline, lineWidth: 1))
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
        ArchiveBackend.shared.run({ archive -> (Note, [String], [VoiceItem], ReminderSuggestion?)? in
            guard let note = archive.note(id: noteId), !note.isDeleted else { return nil }
            let hint = archive.dateHint(note: note, nowMillis: now, utcOffsetMillis: offset)
            let caption = archive.captionOf(note: note)
            return (
                note,
                archive.photoPaths(note: note),
                archive.voiceItems(note: note),
                hint.map { ReminderSuggestion(hint: $0, caption: caption) }
            )
        }, then: { loaded in
            guard let loaded else { dismiss(); return }
            let (note, paths, voices, reminder) = loaded
            self.note = note
            self.voices = voices
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
