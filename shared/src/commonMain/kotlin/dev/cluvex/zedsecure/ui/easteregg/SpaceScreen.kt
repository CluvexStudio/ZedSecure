package dev.cluvex.zedsecure.ui.easteregg

import dev.cluvex.zedsecure.ui.platform.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.util.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SpaceScreen(onExit: () -> Unit) {
    var seed by rememberSaveable { mutableLongStateOf(dailySeed()) }
    val game = remember(seed) { GameState(seed) }

    BackHandler(onBack = onExit)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            SystemCanvas(game)

            val density = LocalDensity.current
            val deadZone = with(density) { 48.dp.toPx() }
            val fullThrottle = with(density) { 104.dp.toPx() }
            FlightStick(
                modifier = Modifier.fillMaxSize(),
                deadZone = deadZone,
                fullThrottle = fullThrottle,
                onStick = { stick -> game.steer(stick, deadZone, fullThrottle) },
            )

            Telemetry(
                game = game,
                onExit = onExit,
                onToggleAutopilot = game::toggleAutopilot,
                onNewSystem = { seed = Random.nextLong().absoluteValue % 10_000_000L },
            )
        }
    }

    LaunchedEffect(game) {
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                game.universe.step(nanos)
                game.onFrame(nanos)
            }
        }
    }
}

@Stable
private class GameState(seed: Long) {
    val universe = Universe(Namer(), seed).apply { initRandom() }
    val autopilot = Autopilot(universe.ship, universe).also { universe.add(it) }

    val frame = mutableLongStateOf(0L)
    var hud by mutableStateOf(buildHud())
        private set
    var autopilotOn by mutableStateOf(false)
        private set

    var zoom = DEFAULT_ZOOM

    private var lastHudNanos = 0L
    private var discovery: Planet? = null
    private var discoveredAt = -DISCOVERY_BANNER_SECONDS

    fun onFrame(nanos: Long) {
        frame.longValue = nanos
        if (universe.latestDiscovery !== discovery) {
            discovery = universe.latestDiscovery
            discoveredAt = universe.now
        }
        if (nanos - lastHudNanos > HUD_INTERVAL_NANOS) {
            lastHudNanos = nanos
            hud = buildHud()
        }
    }

    fun toggleAutopilot() {
        autopilotOn = !autopilotOn
        if (autopilotOn) autopilot.enabled = true else autopilot.disengage()
    }

    fun steer(stick: Vec2, deadZone: Float, fullThrottle: Float) {
        val ship = universe.ship
        if (stick == Vec2.Zero) {
            ship.thrust = Vec2.Zero
            return
        }
        val magnitude = stick.mag()
        ship.angle = stick.angle()
        ship.thrust = if (magnitude < deadZone) {
            Vec2.Zero
        } else {
            vecFromAngle(ship.angle, unlerp(deadZone, fullThrottle, magnitude).coerceIn(0f, 1f))
        }
    }

    private fun buildHud(): HudModel {
        val ship = universe.ship
        val closest = universe.closestPlanet()
        val altitude = ((closest.pos - ship.pos).mag() - closest.radius).roundToInt()

        val catalog = buildString {
            appendLine("  STAR: ${universe.star.name.uppercase()} (ZED-${universe.seed % 100_000})")
            appendLine(" CLASS: ${universe.star.cls.name}")
            appendLine("RADIUS: ${universe.star.radius.roundToInt()}")
            appendLine("  MASS: %.3g".format(universe.star.mass))
            appendLine("BODIES: ${universe.exploredCount} / ${universe.planets.size}")
            universe.planets.filter { it.explored }.forEach {
                appendLine()
                appendLine("  BODY: ${it.name.uppercase()}")
                appendLine("  TYPE: ${it.description}")
                appendLine("  ATMO: ${it.atmosphere}")
                appendLine(" FLORA: ${it.flora}")
                appendLine(" FAUNA: ${it.fauna}")
            }
        }.trimEnd()

        val flight = listOfNotNull(
            ship.landing?.let { "LND: ${it.planet.name.uppercase()}\nJOB: ${it.activity}" }
                ?: altitude.takeIf { it < 10_000 }?.let { "ALT: $it" },
            "THR: %.0f%%".format(ship.thrust.mag() * 100f),
            "POS: %+7.0f %+7.0f".format(ship.pos.x, ship.pos.y),
            "VEL: %.0f".format(ship.velocity.mag()),
        ).joinToString("\n")

        val fresh = universe.now - discoveredAt < DISCOVERY_BANNER_SECONDS
        return HudModel(
            catalog = catalog,
            flight = flight,
            autopilot = autopilot.telemetry.takeIf { autopilot.enabled },
            discovery = discovery?.takeIf { fresh }?.let { "◈ ${it.name.uppercase()} SURVEYED" },
        )
    }

