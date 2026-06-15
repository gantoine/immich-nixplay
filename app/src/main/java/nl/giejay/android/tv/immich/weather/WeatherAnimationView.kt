package nl.giejay.android.tv.immich.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Random
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
    private var glowShader: RadialGradient? = null
    private val cloudPath = Path()
    private val locOnScreen = IntArray(2)
    private var glowPhase = 0f
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
        drops.clear(); flakes.clear(); fogBands.clear(); clouds.clear(); glowShader = null
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
            Scene.CLOUDS -> repeat(3) { i ->
                clouds.add(Band(
                    rnd.nextFloat() * w,
                    h * (0.13f + 0.15f * i),
                    w * (0.34f + rnd.nextFloat() * 0.20f),
                    h * (0.10f + rnd.nextFloat() * 0.05f),
                    (5f + rnd.nextFloat() * 6f) * d,
                    60 + rnd.nextInt(45)
                ))
            }
            Scene.CLEAR, Scene.NONE -> {}
        }
        buildGlow(h)
    }

    /**
     * Soft weather-tinted ambient glow drawn under the particles. Clear gets warm light from the
     * screen's top-right corner; overcast/precipitation get a dimmer, cooler diffuse light from the
     * top, scaled down for darker conditions. Anchored to the screen (the view is shifted behind the
     * sidebar) so it sits where the light should be.
     */
    private fun buildGlow(h: Float) {
        getLocationOnScreen(locOnScreen)
        val screenW = resources.displayMetrics.widthPixels
        val rightInView = screenW - locOnScreen[0]
        val centerInView = screenW / 2f - locOnScreen[0]
        glowShader = when (scene) {
            Scene.CLEAR -> RadialGradient(
                rightInView - h * 0.08f, h * 0.08f, h * 0.9f,
                intArrayOf(0x66FFF0E2, 0x24FFE6CE, 0x00000000),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            Scene.CLOUDS -> skyGlow(centerInView, h, 0x40EAF1F7, 0x1AEAF1F7)
            Scene.SNOW -> skyGlow(centerInView, h, 0x44E8F0F8, 0x1CE8F0F8)
            Scene.FOG -> skyGlow(centerInView, h, 0x33E6E9ED, 0x16E6E9ED)
            Scene.RAIN -> skyGlow(centerInView, h, 0x2EDCE6F0, 0x12DCE6F0)
            Scene.THUNDER -> skyGlow(centerInView, h, 0x22C8D2E0, 0x0EC8D2E0)
            Scene.NONE -> null
        }
    }

    /** Broad, soft diffuse light from above (for non-clear skies). */
    private fun skyGlow(cx: Float, h: Float, center: Int, mid: Int) = RadialGradient(
        cx, -h * 0.08f, h * 1.1f,
        intArrayOf(center, mid, 0x00000000),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)

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
        drawGlow(canvas, dt, w, h)
        when (scene) {
            Scene.RAIN -> drawRain(canvas, dt, w, h)
            Scene.THUNDER -> { drawRain(canvas, dt, w, h); drawThunder(canvas, dt, w, h) }
            Scene.SNOW -> drawSnow(canvas, dt, w, h)
            Scene.FOG -> drawBands(canvas, dt, w, fogBands, true)
            Scene.CLOUDS -> drawClouds(canvas, dt, w)
            Scene.CLEAR, Scene.NONE -> {}
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

    private fun drawGlow(canvas: Canvas, dt: Float, w: Float, h: Float) {
        // Soft, slowly-breathing ambient light wash (under the particles).
        glowPhase += dt
        val pulse = 0.9f + 0.1f * sin(glowPhase * 0.6f)
        glowShader?.let {
            paint.style = Paint.Style.FILL
            paint.shader = it
            paint.alpha = (255 * pulse).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
    }

    /** Drifting fluffy clouds — each a union of overlapping circles drawn as one Path (uniform alpha). */
    private fun drawClouds(canvas: Canvas, dt: Float, w: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        for (b in clouds) {
            b.x += b.speed * dt
            val halfW = b.w / 2f
            if (b.x - halfW > w) b.x = -halfW
            cloudPath.reset()
            val cx = b.x
            val cy = b.y
            val cw = b.w
            val r = b.h
            cloudPath.addCircle(cx - cw * 0.30f, cy + b.h * 0.18f, r * 0.85f, Path.Direction.CW)
            cloudPath.addCircle(cx - cw * 0.04f, cy + b.h * 0.24f, r * 1.05f, Path.Direction.CW)
            cloudPath.addCircle(cx + cw * 0.26f, cy + b.h * 0.18f, r * 0.9f, Path.Direction.CW)
            cloudPath.addCircle(cx - cw * 0.14f, cy, r * 0.95f, Path.Direction.CW)
            cloudPath.addCircle(cx + cw * 0.10f, cy - b.h * 0.08f, r * 1.05f, Path.Direction.CW)
            paint.alpha = b.alpha
            canvas.drawPath(cloudPath, paint)
        }
    }
}
