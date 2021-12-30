/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.foundation.lazy.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Measures and calculates the positions for the currently visible items. The result is produced
 * as a [LazyGridMeasureResult] which contains all the calculations.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun measureLazyGrid(
    itemsCount: Int,
    lineProvider: LazyMeasuredLineProvider,
    mainAxisMaxSize: Int,
    beforeContentPadding: Int,
    afterContentPadding: Int,
    firstVisibleLineIndex: LineIndex,
    firstVisibleLineScrollOffset: Int,
    scrollToBeConsumed: Float,
    constraints: Constraints,
    isVertical: Boolean,
    verticalArrangement: Arrangement.Vertical?,
    horizontalArrangement: Arrangement.Horizontal?,
    reverseLayout: Boolean,
    density: Density,
    layoutDirection: LayoutDirection,
    // placementAnimator: LazyListItemPlacementAnimator,
    layout: (Int, Int, Placeable.PlacementScope.() -> Unit) -> MeasureResult
): LazyGridMeasureResult {
    require(beforeContentPadding >= 0)
    require(afterContentPadding >= 0)
    if (itemsCount <= 0) {
        // empty data set. reset the current scroll and report zero size
        return LazyGridMeasureResult(
            firstVisibleLine = null,
            firstVisibleLineScrollOffset = 0,
            canScrollForward = false,
            consumedScroll = 0f,
            measureResult = layout(constraints.minWidth, constraints.minHeight) {},
            visibleItemsInfo = emptyList(),
            viewportStartOffset = -beforeContentPadding,
            viewportEndOffset = afterContentPadding,
            totalItemsCount = 0
        )
    } else {
        var currentFirstLineIndex = firstVisibleLineIndex
        var currentFirstLineScrollOffset = firstVisibleLineScrollOffset

        // represents the real amount of scroll we applied as a result of this measure pass.
        var scrollDelta = scrollToBeConsumed.roundToInt()

        // applying the whole requested scroll offset. we will figure out if we can't consume
        // all of it later
        currentFirstLineScrollOffset -= scrollDelta

        // if the current scroll offset is less than minimally possible
        if (currentFirstLineIndex == LineIndex(0) && currentFirstLineScrollOffset < 0) {
            scrollDelta += currentFirstLineScrollOffset
            currentFirstLineScrollOffset = 0
        }

        // this will contain all the MeasuredItems representing the visible lines
        val visibleLines = mutableListOf<LazyMeasuredLine>()

        // include the start padding so we compose items in the padding area. before starting
        // scrolling forward we would remove it back
        currentFirstLineScrollOffset -= beforeContentPadding

        // define min and max offsets (min offset currently includes beforeContentPadding)
        val minOffset = -beforeContentPadding
        val maxOffset = mainAxisMaxSize

        // we had scrolled backward or we compose items in the start padding area, which means
        // items before current firstLineScrollOffset should be visible. compose them and update
        // firstLineScrollOffset
        while (currentFirstLineScrollOffset < 0 && currentFirstLineIndex > LineIndex(0)) {
            val previous = LineIndex(currentFirstLineIndex.value - 1)
            val measuredLine = lineProvider.getAndMeasure(previous)
            visibleLines.add(0, measuredLine)
            currentFirstLineScrollOffset += measuredLine.sizeWithSpacings
            currentFirstLineIndex = previous
        }
        // if we were scrolled backward, but there were not enough lines before. this means
        // not the whole scroll was consumed
        if (currentFirstLineScrollOffset < minOffset) {
            scrollDelta += currentFirstLineScrollOffset
            currentFirstLineScrollOffset = minOffset
        }

        // neutralize previously added start padding as we stopped filling the before content padding
        currentFirstLineScrollOffset += beforeContentPadding

        var index = currentFirstLineIndex
        val maxMainAxis = maxOffset + afterContentPadding
        var mainAxisUsed = -currentFirstLineScrollOffset

        // first we need to skip lines we already composed while composing backward
        visibleLines.fastForEach {
            index++
            mainAxisUsed += it.sizeWithSpacings
        }

        // then composing visible lines forward until we fill the whole viewport
        while (mainAxisUsed <= maxMainAxis) {
            val measuredLine = lineProvider.getAndMeasure(index)
            if (measuredLine.isEmpty()) {
                --index
                break
            }

            mainAxisUsed += measuredLine.sizeWithSpacings
            if (mainAxisUsed <= minOffset) {
                // this line is offscreen and will not be placed. advance firstVisibleLineIndex
                currentFirstLineIndex = index + 1
                currentFirstLineScrollOffset -= measuredLine.sizeWithSpacings
            } else {
                visibleLines.add(measuredLine)
            }
            index++
        }

        // we didn't fill the whole viewport with lines starting from firstVisibleLineIndex.
        // lets try to scroll back if we have enough lines before firstVisibleLineIndex.
        if (mainAxisUsed < maxOffset) {
            val toScrollBack = maxOffset - mainAxisUsed
            currentFirstLineScrollOffset -= toScrollBack
            mainAxisUsed += toScrollBack
            while (currentFirstLineScrollOffset < 0 && currentFirstLineIndex > LineIndex(0)) {
                val previousIndex = LineIndex(currentFirstLineIndex.value - 1)
                val measuredLine = lineProvider.getAndMeasure(previousIndex)
                visibleLines.add(0, measuredLine)
                currentFirstLineScrollOffset += measuredLine.sizeWithSpacings
                currentFirstLineIndex = previousIndex
            }
            scrollDelta += toScrollBack
            if (currentFirstLineScrollOffset < 0) {
                scrollDelta += currentFirstLineScrollOffset
                mainAxisUsed += currentFirstLineScrollOffset
                currentFirstLineScrollOffset = 0
            }
        }

        // report the amount of pixels we consumed. scrollDelta can be smaller than
        // scrollToBeConsumed if there were not enough lines to fill the offered space or it
        // can be larger if lines were resized, or if, for example, we were previously
        // displaying the line 15, but now we have only 10 lines in total in the data set.
        val consumedScroll = if (scrollToBeConsumed.roundToInt().sign == scrollDelta.sign &&
            abs(scrollToBeConsumed.roundToInt()) >= abs(scrollDelta)
        ) {
            scrollDelta.toFloat()
        } else {
            scrollToBeConsumed
        }

        // the initial offset for lines from visibleLines list
        val visibleLinesScrollOffset = -currentFirstLineScrollOffset
        var firstLine = visibleLines.firstOrNull()

        // even if we compose lines to fill before content padding we should ignore lines fully
        // located there for the state's scroll position calculation (first line + first offset)
        if (beforeContentPadding > 0) {
            for (i in visibleLines.indices) {
                val size = visibleLines[i].sizeWithSpacings
                if (size <= currentFirstLineScrollOffset && i != visibleLines.lastIndex) {
                    currentFirstLineScrollOffset -= size
                    firstLine = visibleLines[i + 1]
                } else {
                    break
                }
            }
        }

        val layoutWidth =
            if (isVertical) constraints.maxWidth else constraints.constrainWidth(mainAxisUsed)
        val layoutHeight =
            if (isVertical) constraints.constrainHeight(mainAxisUsed) else constraints.maxHeight

        val positionedItems = calculateItemsOffsets(
            lines = visibleLines,
            layoutWidth = layoutWidth,
            layoutHeight = layoutHeight,
            usedMainAxisSize = mainAxisUsed,
            firstLineScrollOffset = visibleLinesScrollOffset,
            isVertical = isVertical,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            reverseLayout = reverseLayout,
            density = density,
            layoutDirection = layoutDirection
        )

        // placementAnimator.onMeasured(
        //     consumedScroll = consumedScroll.toInt(),
        //     layoutWidth = layoutWidth,
        //     layoutHeight = layoutHeight,
        //     reverseLayout = reverseLayout,
        //     positionedItems = positionedItems,
        //     lineProvider = lineProvider
        // )

        val maximumVisibleOffset = minOf(mainAxisUsed, mainAxisMaxSize) + afterContentPadding

        return LazyGridMeasureResult(
            firstVisibleLine = firstLine,
            firstVisibleLineScrollOffset = currentFirstLineScrollOffset,
            canScrollForward = mainAxisUsed > maxOffset,
            consumedScroll = consumedScroll,
            measureResult = layout(layoutWidth, layoutHeight) {
                positionedItems.fastForEach { it.place(this) }
            },
            viewportStartOffset = -beforeContentPadding,
            viewportEndOffset = maximumVisibleOffset,
            visibleItemsInfo = positionedItems,
            totalItemsCount = itemsCount,
        )
    }
}