    private companion object {
        const val HUD_INTERVAL_NANOS = 100_000_000L
        const val DISCOVERY_BANNER_SECONDS = 6f
    }
}

private data class HudModel(
    val catalog: String,
    val flight: String,
    val autopilot: String?,
    val discovery: String?,
)

private fun dailySeed(): Long = with(GregorianCalendar()) {
    get(Calendar.YEAR) * 10_000L + get(Calendar.MONTH) * 100L + get(Calendar.DAY_OF_MONTH)
}

@Composable
private fun FlightStick(
    modifier: Modifier,
    deadZone: Float,
    fullThrottle: Float,
    onStick: (Vec2) -> Unit,
) {
    var origin by remember { mutableStateOf<Vec2?>(null) }
    var current by remember { mutableStateOf(Vec2.Zero) }

    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    origin = down.position
                    current = down.position
                    onStick(Vec2.Zero)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        current = change.position
                        onStick(change.position - down.position)
                        if (change.positionChanged()) change.consume()
                        if (!change.pressed) break
                    }
                    origin = null
                    onStick(Vec2.Zero)
                }
            }
            .drawBehind {
                val anchor = origin ?: return@drawBehind
                val delta = current - anchor
                val magnitude = delta.mag().coerceAtMost(fullThrottle)
                val ring = max(deadZone, magnitude)
                drawCircle(
                    color = EggColors.Track,
                    center = anchor,
                    radius = ring,
                    style = Stroke(
                        width = 2f,

                        pathEffect = if (magnitude < deadZone) {
                            PathEffect.dashPathEffect(floatArrayOf(density * 1f, density * 2f))
                        } else {
                            null
                        },
                    ),
                )
                if (magnitude > 0f) {
                    drawLine(
                        color = EggColors.Track,
                        start = anchor,
                        end = anchor + vecFromAngle(delta.angle(), magnitude),
                        strokeWidth = 2f,
                    )
                }
            }
    )
}

private const val DEFAULT_ZOOM = 1f
private val MIN_ZOOM = 250f / UNIVERSE_RANGE
private const val MAX_ZOOM = 5f

private const val STAR_POINTS = 12

@Composable
private fun SystemCanvas(game: GameState) {
    val backdrop = remember(game) {
        List(120) { Offset(Random.nextFloat(), Random.nextFloat()) to Random.nextFloat() }
    }

    Canvas(Modifier.fillMaxSize()) {
        game.frame.longValue

        val universe = game.universe
        val ship = universe.ship
        drawRect(EggColors.Space)

        val closest = universe.closestPlanet()
        val surfaceRange = max(1f, (ship.pos - closest.pos).mag() - closest.radius * 1.2f)
        val targetZoom =
            if (game.autopilotOn) (500f / surfaceRange).coerceIn(MIN_ZOOM, MAX_ZOOM) else DEFAULT_ZOOM
        game.zoom = expSmooth(game.zoom, targetZoom, dt = universe.dt, speed = 1.5f)
        val zoom = game.zoom

        drawBackdropStars(backdrop, ship.pos, zoom)

        val halfWidth = size.width / (2f * zoom)
        val halfHeight = size.height / (2f * zoom)
        val view = Rect(
            left = ship.pos.x - halfWidth,
            top = ship.pos.y - halfHeight,
            right = ship.pos.x + halfWidth,
            bottom = ship.pos.y + halfHeight,
        )

        translate(size.width / 2f, size.height / 2f) {
            scale(zoom, zoom, pivot = Offset.Zero) {
                translate(-ship.pos.x, -ship.pos.y) {
                    drawGrid(view, zoom, minSpacing = 32.dp.toPx())
                    universe.planets.forEach { drawOrbit(it, zoom) }
                    drawStar(universe.star, zoom)
                    universe.planets.forEach { drawPlanet(it, zoom) }
                    universe.entities.filterIsInstance<Spark>().forEach { drawSpark(it, zoom) }
                    if (game.autopilotOn) drawAutopilot(game.autopilot, ship, universe.now, zoom)
                    drawTrack(ship.track, zoom)
                    drawShip(ship, zoom)
                }
            }
        }
    }
}

