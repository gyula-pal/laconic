package cc.gyula.wave.core

/**
 * The boxes layout for home: the pinned apps grouped into boxes that tile one rectangle, like a tiling
 * window manager. A box is made by splitting another in two, so gaps and overlaps cannot happen. Which
 * apps are on home is still decided by the pins; the tree only says which box each pin sits in.
 */
sealed interface BoxTree

/**
 * A box holding apps, by the app's key (the app module's `AppEntry.key`), in order. [id] stays with the box across edits.
 * [name] is an optional label shown in the box's corner; empty means none.
 */
data class AppBox(val id: Int, val apps: List<String>, val name: String = "") : BoxTree

/** [sideBySide]: the children sit left | right, otherwise top / bottom. [ratio] is the first child's share, 0..1. */
data class BoxSplit(val sideBySide: Boolean, val ratio: Float, val first: BoxTree, val second: BoxTree) : BoxTree

data class BoxArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The line between the two halves of a split, to draw and to drag. [path] is the route from the root
 * (false = first child, true = second); [parent] is the split's whole area, which bounds how far it can move.
 * [line] has no width (or no height, for a line across).
 */
data class BoxDivider(val path: List<Boolean>, val sideBySide: Boolean, val line: BoxArea, val parent: BoxArea)

/** Every box, in reading order. */
fun BoxTree.boxes(): List<AppBox> = when (this) {
    is AppBox -> listOf(this)
    is BoxSplit -> first.boxes() + second.boxes()
}

/** Where a split's cut falls, and the areas of its two halves. */
private fun BoxSplit.halves(area: BoxArea): Pair<BoxArea, BoxArea> =
    if (sideBySide) {
        val x = area.left + (area.right - area.left) * ratio
        area.copy(right = x) to area.copy(left = x)
    } else {
        val y = area.top + (area.bottom - area.top) * ratio
        area.copy(bottom = y) to area.copy(top = y)
    }

/** Each box with its share of [area]; together they cover it exactly. */
fun layoutBoxes(tree: BoxTree, area: BoxArea): List<Pair<AppBox, BoxArea>> = when (tree) {
    is AppBox -> listOf(tree to area)
    is BoxSplit -> tree.halves(area).let { (a, b) -> layoutBoxes(tree.first, a) + layoutBoxes(tree.second, b) }
}

/** The lines between boxes, never the outer edge. */
fun dividers(tree: BoxTree, area: BoxArea, path: List<Boolean> = emptyList()): List<BoxDivider> = when (tree) {
    is AppBox -> emptyList()
    is BoxSplit -> {
        val (a, b) = tree.halves(area)
        val line = if (tree.sideBySide) BoxArea(b.left, area.top, b.left, area.bottom) else BoxArea(area.left, b.top, area.right, b.top)
        listOf(BoxDivider(path, tree.sideBySide, line, area)) +
            dividers(tree.first, a, path + false) + dividers(tree.second, b, path + true)
    }
}

/** The split at [path] cut at [ratio], kept at least [minFraction] from either side so no box can be dragged away. */
fun withRatio(tree: BoxTree, path: List<Boolean>, ratio: Float, minFraction: Float): BoxTree = when {
    tree !is BoxSplit -> tree
    path.isEmpty() -> tree.copy(ratio = ratio.coerceIn(minFraction, 1 - minFraction))
    path.first() -> tree.copy(second = withRatio(tree.second, path.drop(1), ratio, minFraction))
    else -> tree.copy(first = withRatio(tree.first, path.drop(1), ratio, minFraction))
}

/**
 * The least room [tree] needs along one axis ([sideBySide] = widths, else heights) so every box in it keeps
 * [minBox]: boxes side by side along that axis add up, boxes across it need only the widest of them.
 */
fun minExtent(tree: BoxTree, sideBySide: Boolean, minBox: Float): Float = when (tree) {
    is AppBox -> minBox
    is BoxSplit -> {
        val a = minExtent(tree.first, sideBySide, minBox)
        val b = minExtent(tree.second, sideBySide, minBox)
        if (tree.sideBySide == sideBySide) a + b else maxOf(a, b)
    }
}

/** The split at [path] (false = first, true = second), or null if the path doesn't lead to one. */
fun splitAt(tree: BoxTree, path: List<Boolean>): BoxSplit? = when {
    tree !is BoxSplit -> null
    path.isEmpty() -> tree
    else -> splitAt(if (path.first()) tree.second else tree.first, path.drop(1))
}

/**
 * Where [split]'s line may go in an area [extent] long along its axis: far enough from each side that every box
 * on that side keeps [minBox] — not only the two boxes either side of the line. Dragging an outer line used to
 * squeeze the boxes behind an inner one to a sliver (seen on the phone). Too little room: the middle.
 */
fun ratioRange(split: BoxSplit, extent: Float, minBox: Float): ClosedFloatingPointRange<Float> {
    val lo = minExtent(split.first, split.sideBySide, minBox) / extent
    val hi = 1 - minExtent(split.second, split.sideBySide, minBox) / extent
    return if (lo <= hi) lo..hi else ((lo + hi) / 2).let { it..it }
}

/**
 * [tree] laid into [area] with every line moved, if it must, so every box keeps [minBox]. Layouts saved before
 * lines respected the boxes behind them can hold a sliver of a box; this opens it up again when drawn.
 */
