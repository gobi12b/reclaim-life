package io.github.gobi12b.reclaimlife.ui.home

import io.github.gobi12b.reclaimlife.data.TreeStage
import kotlin.math.PI
import kotlin.math.cos

// The tree's shapes as plain numbers (no Compose types), in units of a 112×148 canvas with the
// base at (56, 142), so height and canopy can be tested to never go down from one state to the next.

/** A stage 0–4 plant: a curved stem and leaves at (position along the stem, length). */
internal class PlantShape(val stemHeight: Float, val leaves: List<Pair<Float, Float>>, val stemStroke: Float = 4f) {
    /** The seed's first leaf opens on one side only, like the brand mark's first sprout. */
    val oneSided: Boolean get() = stemHeight <= 10f
}

private val SAPLING_LEAVES = listOf(0.30f to 20f, 0.55f to 24f, 0.80f to 28f, 1f to 30f)

/** The first five stages, exactly as the old plant drew them. Null from the young tree on. */
internal fun plantShapeOf(stage: TreeStage): PlantShape? = when (stage) {
    TreeStage.SEED -> PlantShape(10f, listOf(1f to 12f))
    TreeStage.SPROUT -> PlantShape(40f, listOf(1f to 26f))
    TreeStage.SEEDLING -> PlantShape(64f, listOf(0.55f to 22f, 1f to 28f))
    TreeStage.YOUNG_PLANT -> PlantShape(88f, listOf(0.40f to 20f, 0.70f to 24f, 1f to 28f))
    TreeStage.SAPLING -> PlantShape(112f, SAPLING_LEAVES, stemStroke = 5f)
    else -> null
}

/** Leaves lean this far off vertical, one each side. */
internal const val LEAF_DEGREES = 50f

/** A point along a plant's stem: the quadratic from the base through (48, 142 − h/2) to the tip. */
internal fun stemPoint(stemHeight: Float, t: Float): Pair<Float, Float> {
    val a = (1 - t) * (1 - t)
    val b = 2 * (1 - t) * t
    val c = t * t
    return (a * 56f + b * 48f + c * 56f) to (a * 142f + b * (142f - stemHeight / 2) + c * (142f - stemHeight))
}

internal enum class Mark { LEAF, BRIGHT, BLOSSOM, FRUIT }

/** A circle-ish mark: a canopy cluster or tuft (LEAF/BRIGHT, radius [r]), a blossom or a fruit. */
internal data class Blob(val mark: Mark, val x: Float, val y: Float, val r: Float)

internal data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

internal data class Trunk(val height: Float, val baseHalf: Float, val topHalf: Float)

/**
 * A stage 5–9 tree. [clusters] and [marks] (tufts, blossoms, fruits carried from earlier stages)
 * always show; [details] are this stage's own, revealed in order as points come in.
 */
internal class TreeShape(
    val trunk: Trunk,
    val branches: List<Line>,
    val roots: Boolean,
    val clusters: List<Blob>,
    val marks: List<Blob>,
    val details: List<Blob>
) {
    /** Where the celebration's leaves rise from: the highest cluster. */
    val top: Blob get() = clusters.minBy { it.y }
}

/** Blossom backing disc and fruit radius, for bounds and canopy. */
internal const val BLOSSOM_R = 6.2f
internal const val FRUIT_R = 4.5f

private fun blob(mark: Mark, x: Number, y: Number, r: Number) = Blob(mark, x.toFloat(), y.toFloat(), r.toFloat())

private val TREE_BRANCHES = listOf(Line(56f, 104f, 32f, 70f), Line(56f, 98f, 82f, 66f), Line(56f, 86f, 44f, 50f))
private val TREE_CLUSTERS = listOf(
    blob(Mark.LEAF, 30, 58, 20), blob(Mark.BRIGHT, 82, 56, 20), blob(Mark.BRIGHT, 56, 58, 18),
    blob(Mark.LEAF, 56, 34, 28), blob(Mark.BRIGHT, 42, 24, 16), blob(Mark.LEAF, 72, 22, 16)
)
private val TREE_TUFTS = listOf(blob(Mark.LEAF, 14, 64, 8), blob(Mark.BRIGHT, 98, 62, 8), blob(Mark.LEAF, 24, 36, 8), blob(Mark.BRIGHT, 88, 34, 8))
private val BLOSSOMS = listOf(44 to 20, 68 to 16, 30 to 50, 84 to 48, 56 to 40, 40 to 64, 74 to 62, 56 to 14)
    .map { (x, y) -> blob(Mark.BLOSSOM, x, y, BLOSSOM_R) }
private val FRUITS = listOf(36 to 44, 76 to 40, 50 to 56, 64 to 30, 26 to 62, 88 to 58, 46 to 30, 70 to 50)
    .map { (x, y) -> blob(Mark.FRUIT, x, y, FRUIT_R) }
private val LATE_BLOSSOMS = listOf(20 to 40, 92 to 38, 48 to 8, 66 to 8, 30 to 30, 80 to 26)
    .map { (x, y) -> blob(Mark.BLOSSOM, x, y, BLOSSOM_R) }