private fun DrawScope.drawBackdropStars(
    backdrop: List<Pair<Offset, Float>>,
    focus: Vec2,
    zoom: Float,
) {
    val shiftX = focus.x * PARALLAX * zoom
    val shiftY = focus.y * PARALLAX * zoom
    backdrop.forEach { (position, brightness) ->
        val x = (position.x * size.width - shiftX).mod(size.width)
        val y = (position.y * size.height - shiftY).mod(size.height)
        drawCircle(
            color = Color.White.copy(alpha = 0.08f + brightness * 0.22f),
            radius = 0.6f + brightness,
            center = Offset(x, y),
        )
    }
}

private const val PARALLAX = 0.03f

private fun DrawScope.drawGrid(view: Rect, zoom: Float, minSpacing: Float) {
    var step = 1_000f
    while (step * zoom < minSpacing) step *= 10f

    var x = floor(view.left / step) * step
    while (x < view.right) {
        drawLine(
            color = EggColors.Grid,
            start = Offset(x, view.top),
            end = Offset(x, view.bottom),
            strokeWidth = (if (x % (step * 10f) == 0f) 3f else 1.5f) / zoom,
        )
        x += step
    }

    var y = floor(view.top / step) * step
    while (y < view.bottom) {
        drawLine(
            color = EggColors.Grid,
            start = Offset(view.left, y),
            end = Offset(view.right, y),
            strokeWidth = (if (y % (step * 10f) == 0f) 3f else 1.5f) / zoom,
        )
        y += step
    }
}

private fun DrawScope.worthDrawing(radius: Float, zoom: Float): Boolean =
    radius * zoom <= size.maxDimension * 20f

private fun DrawScope.drawOrbit(planet: Planet, zoom: Float) {
    if (!worthDrawing(planet.orbitRadius, zoom)) return
    drawCircle(
        color = EggColors.Orbit,
        radius = planet.orbitRadius,
        center = planet.orbitCenter,
        style = Stroke(width = 1f / zoom),
    )
}

private fun DrawScope.drawGravityWell(body: Planet, zoom: Float) {
    repeat(GRAVITY_RINGS) { i ->
        val fraction = i.toFloat() / GRAVITY_RINGS
        val radius = body.rangeForGravity(lerp(200f, 0.01f, fraction))
        if (!worthDrawing(radius, zoom)) return@repeat
        drawCircle(
            color = Color(1f, 0.35f, 0.35f, 0.20f * (1f - fraction)),
            center = body.pos,
            radius = radius,
            style = Stroke(2f / zoom),
        )
    }
}

private const val GRAVITY_RINGS = 8

private fun DrawScope.drawPlanet(planet: Planet, zoom: Float) {
    drawGravityWell(planet, zoom)

    drawCircle(color = EggColors.Space, radius = planet.radius, center = planet.pos)
    drawCircle(
        color = planet.color,
        radius = planet.radius,
        center = planet.pos,
        style = Stroke(2f / zoom),
    )
    planet.flagAngle?.let { drawFlag(planet, it, zoom) }
}