fun fitted(tree: BoxTree, area: BoxArea, minBox: Float): BoxTree = when (tree) {
    is AppBox -> tree
    is BoxSplit -> {
        val extent = if (tree.sideBySide) area.right - area.left else area.bottom - area.top
        val ratio = if (extent > 0f) tree.ratio.coerceIn(ratioRange(tree, extent, minBox)) else tree.ratio
        val split = tree.copy(ratio = ratio)
        val (a, b) = split.halves(area)
        split.copy(first = fitted(tree.first, a, minBox), second = fitted(tree.second, b, minBox))
    }
}

/** Box [boxId] cut in two, its apps staying in the first half. The new box is an empty one with a fresh id. */
fun splitBox(tree: BoxTree, boxId: Int, sideBySide: Boolean): BoxTree {
    val newId = tree.boxes().maxOf { it.id } + 1
    fun go(t: BoxTree): BoxTree = when {
        t is AppBox && t.id == boxId -> BoxSplit(sideBySide, 0.5f, t, AppBox(newId, emptyList()))
        t is BoxSplit -> t.copy(first = go(t.first), second = go(t.second))
        else -> t
    }
    return go(tree)
}

/** Box [boxId] labelled [name], trimmed; a blank name takes the label away. */
fun renameBox(tree: BoxTree, boxId: Int, name: String): BoxTree = when (tree) {
    is AppBox -> if (tree.id == boxId) tree.copy(name = name.trim()) else tree
    is BoxSplit -> tree.copy(first = renameBox(tree.first, boxId, name), second = renameBox(tree.second, boxId, name))
}

/** Box [boxId] gone: its sibling takes the space and the removed box's apps join the end of the sibling's first box. */
fun removeBox(tree: BoxTree, boxId: Int): BoxTree {
    val gone = tree.boxes().find { it.id == boxId } ?: return tree
    if (tree is AppBox) return tree
    fun go(t: BoxTree): BoxTree {
        if (t !is BoxSplit) return t
        val kept = when {
            (t.first as? AppBox)?.id == boxId -> t.second
            (t.second as? AppBox)?.id == boxId -> t.first
            else -> return t.copy(first = go(t.first), second = go(t.second))
        }
        val receiver = kept.boxes().first().id
        return appendTo(kept, receiver, gone.apps)
    }
    return go(tree)
}

private fun appendTo(tree: BoxTree, boxId: Int, keys: List<String>): BoxTree = when (tree) {
    is AppBox -> if (tree.id == boxId) tree.copy(apps = tree.apps + keys) else tree
    is BoxSplit -> tree.copy(first = appendTo(tree.first, boxId, keys), second = appendTo(tree.second, boxId, keys))
}

/** [key] out of its box and into [toBoxId], at [index] (counted after it has left) or at the end. */
fun moveApp(tree: BoxTree, key: String, toBoxId: Int, index: Int? = null): BoxTree {
    if (tree.boxes().none { it.id == toBoxId }) return tree
    fun go(t: BoxTree): BoxTree = when (t) {
        is BoxSplit -> t.copy(first = go(t.first), second = go(t.second))
        is AppBox -> {
            val rest = t.apps - key
            if (t.id == toBoxId) t.copy(apps = rest.toMutableList().apply { add((index ?: rest.size).coerceIn(0, rest.size), key) })
            else t.copy(apps = rest)
        }
    }
    return go(tree)
}

/**
 * Box [boxId] holding exactly [keys]: the apps it already had that are still wanted keep their order, new ones
 * follow in the order given, and each key leaves any other box. An unknown box id changes nothing.
 */
fun setBoxApps(tree: BoxTree, boxId: Int, keys: List<String>): BoxTree {
    if (tree.boxes().none { it.id == boxId }) return tree
    val wanted = keys.toSet()
    fun go(t: BoxTree): BoxTree = when (t) {
        is BoxSplit -> t.copy(first = go(t.first), second = go(t.second))
        is AppBox -> if (t.id == boxId) {
            val kept = t.apps.filter { it in wanted }.distinct()
            t.copy(apps = kept + keys.distinct().filter { it !in kept })
        } else t.copy(apps = t.apps.filter { it !in wanted })
    }
    return go(tree)
}

/**
 * The pins after box [boxId] is set to [keys]: apps that left the box ([before] but not in [keys]) are unpinned,
 * keys not pinned yet are pinned at the end. The rest keep their place.
 */
fun pinsForBoxApps(pins: List<String>, before: List<String>, keys: List<String>): List<String> {
    val gone = before.toSet() - keys.toSet()
    return pins.filter { it !in gone } + keys.distinct().filter { it !in pins }
}

/**
 * [tree] made to match the [pins]: apps no longer pinned drop out, repeats keep their first place, and
 * pins the tree has not seen join the first box in pin order. With nothing saved, one box holds them all.
 */
fun reconciled(tree: BoxTree?, pins: List<String>): BoxTree {
    if (tree == null) return AppBox(0, pins.distinct())
    val wanted = pins.toSet()
    val seen = HashSet<String>()
    fun go(t: BoxTree): BoxTree = when (t) {
        is BoxSplit -> t.copy(first = go(t.first), second = go(t.second))
        is AppBox -> t.copy(apps = t.apps.filter { it in wanted && seen.add(it) })
    }
    val cleaned = go(tree)
    val missing = pins.filter { it !in seen }.distinct()
    return if (missing.isEmpty()) cleaned else appendTo(cleaned, cleaned.boxes().first().id, missing)
}
