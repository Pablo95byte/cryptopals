import AppIntents

/// Le scorciatoie che compaiono da sole in Comandi rapidi e nella scelta del tasto Azione:
/// l'utente non deve costruire niente.
struct InkNoteShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: WriteNoteIntent(),
            phrases: [
                "Write a note in \(.applicationName)",
                "New \(.applicationName) note",
            ],
            shortTitle: "Write",
            systemImageName: "scribble.variable"
        )
    }
}
