package com.fieldflow.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fieldflow.audio.AudioCaptureController
import com.fieldflow.stt.TranscriptionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

enum class OverlayVisualState {
    IDLE_DIMMED,
    RECORDING_HOLD,
    RECORDING_LOCKED,
    TRANSCRIBING
}

/**
 * Persistent microphone ForegroundService rendering the draggable 56dp floating bubble
 * and dispatching finished recordings to Sprint 2 TranscriptionWorker (VAD + whisper.cpp + .txt).
 */
class OverlayCaptureService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.fieldflow.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.fieldflow.action.HIDE_OVERLAY"
        const val ACTION_OPEN_EDITOR = "com.fieldflow.action.OPEN_EDITOR"
        const val EXTRA_FOLDER_ID = "com.fieldflow.extra.FOLDER_ID"

        private const val CHANNEL_ID = "fieldflow_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        private const val HOLD_THRESHOLD_MS = 120L
        private const val DRAG_SLOP_DP = 8f
        private const val SLIDE_TO_LOCK_DP = 48f
        private const val BUBBLE_SIZE_DP = 56f
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var audioController: AudioCaptureController
    private lateinit var bubbleContainer: FrameLayout
    private lateinit var bubbleCircle: View
    private lateinit var stateBadge: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val _visualState = MutableStateFlow(OverlayVisualState.IDLE_DIMMED)
    val visualState: StateFlow<OverlayVisualState> = _visualState.asStateFlow()

    private var activeFolderId = "A_Field_Logs"
    private var holdJob: Job? = null
    private var timerJob: Job? = null
    private var elapsedSeconds = 0

    private var dragSlopPx = 0f
    private var slideToLockPx = 0f
    private var screenWidthPx = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioController = AudioCaptureController(applicationContext)
        dragSlopPx = dpToPx(DRAG_SLOP_DP)
        slideToLockPx = dpToPx(SLIDE_TO_LOCK_DP)
        screenWidthPx = resources.displayMetrics.widthPixels

        createNotificationChannel()
        startForegroundWithMicType()
        initFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val folderExtra = intent?.getStringExtra(EXTRA_FOLDER_ID)
        if (!folderExtra.isNullOrBlank()) {
            activeFolderId = folderExtra
        }
        when (intent?.action) {
            ACTION_HIDE_OVERLAY -> {
                removeBubble()
                stopSelf()
            }
            else -> {
                if (Settings.canDrawOverlays(this)) {
                    attachBubbleIfNeeded()
                    audioController.arm()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        audioController.release()
        removeBubble()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithMicType() {
        val notification = buildNotification("Floating dictaphone ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingBubble() {
        val sizePx = dpToPx(BUBBLE_SIZE_DP).toInt()
        bubbleContainer = FrameLayout(this).apply {
            alpha = 0.40f
        }

        bubbleCircle = View(this).apply {
            background = createCircleDrawable(Color.parseColor("#1E293B"), Color.parseColor("#60A5FA"))
        }
        bubbleContainer.addView(bubbleCircle, FrameLayout.LayoutParams(sizePx, sizePx))

        stateBadge = TextView(this).apply {
            text = "MIC"
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
        }
        bubbleContainer.addView(stateBadge, FrameLayout.LayoutParams(sizePx, sizePx))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidthPx - sizePx - dpToPx(12f).toInt()
            y = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        }

        var downRawX = 0f
        var downRawY = 0f
        var startParamX = 0
        var startParamY = 0
        var isDragging = false

        bubbleContainer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startParamX = layoutParams.x
                    startParamY = layoutParams.y
                    isDragging = false

                    if (_visualState.value == OverlayVisualState.IDLE_DIMMED) {
                        holdJob?.cancel()
                        holdJob = serviceScope.launch {
                            delay(HOLD_THRESHOLD_MS)
                            if (!isDragging) {
                                enterHoldRecording()
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val distance = hypot(dx, dy)

                    when (_visualState.value) {
                        OverlayVisualState.RECORDING_HOLD -> {
                            if (dy < -slideToLockPx) {
                                enterLockedRecording()
                            }
                        }
                        OverlayVisualState.IDLE_DIMMED -> {
                            if (distance > dragSlopPx) {
                                isDragging = true
                                holdJob?.cancel()
                                layoutParams.x = (startParamX + dx).toInt()
                                layoutParams.y = (startParamY + dy).toInt()
                                windowManager.updateViewLayout(bubbleContainer, layoutParams)
                            }
                        }
                        else -> Unit
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdJob?.cancel()
                    when {
                        isDragging -> snapToNearestEdge()
                        _visualState.value == OverlayVisualState.RECORDING_HOLD -> stopRecordingAndQueue()
                        _visualState.value == OverlayVisualState.RECORDING_LOCKED -> stopRecordingAndQueue()
                        _visualState.value == OverlayVisualState.IDLE_DIMMED -> openEditorScreen()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun enterHoldRecording() {
        _visualState.value = OverlayVisualState.RECORDING_HOLD
        vibratePulse(longArrayOf(0, 35))
        renderState(OverlayVisualState.RECORDING_HOLD)
        startElapsedTimer()
        audioController.startCapture(activeFolderId)
    }

    private fun enterLockedRecording() {
        _visualState.value = OverlayVisualState.RECORDING_LOCKED
        vibratePulse(longArrayOf(0, 30, 50, 45))
        renderState(OverlayVisualState.RECORDING_LOCKED)
    }

    private fun stopRecordingAndQueue() {
        timerJob?.cancel()
        vibratePulse(longArrayOf(0, 25))
        _visualState.value = OverlayVisualState.TRANSCRIBING
        renderState(OverlayVisualState.TRANSCRIBING)

        serviceScope.launch {
            val result = audioController.stopCapture()
            if (result != null) {
                TranscriptionWorker.enqueue(
                    context = applicationContext,
                    recordingId = result.recordingId,
                    folderId = activeFolderId,
                    audioFile = result.audioFile,
                    autoCopyToClipboard = true
                )
            }
            delay(350)
            _visualState.value = OverlayVisualState.IDLE_DIMMED
            renderState(OverlayVisualState.IDLE_DIMMED)
        }
    }

    private fun openEditorScreen() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = ACTION_OPEN_EDITOR
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (intent != null) {
            startActivity(intent)
        }
    }

    private fun snapToNearestEdge() {
        val bubbleWidth = dpToPx(BUBBLE_SIZE_DP).toInt()
        val margin = dpToPx(12f).toInt()
        val center = layoutParams.x + bubbleWidth / 2
        layoutParams.x = if (center < screenWidthPx / 2) margin else screenWidthPx - bubbleWidth - margin
        windowManager.updateViewLayout(bubbleContainer, layoutParams)
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        elapsedSeconds = 0
        timerJob = serviceScope.launch {
            while (isActive) {
                val mins = elapsedSeconds / 60
                val secs = elapsedSeconds % 60
                stateBadge.text = "%02d:%02d".format(mins, secs)
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    private fun renderState(state: OverlayVisualState) {
        when (state) {
            OverlayVisualState.IDLE_DIMMED -> {
                bubbleContainer.alpha = 0.40f
                bubbleContainer.scaleX = 1.0f
                bubbleContainer.scaleY = 1.0f
                bubbleCircle.background = createCircleDrawable(
                    Color.parseColor("#1E293B"),
                    Color.parseColor("#60A5FA")
                )
                stateBadge.text = "MIC"
            }
            OverlayVisualState.RECORDING_HOLD -> {
                bubbleContainer.alpha = 1.0f
                bubbleContainer.scaleX = 1.12f
                bubbleContainer.scaleY = 1.12f
                bubbleCircle.background = createCircleDrawable(
                    Color.parseColor("#B91C1C"),
                    Color.parseColor("#FCA5A5")
                )
            }
            OverlayVisualState.RECORDING_LOCKED -> {
                bubbleContainer.alpha = 1.0f
                bubbleContainer.scaleX = 1.12f
                bubbleContainer.scaleY = 1.12f
                bubbleCircle.background = createCircleDrawable(
                    Color.parseColor("#1D4ED8"),
                    Color.parseColor("#93C5FD")
                )
            }
            OverlayVisualState.TRANSCRIBING -> {
                bubbleContainer.alpha = 0.92f
                bubbleContainer.scaleX = 1.0f
                bubbleContainer.scaleY = 1.0f
                bubbleCircle.background = createCircleDrawable(
                    Color.parseColor("#B45309"),
                    Color.parseColor("#FCD34D")
                )
                stateBadge.text = "..."
            }
        }
    }

    private fun attachBubbleIfNeeded() {
        if (!bubbleContainer.isAttachedToWindow) {
            windowManager.addView(bubbleContainer, layoutParams)
        }
    }

    private fun removeBubble() {
        if (::bubbleContainer.isInitialized && bubbleContainer.isAttachedToWindow) {
            windowManager.removeView(bubbleContainer)
        }
    }

    private fun createCircleDrawable(fillColor: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(dpToPx(2.5f).toInt(), strokeColor)
        }

    private fun vibratePulse(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FieldFlow Floating Dictaphone",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FieldFlow")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
