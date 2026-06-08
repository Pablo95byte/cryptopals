#!/usr/bin/env python3
"""
Shannon Information Theory - Visualizzazione interattiva
Capire cosa è il logaritmo e come si collega alla probabilità
"""

import math
import os

# Colori ANSI per il terminale
CYAN   = "\033[96m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
MAGENTA= "\033[95m"
WHITE  = "\033[97m"
GRAY   = "\033[90m"
RESET  = "\033[0m"
BOLD   = "\033[1m"

def clear():
    os.system("clear")

def info(p: float) -> float:
    """Informazione di Shannon: -log2(p)"""
    if p <= 0 or p > 1:
        return float("inf")
    return -math.log2(p)

def entropy(probs: list[float]) -> float:
    """Entropia di Shannon: H = -sum(p * log2(p))"""
    return sum(-p * math.log2(p) for p in probs if p > 0)

# ─────────────────────────────────────────────────────────────
# SEZIONE 1 — Cosa è un logaritmo
# ─────────────────────────────────────────────────────────────

def spiegazione_logaritmo():
    clear()
    print(f"{BOLD}{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}")
    print(f"{BOLD}{WHITE}  1. COS'È UN LOGARITMO?{RESET}")
    print(f"{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}\n")

    print(f"{WHITE}Il logaritmo risponde a questa domanda:{RESET}")
    print(f"{YELLOW}  «A quale potenza devo elevare la base per ottenere questo numero?»{RESET}\n")

    print(f"{GRAY}  Esempio base 2 (quella usata in informatica):{RESET}")
    examples = [
        (1,   "2^0 = 1  → ho bisogno di 0 bit"),
        (2,   "2^1 = 2  → ho bisogno di 1 bit"),
        (4,   "2^2 = 4  → ho bisogno di 2 bit"),
        (8,   "2^3 = 8  → ho bisogno di 3 bit"),
        (16,  "2^4 = 16 → ho bisogno di 4 bit"),
        (32,  "2^5 = 32 → ho bisogno di 5 bit"),
    ]
    for n, desc in examples:
        log_val = math.log2(n)
        bar = "█" * int(log_val * 4)
        print(f"  log₂({n:>2}) = {log_val:.0f}  {GREEN}{bar}{RESET}  {GRAY}({desc}){RESET}")

    print(f"\n{WHITE}Intuizione:{RESET}")
    print(f"  log₂(n) conta {YELLOW}quante volte devi dividere n per 2{RESET} prima di arrivare a 1.")
    print(f"  È esattamente il numero di bit necessari per rappresentare n possibilità.\n")

    input(f"{GRAY}[Premi INVIO per continuare...]{RESET}")

# ─────────────────────────────────────────────────────────────
# SEZIONE 2 — Il grafico Information = -log2(p)
# ─────────────────────────────────────────────────────────────

