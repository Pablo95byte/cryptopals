package app.inknote.android

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * La fotocamera dentro il foglio (D45).
 *
 * ## Perché non l'app fotocamera del sistema
 *
 * Il primo telefono vero l'ha mostrato: aprire la fotocamera del sistema dal foglio
 * voleva dire lasciare la nostra app. A telefono bloccato alcune fotocamere chiedono lo
 * sblocco, e alcune girano in un compito loro: allora il sistema ci dà subito "annullato",
 * il foglio crede di essere stato abbandonato e si chiude (D34). La foto era persa, e
 * l'utente si ritrovava davanti alla richiesta del codice.
 *
 * Qui la fotocamera è una vista sopra il foglio, nella stessa Activity: resta sopra il
 * blocco come il foglio, non cambia app, e la foto arriva a noi come byte. Camera2 è
 * un'API di piattaforma: nessuna libreria, nessun `ContentProvider` (invariante 21).
 *
 * Costa il permesso della fotocamera, chiesto una volta sola al primo scatto.
 */
class InlineCamera(
    private val activity: Activity,
    private val onPhoto: (ByteArray) -> Unit,
    private val onClose: () -> Unit,
    private val onFailure: () -> Unit,
) {
    val view: FrameLayout = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }

    private val texture = TextureView(activity)
    private val shutter: View
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSize: Size? = null
    private var sensorOrientation = 90
    private var shooting = false

    init {
        view.addView(texture, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val close = Ui.iconButton(activity, R.drawable.ic_close, activity.getString(R.string.cancel), Color.WHITE) { onClose() }
        view.addView(close, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            topMargin = Ui.dp(activity, 8f)
            leftMargin = Ui.dp(activity, 8f)
        })

        // Il pulsante di scatto: un anello bianco, il segno che tutti riconoscono.
        val size = Ui.dp(activity, 76f)
        shutter = View(activity).apply {
            background = Ui.pressable(activity, Ui.rounded(0x33FFFFFF, size / 2f, Color.WHITE, Ui.dp(activity, 5f)), size / 2f)
            contentDescription = activity.getString(R.string.camera)
            setOnClickListener { shoot() }
        }
        view.addView(shutter, FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = Ui.dp(activity, 36f)
        })
        Ui.padForSystemBars(view, top = true, bottom = true)

        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) = open()
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = configureTransform(width, height)
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
    }

    /** Da chiamare dopo aver aggiunto [view] allo schermo. */
    fun start() {
        thread = HandlerThread("inknote-fotocamera").also { it.start() }
        handler = Handler(thread!!.looper)
        if (texture.isAvailable) open()
    }

    /** Libera la fotocamera: da chiamare quando la vista esce, e sempre in `onPause`. */
    fun stop() {
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        session = null
        device = null
        reader = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    @SuppressLint("MissingPermission") // il foglio apre questa vista solo a permesso concesso
    private fun open() {
        val handler = handler ?: return
        val manager = activity.getSystemService(CameraManager::class.java) ?: return fail()
        try {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull() ?: return fail()
            val characteristics = manager.getCameraCharacteristics(id)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return fail()

            // Una foto da documento, non da concorso: fino a 12 megapixel bastano per
            // leggere una lavagna, e pesano la metà di una da 50.
            val jpeg = map.getOutputSizes(ImageFormat.JPEG)
                .filter { it.width.toLong() * it.height <= 12_600_000L }
                .maxByOrNull { it.width.toLong() * it.height } ?: return fail()
            val aspect = jpeg.width.toFloat() / jpeg.height
            val previews = map.getOutputSizes(SurfaceTexture::class.java)
            previewSize = previews
                .filter { it.width <= 1920 && it.height <= 1440 && abs(it.width.toFloat() / it.height - aspect) < 0.02f }
                .maxByOrNull { it.width * it.height } ?: previews.first()

            reader = ImageReader.newInstance(jpeg.width, jpeg.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ source ->
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    image.close()
                    activity.runOnUiThread {
                        shooting = false
                        onPhoto(bytes)
                    }
                }, handler)
            }

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    startPreview(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    device = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    device = null
                    fail()
                }
            }, handler)
        } catch (e: CameraAccessException) {
            fail()
        } catch (e: SecurityException) {
            fail()
        }
    }

    @Suppress("DEPRECATION") // createCaptureSession con la lista: l'alternativa esiste da Android 9, noi partiamo da 8.1
    private fun startPreview(camera: CameraDevice) {
        val size = previewSize ?: return
        val st = texture.surfaceTexture ?: return
        val reader = reader ?: return
        st.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(st)
        try {
            camera.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (device == null) return
                    session = configured
                    try {
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        configured.setRepeatingRequest(request.build(), null, handler)
                    } catch (e: CameraAccessException) {
                        fail()
                    }
                }

                override fun onConfigureFailed(failed: CameraCaptureSession) = fail()
            }, handler)
        } catch (e: CameraAccessException) {
            fail()
        }
        activity.runOnUiThread { configureTransform(texture.width, texture.height) }
    }

    private fun shoot() {
        val camera = device ?: return
        val session = session ?: return
        val reader = reader ?: return
        if (shooting) return
        shooting = true
        // Un lampo bianco: la foto è stata presa, anche prima che arrivi.
        shutter.animate().scaleX(0.86f).scaleY(0.86f).setDuration(80).withEndAction {
            shutter.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
                set(CaptureRequest.JPEG_QUALITY, 88.toByte())
            }
            session.capture(request.build(), null, handler)
        } catch (e: CameraAccessException) {
            shooting = false
            fail()
        }
    }

    /** La foto va dritta come la tiene l'utente: orientamento del sensore meno quello dello schermo. */
    private fun jpegOrientation(): Int = (sensorOrientation - displayRotationDegrees() + 360) % 360

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = activity.windowManager.defaultDisplay.rotation

    private fun displayRotationDegrees(): Int = when (displayRotation()) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    /**
     * La preview riempie lo schermo senza deformarsi: si ritaglia ai lati, come fanno
     * tutte le fotocamere, invece di schiacciare l'immagine.
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val size = previewSize ?: return
        if (viewWidth == 0 || viewHeight == 0) return
        val rotation = displayRotation()
        val matrix = Matrix()
        val cx = viewWidth / 2f
        val cy = viewHeight / 2f
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            val bufferRect = RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
            bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(viewHeight.toFloat() / size.height, viewWidth.toFloat() / size.width)
            matrix.postScale(scale, scale, cx, cy)
            matrix.postRotate(90f * (rotation - 2), cx, cy)
        } else {
            // In verticale l'immagine arriva già dritta, larga quanto l'altezza della
            // preview: basta allargarla sul lato che avanza.
            val contentAspect = size.height.toFloat() / size.width
            val viewAspect = viewWidth.toFloat() / viewHeight
            if (viewAspect < contentAspect) {
                matrix.setScale(contentAspect / viewAspect, 1f, cx, cy)
            } else {
                matrix.setScale(1f, viewAspect / contentAspect, cx, cy)
            }
            if (rotation == Surface.ROTATION_180) matrix.postRotate(180f, cx, cy)
        }
        texture.setTransform(matrix)
    }

    private fun fail() {
        activity.runOnUiThread { onFailure() }
    }
}