private fun DrawScope.drawFlag(planet: Planet, angle: Float, zoom: Float) {
    val base = planet.pos + vecFromAngle(angle, planet.radius)
    val height = max(60f, planet.radius * 0.35f)
    rotateRad(angle, pivot = base) {
        translate(base.x, base.y) {
            val flag = Path().apply {
                moveTo(0f, 0f)
                lineTo(height, 0f)
                lineTo(height * 0.875f, height * 0.25f)
                lineTo(height * 0.75f, 0f)
                close()
            }
            drawPath(flag, EggColors.Flag, style = Stroke(width = 2f / zoom))
        }
    }
}

private fun DrawScope.drawStar(star: Star, zoom: Float) {
    translate(star.pos.x, star.pos.y) {
        drawCircle(color = star.color, radius = star.radius, center = Offset.Zero)
    }
    drawGravityWell(star, zoom)

    listOf(
        Triple(star.anim / 23f, star.radius + 80f to star.radius + 250f, STAR_POINTS),
        Triple(star.anim / -19f, star.radius + 20f to star.radius + 200f, STAR_POINTS + 1),
    ).forEach { (turns, radii, points) ->
        rotateRad(turns * PI2f, pivot = star.pos) {
            translate(star.pos.x, star.pos.y) {
                drawPath(
                    path = starPath(radii.first, radii.second, points),
                    color = star.color,
                    style = Stroke(
                        width = 3f / zoom,
                        pathEffect = PathEffect.cornerPathEffect(radius = 200f),
                    ),
                )
            }
        }
    }
}

private fun starPath(inner: Float, outer: Float, points: Int): Path = Path().apply {
    val step = PI2f / points
    moveTo(inner, 0f)
    for (i in 0 until points) {
        lineTo(outer * cos(step * (i + 0.5f)), outer * sin(step * (i + 0.5f)))
        lineTo(inner * cos(step * (i + 1)), inner * sin(step * (i + 1)))
    }
    close()
}

private val shipHull = Path().apply {
    moveTo(14f, 0f)
    lineTo(-6f, -9f)
    lineTo(-2f, 0f)
    lineTo(-6f, 9f)
    close()
}

private val shipLegs = Path().apply {
    moveTo(-4f, -7f); lineTo(-12f, -13f); moveTo(-9f, -15f); lineTo(-15f, -11f)
    moveTo(-4f, 7f); lineTo(-12f, 13f); moveTo(-9f, 15f); lineTo(-15f, 11f)
}

private val thrustPlume = Path().apply {
    moveTo(-4f, -5f)
    lineTo(-18f, 0f)
    lineTo(-4f, 5f)
    close()
}

private fun DrawScope.drawShip(ship: Spacecraft, zoom: Float) {
    rotateRad(ship.angle, pivot = ship.pos) {
        translate(ship.pos.x, ship.pos.y) {
            if (ship.landing != null) {
                drawPath(shipLegs, Color(0xFFCCCCCC), style = Stroke(width = 2f / zoom))
            }
            if (ship.thrust != Vec2.Zero) {
                drawPath(
                    path = thrustPlume,
                    color = EggColors.Thrust,
                    style = Stroke(
                        width = 2f / zoom,
                        pathEffect = PathEffect.cornerPathEffect(radius = 1f),
                    ),
                )
            }
            drawPath(shipHull, EggColors.Space)
            drawPath(
                path = shipHull,

                color = if (ship.transit) Color.Black else Color.White,
                style = Stroke(width = 2f / zoom),
            )
        }
    }
}

private fun DrawScope.drawTrack(track: Track, zoom: Float) {
    if (track.positions.size < 2) return

    drawPoints(
        points = track.positions,
        pointMode = PointMode.Lines,
        color = EggColors.Track,
        strokeWidth = 1f / zoom,
    )
}