def grafico_informazione():
    clear()
    print(f"{BOLD}{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}")
    print(f"{BOLD}{WHITE}  2. INFORMAZIONE = -log₂(p){RESET}")
    print(f"{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}\n")

    print(f"{WHITE}Perché il MENO davanti?{RESET}")
    print(f"  • La probabilità p è tra 0 e 1")
    print(f"  • log₂(0.1 to 1) è {YELLOW}negativo o zero{RESET}")
    print(f"  • Il meno lo rende {GREEN}positivo{RESET}: più raro = più informazione\n")

    # Grafico ASCII
    width  = 50
    height = 10
    max_bits = 6.0

    print(f"{CYAN}  bits{RESET}")
    for row in range(height, -1, -1):
        bit_val = (row / height) * max_bits
        label = f"{bit_val:.0f}" if row % 2 == 0 else " "
        line = f"  {label:>3} │"
        for col in range(1, width + 1):
            p = col / width
            i_val = info(p)
            # normalizza tra 0 e max_bits
            norm = min(i_val / max_bits, 1.0)
            plot_row = norm * height
            if abs(plot_row - row) < 0.6:
                # evidenzia p=0.25 (2 bit)
                if abs(p - 0.25) < 0.03:
                    line += f"{YELLOW}●{RESET}"
                else:
                    line += f"{CYAN}·{RESET}"
            else:
                line += " "
        print(line)

    # Asse X
    print(f"      └" + "─" * width + f"▶  {WHITE}p{RESET}")
    ticks = "       0"
    for i in range(1, 11):
        pos = int(i * width / 10)
        ticks += " " * (pos - len(ticks) + 8) + f"{i/10:.1f}"
    print(f"  {GRAY}{ticks}{RESET}\n")

    # Tabella valori
    print(f"  {WHITE}{'Probabilità p':>15}  {'Informazione -log₂(p)':>22}  {'Esempio pratico'}{RESET}")
    print(f"  {GRAY}{'─'*15}  {'─'*22}  {'─'*30}{RESET}")

    cases = [
        (1.0,    "Evento certo (es. il sole sorge)"),
        (0.5,    "Moneta (testa o croce)"),
        (0.25,   "← punto nel grafico: 2 bit"),
        (0.125,  "1 su 8 (lancio dado binario)"),
        (1/52,   "Estrarre l'asso di picche"),
        (0.001,  "Evento molto raro"),
    ]
    for p, desc in cases:
        i_val = info(p)
        bar = "▓" * min(int(i_val), 20)
        highlight = YELLOW if abs(p - 0.25) < 0.01 else WHITE
        print(f"  {highlight}p = {p:>6.4f}{RESET}  →  {GREEN}{i_val:>5.2f} bit{RESET}  {CYAN}{bar}{RESET}  {GRAY}{desc}{RESET}")

    print(f"\n{WHITE}Regola chiave:{RESET}")
    print(f"  {YELLOW}Evento raro{RESET}  (p piccolo)  →  {GREEN}molta informazione{RESET} (tanti bit)")
    print(f"  {YELLOW}Evento certo{RESET} (p = 1)     →  {GREEN}zero informazione{RESET}  (0 bit)\n")

    input(f"{GRAY}[Premi INVIO per continuare...]{RESET}")

# ─────────────────────────────────────────────────────────────
# SEZIONE 3 — Entropia: media dell'informazione
# ─────────────────────────────────────────────────────────────

def demo_entropia():
    clear()
    print(f"{BOLD}{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}")
    print(f"{BOLD}{WHITE}  3. ENTROPIA H(X) — la media dell'informazione{RESET}")
    print(f"{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}\n")

    print(f"  {WHITE}H(X) = -Σ p(x) · log₂(p(x)){RESET}\n")

    scenarios = {
        "Moneta equa (0.5 / 0.5)":              [0.5, 0.5],
        "Moneta truccata (0.9 / 0.1)":           [0.9, 0.1],
        "Dado a 6 facce (uniforme)":             [1/6]*6,
        "Dado truccato (faccia 6 = 50%)":        [0.1, 0.1, 0.1, 0.1, 0.1, 0.5],
        "Risultato quasi certo (0.99 / 0.01)":   [0.99, 0.01],
    }

    max_h = 1.0  # massima entropia per moneta equa = 1 bit
    for label, probs in scenarios.items():
        h = entropy(probs)
        # normalizza rispetto all'entropia massima possibile
        h_max = math.log2(len(probs))
        norm = h / h_max if h_max > 0 else 0
        bar_len = int(norm * 30)
        bar = "█" * bar_len + "░" * (30 - bar_len)
        color = GREEN if norm > 0.8 else (YELLOW if norm > 0.4 else MAGENTA)
        print(f"  {WHITE}{label}{RESET}")
        print(f"    H = {color}{h:.3f} bit{RESET}  {color}{bar}{RESET}  ({norm*100:.0f}% del massimo)\n")

    print(f"{WHITE}Cosa significa?{RESET}")
    print(f"  • {GREEN}Alta entropia{RESET} = distribuzione uniforme = massima incertezza = più bit necessari")
    print(f"  • {MAGENTA}Bassa entropia{RESET} = un esito dominante = poca sorpresa = meno bit necessari\n")

    input(f"{GRAY}[Premi INVIO per continuare...]{RESET}")