/** The tree for [stage] with [details] of its own shown, or null for stages 0–4. */
internal fun treeShapeOf(stage: TreeStage, details: Int): TreeShape? {
    val shown = details.coerceIn(0, stage.maxDetails)
    return when (stage) {
        TreeStage.YOUNG_TREE -> TreeShape(
            Trunk(84f, 5f, 3f),
            listOf(Line(56f, 100f, 38f, 66f), Line(56f, 94f, 76f, 64f)),
            roots = false,
            clusters = listOf(blob(Mark.LEAF, 34, 56, 18), blob(Mark.BRIGHT, 78, 54, 18), blob(Mark.LEAF, 56, 36, 26), blob(Mark.BRIGHT, 64, 26, 14)),
            marks = emptyList(),
            details = listOf(22 to 70, 92 to 68, 44 to 18, 82 to 36).map { (x, y) -> blob(Mark.BRIGHT, x, y, 7) }.take(shown)
        )
        TreeStage.TREE -> TreeShape(Trunk(88f, 6f, 3.5f), TREE_BRANCHES, false, TREE_CLUSTERS, emptyList(), TREE_TUFTS.take(shown))
        TreeStage.BLOSSOMING -> TreeShape(Trunk(88f, 6f, 3.5f), TREE_BRANCHES, false, TREE_CLUSTERS, TREE_TUFTS, BLOSSOMS.take(shown))
        TreeStage.FRUITING -> TreeShape(Trunk(88f, 6f, 3.5f), TREE_BRANCHES, false, TREE_CLUSTERS, TREE_TUFTS + BLOSSOMS, FRUITS.take(shown))
        TreeStage.FULL_CANOPY -> TreeShape(
            Trunk(92f, 8f, 4f),
            TREE_BRANCHES,
            roots = true,
            clusters = TREE_CLUSTERS.map { it.copy(r = it.r + 3) } + listOf(blob(Mark.LEAF, 18, 44, 16), blob(Mark.BRIGHT, 94, 42, 16)),
            marks = TREE_TUFTS.map { it.copy(r = it.r + 1) } + BLOSSOMS + FRUITS,
            details = LATE_BLOSSOMS.take(shown)
        )
        else -> null
    }
}

/** Every (stage, details) state in growth order — what the invariant test walks. */
internal fun growthOrder(): List<Pair<TreeStage, Int>> =
    TreeStage.entries.flatMap { stage -> (0..stage.maxDetails).map { stage to it } }

/** The topmost reach of a blob: fruit stems poke up a little past the fruit. */
private fun Blob.topY(): Float = if (mark == Mark.FRUIT) y - 7f else y - r

private fun leafTipY(stemHeight: Float, position: Float, length: Float): Float =
    stemPoint(stemHeight, position).second - cos(LEAF_DEGREES * PI / 180).toFloat() * length

/** 142 minus the highest point any shape reaches, leaves included. */
internal fun treeHeight(stage: TreeStage, details: Int): Float {
    plantShapeOf(stage)?.let { plant ->
        val top = plant.leaves.minOf { (position, length) -> leafTipY(plant.stemHeight, position, length) }
        return 142f - minOf(top, 142f - plant.stemHeight)
    }
    val tree = treeShapeOf(stage, details)!!
    val top = (tree.clusters + tree.marks + tree.details).minOf { it.topY() }
    return 142f - minOf(top, 142f - tree.trunk.height)
}

/** Canopy: πr² over every round mark, 0.33·L² per leaf, plus the trunk (or stem) area. */
internal fun treeMass(stage: TreeStage, details: Int): Float {
    plantShapeOf(stage)?.let { plant ->
        val sides = if (plant.oneSided) 1 else 2
        return plant.stemStroke * plant.stemHeight + plant.leaves.sumOf { (_, l) -> 0.33 * l * l * sides }.toFloat()
    }
    val tree = treeShapeOf(stage, details)!!
    val round = (tree.clusters + tree.marks + tree.details).sumOf { PI * it.r * it.r }.toFloat()
    return round + tree.trunk.height * (tree.trunk.baseHalf + tree.trunk.topHalf)
}

/** Every shape's bounding box, for the stays-inside-the-canvas test: (left, top, right, bottom). */
internal fun treeBounds(stage: TreeStage, details: Int): List<List<Float>> {
    plantShapeOf(stage)?.let { plant ->
        return plant.leaves.map { (position, length) ->
            val (x, y) = stemPoint(plant.stemHeight, position)
            val dx = kotlin.math.sin(LEAF_DEGREES * PI / 180).toFloat() * length
            listOf(x - dx, leafTipY(plant.stemHeight, position, length), x + dx, y)
        }
    }
    val tree = treeShapeOf(stage, details)!!
    val blobs = (tree.clusters + tree.marks + tree.details).map { listOf(it.x - it.r, it.topY(), it.x + it.r, it.y + it.r) }
    val roots = if (tree.roots) listOf(listOf(40f, 140f, 72f, 146f)) else emptyList()
    return blobs + roots + listOf(listOf(56f - tree.trunk.baseHalf, 142f - tree.trunk.height, 56f + tree.trunk.baseHalf, 142f))
}