private fun DrawScope.drawSpark(spark: Spark, zoom: Float) {
    when (spark.style) {
        Spark.Style.Dot -> drawCircle(spark.color, spark.size, spark.pos)
        Spark.Style.Line -> drawLine(spark.color, spark.previousPos, spark.pos, spark.size / zoom)
        Spark.Style.Ring -> drawCircle(
            color = spark.color.copy(alpha = spark.color.alpha * (1f - spark.life)),
            radius = exp(spark.size + 2f * spark.size * spark.life) - 1f,
            center = spark.pos,
            style = Stroke(width = 1f / zoom),
        )
    }
}

private fun DrawScope.drawAutopilot(
    autopilot: Autopilot,
    ship: Spacecraft,
    now: Float,
    zoom: Float,
) {
    val target = autopilot.target ?: return
    val color = EggColors.Autopilot.copy(alpha = 0.5f)
    rotateRad(now * PI2f / 10f, pivot = target.pos) {
        translate(target.pos.x, target.pos.y) {
            drawPath(
                path = polygonPath(target.radius + autopilot.brakingDistance, sides = 15),
                color = color,
                style = Stroke(1f / zoom),
            )
        }
    }
    drawCircle(
        color = color,
        radius = target.radius + autopilot.landingAltitude / 2f,
        center = target.pos,
        alpha = 0.25f,
        style = Stroke(autopilot.landingAltitude),
    )
    drawLine(color, start = ship.pos, end = autopilot.leadPoint, strokeWidth = 1f / zoom)
    drawCircle(color, radius = 5f / zoom, center = autopilot.leadPoint, style = Stroke(1f / zoom))
}

private fun polygonPath(radius: Float, sides: Int): Path = Path().apply {
    val step = PI2f / sides
    moveTo(radius, 0f)
    for (i in 1 until sides) lineTo(radius * cos(step * i), radius * sin(step * i))
    close()
}

private val flickerFadeIn = fadeIn(
    animationSpec = tween(
        durationMillis = 1_000,
        easing = Easing { fraction ->
            CubicBezierEasing(0f, 1f, 1f, 0f).transform(fraction).let {
                if (Random.nextFloat() < it) 1f else 0f
            }
        },
    ),
)

private val consoleStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.sp,

    shadow = Shadow(color = EggColors.Space, blurRadius = 6f),
)

@Composable
private fun Telemetry(
    game: GameState,
    onExit: () -> Unit,
    onToggleAutopilot: () -> Unit,
    onNewSystem: () -> Unit,
) {
    val hud = game.hud
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(14.dp),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = flickerFadeIn,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.62f),
        ) {
            Catalog(hud.catalog)
        }

        Column(Modifier.align(Alignment.BottomStart)) {
            AnimatedVisibility(visible = hud.discovery != null, enter = flickerFadeIn, exit = fadeOut()) {
                Text(
                    text = hud.discovery.orEmpty(),
                    style = consoleStyle,
                    color = EggColors.Flag,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            hud.autopilot?.let {
                Text(it, style = consoleStyle, color = EggColors.Autopilot)
                Spacer(Modifier.size(8.dp))
            }
            AnimatedVisibility(visible = visible, enter = flickerFadeIn) {
                Text(hud.flight, style = consoleStyle, color = EggColors.Console)
            }
        }

        ConsoleButton(
            label = "×",
            modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
            onClick = onExit,
        )

        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConsoleButton(
                label = "NEW",
                modifier = Modifier.size(width = 76.dp, height = 44.dp),
                onClick = onNewSystem,
            )
            ConsoleButton(
                label = "AUTO",
                modifier = Modifier.size(width = 76.dp, height = 44.dp),
                active = game.autopilotOn,
                onClick = onToggleAutopilot,
            )
        }
    }
}

@Composable
private fun Catalog(text: String) {
    var size by remember { mutableFloatStateOf(11f) }
    Text(
        text = text,
        style = consoleStyle,
        color = EggColors.Console,
        fontSize = size.sp,
        lineHeight = (size * 1.15f).sp,
        onTextLayout = { result -> if (result.didOverflowHeight && size > 8f) size -= 1f },
    )
}

@Composable
private fun ConsoleButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (active) EggColors.Autopilot.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
    ) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = consoleStyle,
                color = if (active) EggColors.Autopilot else Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