# ─────────────────────────────────────────────────────────────
# SEZIONE 4 — Calcolatore interattivo
# ─────────────────────────────────────────────────────────────

def calcolatore():
    while True:
        clear()
        print(f"{BOLD}{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}")
        print(f"{BOLD}{WHITE}  4. CALCOLATORE INTERATTIVO{RESET}")
        print(f"{CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{RESET}\n")
        print(f"  Inserisci una probabilità tra 0 e 1 (o 'q' per uscire)\n")

        raw = input(f"  {YELLOW}p = {RESET}").strip()
        if raw.lower() == "q":
            break
        try:
            # supporta frazioni tipo "1/6"
            p = eval(raw, {"__builtins__": {}})
            p = float(p)
            if not (0 < p <= 1):
                raise ValueError
        except Exception:
            print(f"  {MAGENTA}Valore non valido. Usa un numero come 0.25 o una frazione come 1/6{RESET}")
            input(f"  {GRAY}[Premi INVIO]{RESET}")
            continue

        i_val = info(p)
        print(f"\n  {WHITE}Risultati per p = {p:.6f}{RESET}")
        print(f"  ┌──────────────────────────────────────────┐")
        print(f"  │  -log₂({p:.4f})  =  {GREEN}{i_val:.4f} bit{RESET}")
        print(f"  │  Probabilità complementare:  {1-p:.4f}")
        if p <= 0.5:
            odds = int(round(1/p))
            print(f"  │  Circa 1 su {odds}")
        bar = "█" * min(int(i_val * 3), 40)
        print(f"  │  {CYAN}{bar}{RESET}")
        print(f"  └──────────────────────────────────────────┘\n")

        # analogia intuitiva
        if i_val < 0.01:
            print(f"  {GRAY}→ Evento quasi certo, nessuna sorpresa.{RESET}")
        elif i_val < 1.1:
            print(f"  {YELLOW}→ Come il lancio di una moneta (~1 bit).{RESET}")
        elif i_val < 2.1:
            print(f"  {YELLOW}→ Serve circa {i_val:.1f} bit: 1 su {int(round(2**i_val))} esiti equiprobabili.{RESET}")
        else:
            print(f"  {GREEN}→ Evento raro! Porta {i_val:.1f} bit di informazione.{RESET}")

        input(f"\n  {GRAY}[Premi INVIO per un altro calcolo]{RESET}")

# ─────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────

def main():
    clear()
    print(f"\n{BOLD}{CYAN}{'═'*55}{RESET}")
    print(f"{BOLD}{WHITE}   TEORIA DELL'INFORMAZIONE DI SHANNON{RESET}")
    print(f"{BOLD}{CYAN}   Information = -log₂(p){RESET}")
    print(f"{CYAN}{'═'*55}{RESET}\n")
    print(f"  Questo programma spiega il collegamento tra")
    print(f"  {YELLOW}logaritmo{RESET}, {GREEN}probabilità{RESET} e {CYAN}informazione{RESET}.\n")
    input(f"  {GRAY}[Premi INVIO per iniziare]{RESET}")

    spiegazione_logaritmo()
    grafico_informazione()
    demo_entropia()
    calcolatore()

    clear()
    print(f"\n{BOLD}{CYAN}{'═'*55}{RESET}")
    print(f"{BOLD}{WHITE}   RIEPILOGO{RESET}")
    print(f"{CYAN}{'═'*55}{RESET}\n")
    print(f"  {YELLOW}log₂(n){RESET}   = quanti bit servono per n possibilità")
    print(f"  {GREEN}-log₂(p){RESET}  = quanta informazione porta un evento con prob. p")
    print(f"  {CYAN}H(X){RESET}      = media pesata: quanto è incerta la sorgente\n")
    print(f"  {GRAY}Più un evento è raro → più ci sorprende → più informazione porta.{RESET}\n")

if __name__ == "__main__":
    main()
