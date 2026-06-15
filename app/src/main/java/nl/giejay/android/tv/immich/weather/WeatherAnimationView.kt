package nl.giejay.android.tv.immich.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight Canvas-based weather animation drawn behind the weather page. Each scene is a small
 * particle system — rain streaks, drifting snow, fog/cloud bands, a pulsing sun — advanced by a
 * frame-time delta and redrawn on the animation pulse. Deliberately cheap (plain lines/circles/
 * ovals, no blur) for the frame's RK3128 GPU, and skipped entirely when not visible. Can be turned
 * off via the WEATHER_ANIMATED_BACKGROUND preference (scene NONE).
 */
class WeatherAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Scene { NONE, CLEAR, CLOUDS, RAIN, SNOW, FOG, THUNDER }

    private val rnd = Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ovalRect = android.graphics.RectF()
    private var scene = Scene.NONE
    private var running = false
    private var lastFrameMs = 0L

    // ~30fps, driven by a plain Handler post. Capped (rather than running at the display refresh via
    // postInvalidateOnAnimation) to keep the full-screen redraw light on the frame's RK3128 GPU.
    private val frameIntervalMs = 32L
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postDelayed(this, frameIntervalMs)
        }
    }

    private val drops = ArrayList<Drop>()
    private val flakes = ArrayList<Flake>()
    private val fogBands = ArrayList<Band>()
    private val clouds = ArrayList<Band>()
    private var sunShader: RadialGradient? = null
    private var sunPhase = 0f
    private var rayAngle = 0f
    private var flash = 0f
    private var thunderCooldown = 0f

    private class Drop(var x: Float, var y: Float, var len: Float, var speed: Float, var alpha: Int)
    private class Flake(var x: Float, var y: Float, var r: Float, var speed: Float, var amp: Float, var phase: Float, var phaseSpeed: Float)
    private class Band(var x: Float, var y: Float, var w: Float, var h: Float, var speed: Float, var alpha: Int)

    private val density get() = resources.displayMetrics.density

    fun setScene(newScene: Scene) {
        if (newScene != scene) {
            scene = newScene
            build()
        }
        if (scene == Scene.NONE) stop() else ensureRunning()
        invalidate()
    }

    private fun build() {
        drops.clear(); flakes.clear(); fogBands.clear(); clouds.clear(); sunShader = null
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = density
        when (scene) {
            Scene.RAIN, Scene.THUNDER -> {
                val n = if (scene == Scene.THUNDER) 150 else 120
                repeat(n) { drops.add(newDrop(w, h, d)) }
            }
            Scene.SNOW -> repeat(85) { flakes.add(newFlake(w, h, d)) }
            Scene.FOG -> repeat(5) { i ->
                fogBands.add(Band(
                    rnd.nextFloat() * w,
                    h * (0.16f + 0.16f * i),
                    w * (0.7f + rnd.nextFloat() * 0.6f),
                    h * (0.12f + rnd.nextFloat() * 0.10f),
                    (8f + rnd.nextFloat() * 12f) * d * (if (i % 2 == 0) 1f else -1f),
                    18 + rnd.nextInt(16)
                ))
            }
            Scene.CLOUDS -> repeat(4) { i ->
                clouds.add(Band(
                    rnd.nextFloat() * w,
                    h * (0.10f + 0.13f * i),
                    w * (0.28f + rnd.nextFloat() * 0.18f),
                    h * (0.10f + rnd.nextFloat() * 0.05f),
                    (6f + rnd.nextFloat() * 8f) * d,
                    22 + rnd.nextInt(18)
                ))
            }
            Scene.CLEAR -> {
                val cx = w * 0.80f
                val cy = h * 0.22f
                sunShader = RadialGradient(
                    cx, cy, h * 0.5f,
                    intArrayOf(0x99FFF6C8.toInt(), 0x33FFE08A, 0x00000000),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            Scene.NONE -> {}
        }
    }

    private fun newDrop(w: Float, h: Float, d: Float) = Drop(
        rnd.nextFloat() * w * 1.2f - w * 0.1f,
        rnd.nextFloat() * h,
        (12f + rnd.nextFloat() * 16f) * d,
        (820f + rnd.nextFloat() * 520f) * d,
        50 + rnd.nextInt(120)
    )

    private fun newFlake(w: Float, h: Float, d: Float) = Flake(
        rnd.nextFloat() * w,
        rnd.nextFloat() * h,
        (2f + rnd.nextFloat() * 4f) * d,
        (35f + rnd.nextFloat() * 60f) * d,
        (8f + rnd.nextFloat() * 34f) * d,
        rnd.nextFloat() * 6.28f,
        0.6f + rnd.nextFloat() * 1.2f
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        build()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureRunning()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) ensureRunning() else stop()
    }

    private fun ensureRunning() {
        if (!running && scene != Scene.NONE && windowVisibility == View.VISIBLE) {
            running = true
            lastFrameMs = 0L
            removeCallbacks(frameRunnable)
            postDelayed(frameRunnable, frameIntervalMs)
        }
    }

    private fun stop() {
        running = false
        removeCallbacks(frameRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.05f)
        lastFrameMs = now
        val w = width.toFloat()
        val h = height.toFloat()
        when (scene) {
            Scene.RAIN -> drawRain(canvas, dt, w, h)
            Scene.THUNDER -> { drawRain(canvas, dt, w, h); drawThunder(canvas, dt, w, h) }
            Scene.SNOW -> drawSnow(canvas, dt, w, h)
            Scene.FOG -> drawBands(canvas, dt, w, fogBands, true)
            Scene.CLOUDS -> drawBands(canvas, dt, w, clouds, false)
            Scene.CLEAR -> drawSun(canvas, dt, w, h)
            Scene.NONE -> {}
        }
    }

    private fun drawRain(canvas: Canvas, dt: Float, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * density
        paint.color = Color.WHITE
        val slant = 0.18f
        for (drop in drops) {
            drop.y += drop.speed * dt
            drop.x += drop.speed * slant * dt
            if (drop.y - drop.len > h) {
                drop.y = -drop.len
                drop.x = rnd.nextFloat() * w * 1.2f - w * 0.1f
            }
            paint.alpha = drop.alpha
            canvas.drawLine(drop.x, drop.y, drop.x - drop.len * slant, drop.y - drop.len, paint)
        }
    }

    private fun drawThunder(canvas: Canvas, dt: Float, w: Float, h: Float) {
        thunderCooldown -= dt
        if (flash <= 0f && thunderCooldown <= 0f && rnd.nextFloat() < 0.012f) {
            flash = 1f
            thunderCooldown = 2.5f + rnd.nextFloat() * 4f
        }
        if (flash > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = (flash * 120f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, paint)
            flash -= dt * 3.2f
            if (flash < 0f) flash = 0f
        }
    }

    private fun drawSnow(canvas: Canvas, dt: Float, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = 200
        for (f in flakes) {
            f.y += f.speed * dt
            f.phase += f.phaseSpeed * dt
            if (f.y - f.r > h) {
                f.y = -f.r
                f.x = rnd.nextFloat() * w
            }
            canvas.drawCircle(f.x + sin(f.phase) * f.amp, f.y, f.r, paint)
        }
    }

    private fun drawBands(canvas: Canvas, dt: Float, w: Float, bands: List<Band>, gray: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (gray) Color.rgb(200, 205, 210) else Color.WHITE
        for (b in bands) {
            b.x += b.speed * dt
            val half = b.w / 2f
            if (b.speed >= 0f && b.x - half > w) b.x = -half
            if (b.speed < 0f && b.x + half < 0f) b.x = w + half
            paint.alpha = b.alpha
            // drawOval(float,float,float,float,Paint) is API 21+; KitKat only has the RectF overload.
            ovalRect.set(b.x - half, b.y - b.h / 2f, b.x + half, b.y + b.h / 2f)
            canvas.drawOval(ovalRect, paint)
        }
    }

    private fun drawSun(canvas: Canvas, dt: Float, w: Float, h: Float) {
        sunPhase += dt
        rayAngle += dt * 6f
        val cx = w * 0.80f
        val cy = h * 0.22f
        val pulse = 0.85f + 0.15f * sin(sunPhase * 1.2f)
        // Faint rotating rays.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f * density
        paint.color = Color.rgb(255, 236, 170)
        paint.alpha = 30
        val rayInner = h * 0.18f
        val rayOuter = h * 0.34f * pulse
        val n = 12
        for (i in 0 until n) {
            val a = Math.toRadians((rayAngle + i * (360f / n)).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            canvas.drawLine(cx + rayInner * ca, cy + rayInner * sa, cx + rayOuter * ca, cy + rayOuter * sa, paint)
        }
        // Soft glow.
        sunShader?.let {
            paint.style = Paint.Style.FILL
            paint.shader = it
            paint.alpha = (220 * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, h * 0.5f, paint)
            paint.shader = null
        }
    }
}
