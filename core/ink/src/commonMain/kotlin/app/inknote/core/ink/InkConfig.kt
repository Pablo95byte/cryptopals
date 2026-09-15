package app.inknote.core.ink

/**
 * Parametri del motore d'inchiostro.
 *
 * Sta nel core condiviso, e non in ciascuna app, perché è la ragione per cui
 * abbiamo scelto Kotlin Multiplatform: se questi numeri vivessero due volte, il
 * tratto su iPhone e quello su Android non sarebbero la stessa calligrafia.
 *
 * @param minPointDistance distanza minima, in unità di canvas, fra due campioni
 *   memorizzati. Serve a scartare il rumore del digitizer quando la penna è quasi
 *   ferma, che altrimenti produce grumi di inchiostro.
 * @param maxDwellIntervalMs oltre questo intervallo un campione viene tenuto anche
 *   se non si è mosso: la penna ferma sul foglio è un punto voluto, non rumore.
 * @param simplifyTolerance tolleranza della semplificazione a salvataggio: quanto
 *   può scostarsi la spezzata salvata da quella originale.
 * @param resampleSpacing passo con cui la curva viene ricampionata al disegno.
 * @param pressureGamma curva della pressione: sotto 1 rende i tratti leggeri più
 *   visibili, che è come si comporta una biro vera.
 * @param minWidthFactor frazione minima di [app.inknote.core.model.Pen.baseWidth]:
 *   un tratto non arriva mai a spessore zero, o sparisce.
 * @param velocityStrength quanto la velocità assottiglia il tratto quando la
 *   pressione non è disponibile (dito su schermo).
 * @param velocityReference velocità, in unità di canvas al millisecondo, a cui
 *   l'assottigliamento da velocità è massimo.
 * @param taperSamples quanti campioni in entrata e in uscita vengono affilati.
 */
data class InkConfig(
    val minPointDistance: Float = 0.8f,
    val maxDwellIntervalMs: Int = 40,
    val simplifyTolerance: Float = 0.3f,
    val resampleSpacing: Float = 1.5f,
    val pressureGamma: Float = 0.85f,
    val minWidthFactor: Float = 0.32f,
    val velocityStrength: Float = 0.55f,
    val velocityReference: Float = 1.2f,
    val taperSamples: Int = 5,
) {
    init {
        require(minPointDistance >= 0f) { "minPointDistance negativo" }
        require(resampleSpacing > 0f) { "resampleSpacing deve essere positivo" }
        require(minWidthFactor in 0.01f..1f) { "minWidthFactor fuori intervallo: $minWidthFactor" }
        require(velocityStrength in 0f..1f) { "velocityStrength fuori intervallo: $velocityStrength" }
        require(velocityReference > 0f) { "velocityReference deve essere positivo" }
    }

    companion object {
        val Default = InkConfig()
    }
}
