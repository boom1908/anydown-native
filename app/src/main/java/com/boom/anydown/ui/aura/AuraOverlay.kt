package com.boom.anydown.ui.aura

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

data class AuraParticle(
    val id: String,
    val start: Offset,
    val end: Offset,
    val color: Color
)

data class AuraSnapshot(val particle: AuraParticle, val t: Float)

/**
 * Holds in-flight aura particles for the "fly from tapped format card to the
 * Downloads tab icon" animation. Call [fire] from the tap handler, passing
 * both screen positions captured via onGloballyPositioned.
 */
class AuraOverlayController {
    private val particles = mutableStateListOf<AuraParticle>()
    private val progress = mutableStateMapOf<String, Animatable<Float, *>>()

    val activeParticles: List<AuraSnapshot>
        get() = particles.map { AuraSnapshot(it, progress[it.id]?.value ?: 0f) }

    fun fire(start: Offset, end: Offset, color: Color, scope: CoroutineScope) {
        val id = UUID.randomUUID().toString()
        particles.add(AuraParticle(id, start, end, color))
        val anim = Animatable(0f)
        progress[id] = anim
        scope.launch {
            // Slower + smoother than the first pass (700ms, ease-in-out) so
            // it reads clearly instead of flickering past.
            anim.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
            particles.removeAll { it.id == id }
            progress.remove(id)
        }
    }
}

private fun positionAt(particle: AuraParticle, t: Float): Offset {
    val x = particle.start.x + (particle.end.x - particle.start.x) * t
    val arcLift = -130f * (4 * t * (1 - t)) // taller arc than before for visibility
    val y = particle.start.y + (particle.end.y - particle.start.y) * t + arcLift
    return Offset(x, y)
}

@Composable
fun AuraOverlay(controller: AuraOverlayController, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        controller.activeParticles.forEach { snap ->
            val (particle, t) = snap

            // Comet trail: several faded echoes behind the head position.
            for (k in 5 downTo 0) {
                val trailT = (t - k * 0.045f).coerceIn(0f, 1f)
                val pos = positionAt(particle, trailT)
                val echoFade = (1f - k * 0.16f).coerceIn(0f, 1f)
                val fadeOut = 1f - trailT * 0.55f
                val alpha = (echoFade * fadeOut).coerceIn(0f, 1f)
                val radius = lerp(20f, 5f, trailT) * (1f - k * 0.1f)

                // layered glow: outer soft halo -> inner bright core
                drawCircle(color = particle.color.copy(alpha = alpha * 0.20f), radius = radius * 2.6f, center = pos)
                drawCircle(color = particle.color.copy(alpha = alpha * 0.45f), radius = radius * 1.6f, center = pos)
                drawCircle(color = particle.color.copy(alpha = alpha), radius = radius, center = pos)
            }

            // Landing pulse: an expanding ring that fires once the particle
            // is nearly at the Downloads tab icon.
            if (t > 0.82f) {
                val pulseT = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)
                drawCircle(
                    color = particle.color.copy(alpha = (1f - pulseT) * 0.7f),
                    radius = lerp(8f, 46f, pulseT),
                    center = particle.end,
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}
