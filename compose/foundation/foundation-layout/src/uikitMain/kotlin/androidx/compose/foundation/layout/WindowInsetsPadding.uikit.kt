/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.debugInspectorInfo

/**
 * Adds padding to accommodate the [safe drawing][WindowInsets.Companion.safeDrawing] insets.
 *
 * Any insets consumed by other insets padding modifiers or [consumedWindowInsets] on a parent layout
 * will be excluded from the padding. [WindowInsets.Companion.safeDrawing] will be
 * [consumed][consumedWindowInsets] for child layouts as well.
 *
 * For example, if a parent layout uses [statusBarsPadding], the area that the parent
 * pads for the status bars will not be padded again by this [safeDrawingPadding] modifier.
 *
 * When used, the [WindowInsets][android.view.WindowInsets] will be consumed.
 *
 * @sample androidx.compose.foundation.layout.samples.safeDrawingPaddingSample
 */
fun Modifier.safeDrawingPadding() =
    windowInsetsPadding(debugInspectorInfo { name = "safeDrawingPadding" }) { safeDrawing }

/**
 * Adds padding to accommodate the [system bars][WindowInsets.Companion.systemBars] insets.
 *
 * Any insets consumed by other insets padding modifiers or [consumedWindowInsets] on a parent layout
 * will be excluded from the padding. [WindowInsets.Companion.systemBars] will be
 * [consumed][consumedWindowInsets] for child layouts as well.
 *
 * For example, if a parent layout uses [statusBarsPadding], the
 * area that the parent layout pads for the status bars will not be padded again by this
 * [systemBarsPadding] modifier.
 *
 * When used, the [WindowInsets][android.view.WindowInsets] will be consumed.
 *
 * @sample androidx.compose.foundation.layout.samples.systemBarsPaddingSample
 */
fun Modifier.systemBarsPadding() =
    windowInsetsPadding(debugInspectorInfo { name = "systemBarsPadding" }) { systemBars }

/**
 * Adds padding to accommodate the [status bars][WindowInsets.Companion.statusBars] insets.
 *
 * Any insets consumed by other insets padding modifiers or [consumedWindowInsets] on a parent layout
 * will be excluded from the padding. [WindowInsets.Companion.statusBars] will be
 * [consumed][consumedWindowInsets] for child layouts as well.
 *
 * For example, if a parent layout uses [displayCutoutPadding], the
 * area that the parent layout pads for the status bars will not be padded again by this
 * [statusBarsPadding] modifier.
 *
 * When used, the [WindowInsets][android.view.WindowInsets] will be consumed.
 *
 * @sample androidx.compose.foundation.layout.samples.statusBarsAndNavigationBarsPaddingSample
 */
fun Modifier.statusBarsPadding() =
    windowInsetsPadding(debugInspectorInfo { name = "statusBarsPadding" }) { statusBars }

/**
 * Adds padding to accommodate the [ime][WindowInsets.Companion.ime] insets.
 *
 * Any insets consumed by other insets padding modifiers or [consumedWindowInsets] on a parent layout
 * will be excluded from the padding. [WindowInsets.Companion.ime] will be
 * [consumed][consumedWindowInsets] for child layouts as well.
 *
 * For example, if a parent layout uses [navigationBarsPadding],
 * the area that the parent layout pads for the status bars will not be padded again by this
 * [imePadding] modifier.
 *
 * When used, the [WindowInsets][android.view.WindowInsets] will be consumed.
 *
 * @sample androidx.compose.foundation.layout.samples.imePaddingSample
 */
fun Modifier.imePadding() =
    windowInsetsPadding(debugInspectorInfo { name = "imePadding" }) { ime }

/**
 * Adds padding to accommodate the [navigation bars][WindowInsets.Companion.navigationBars] insets.
 *
 * Any insets consumed by other insets padding modifiers or [consumedWindowInsets] on a parent layout
 * will be excluded from the padding. [WindowInsets.Companion.navigationBars] will be
 * [consumed][consumedWindowInsets] for child layouts as well.
 *
 * For example, if a parent layout uses [systemBarsPadding], the
 * area that the parent layout pads for the status bars will not be padded again by this
 * [navigationBarsPadding] modifier.
 *
 * When used, the [WindowInsets][android.view.WindowInsets] will be consumed.
 *
 * @sample androidx.compose.foundation.layout.samples.statusBarsAndNavigationBarsPaddingSample
 */
fun Modifier.navigationBarsPadding() =
    windowInsetsPadding(debugInspectorInfo { name = "navigationBarsPadding" }) { navigationBars }

@Suppress("NOTHING_TO_INLINE", "ModifierInspectorInfo")
@Stable
private inline fun Modifier.windowInsetsPadding(
    noinline inspectorInfo: InspectorInfo.() -> Unit,
    crossinline insetsCalculation: WindowInsetsHolder.() -> WindowInsets
): Modifier = composed(inspectorInfo) {
    val composeInsets =  WindowInsetsHolder.current()
    remember(composeInsets) {
        val insets = composeInsets.insetsCalculation()
        InsetsPaddingModifier(insets)
    }
}
