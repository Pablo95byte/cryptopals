# Regole di R8 per :androidApp (D37).
#
# Quasi vuoto di proposito: niente riflessione, niente serializzazione per nome, nessuna
# libreria. Activity, servizio e widget sono citati nel manifest e R8 li tiene da solo.
#
# L'unico nome che finisce su disco è quello di PenKind, nel giornale: `Enum.name`
# restituisce la stringa scritta nel sorgente anche dopo l'offuscamento, quindi il
# giornale resta leggibile fra una versione e l'altra. Se un giorno qualcosa venisse
# letto per nome di classe o di campo, la regola va aggiunta qui, con il perché.

# Stack trace leggibili nei rapporti di crash, al costo di qualche byte.
-keepattributes SourceFile,LineNumberTable
