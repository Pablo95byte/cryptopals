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
        // Si detta senza sbloccare (D63): la sola cattura sopra il blocco su iPhone.
        AppShortcut(
            intent: AddNoteIntent(),
            phrases: [
                "Add a note to \(.applicationName)",
                "Add an \(.applicationName) note",
                "Take a note in \(.applicationName)",
            ],
            shortTitle: "Dictate",
            systemImageName: "mic"
        )
    }
}