/**
 * Calculates [LazyMeasuredLine]s offsets.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun calculateItemsOffsets(
    lines: List<LazyMeasuredLine>,
    layoutWidth: Int,
    layoutHeight: Int,
    usedMainAxisSize: Int,
    firstLineScrollOffset: Int,
    isVertical: Boolean,
    verticalArrangement: Arrangement.Vertical?,
    horizontalArrangement: Arrangement.Horizontal?,
    reverseLayout: Boolean,
    density: Density,
    layoutDirection: LayoutDirection
): MutableList<LazyGridPositionedItem> {
    val mainAxisLayoutSize = if (isVertical) layoutHeight else layoutWidth
    val hasSpareSpace = usedMainAxisSize < mainAxisLayoutSize
    if (hasSpareSpace) {
        check(firstLineScrollOffset == 0)
    }

    val positionedItems = ArrayList<LazyGridPositionedItem>(lines.size)

    if (hasSpareSpace) {
        val linesCount = lines.size
        val sizes = IntArray(linesCount) { index ->
            val reverseLayoutAwareIndex = if (!reverseLayout) index else linesCount - index - 1
            lines[reverseLayoutAwareIndex].mainAxisSize
        }
        val offsets = IntArray(linesCount) { 0 }
        if (isVertical) {
            with(requireNotNull(verticalArrangement)) {
                density.arrange(mainAxisLayoutSize, sizes, offsets)
            }
        } else {
            with(requireNotNull(horizontalArrangement)) {
                density.arrange(mainAxisLayoutSize, sizes, layoutDirection, offsets)
            }
        }
        offsets.forEachIndexed { index, absoluteOffset ->
            val reverseLayoutAwareIndex = if (!reverseLayout) index else linesCount - index - 1
            val line = lines[reverseLayoutAwareIndex]
            val relativeOffset = if (reverseLayout) {
                mainAxisLayoutSize - absoluteOffset - line.mainAxisSize
            } else {
                absoluteOffset
            }
            val addIndex = if (reverseLayout) 0 else positionedItems.size
            positionedItems.addAll(
                addIndex,
                line.position(relativeOffset, layoutWidth, layoutHeight)
            )
        }
    } else {
        var currentMainAxis = firstLineScrollOffset
        lines.fastForEach {
            positionedItems.addAll(it.position(currentMainAxis, layoutWidth, layoutHeight))
            currentMainAxis += it.sizeWithSpacings
        }
    }
    return positionedItems
}