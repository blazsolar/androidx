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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.TestOnly
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.LocalLayerContainer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFTimeInterval
import platform.Foundation.NSLog
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSTimeInterval
import platform.Foundation.NSValue
import platform.QuartzCore.CACurrentMediaTime
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAFrameRateRangeMake
import platform.UIKit.CGRectValue
import platform.UIKit.UIApplication
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.safeAreaInsets
import platform.darwin.NSObject

/**
 * [WindowInsets] provided by the Android framework. These can be used in
 * [rememberWindowInsetsConnection] to control the insets.
 */
@Stable
class UiKitWindowInsets(
    internal val type: Int,
    private val name: String
) : WindowInsets {
    //    internal var insets by mutableStateOf(AndroidXInsets.NONE)
    internal var insets by mutableStateOf(InsetsValues(0, 0, 0, 0))

    /**
     * Returns whether the insets are visible, irrespective of whether or not they
     * intersect with the Window.
     */
    var isVisible by mutableStateOf(true)
        private set

    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int {
        return with(density) { insets.left.dp.roundToPx() }
    }

    override fun getTop(density: Density): Int {
        return with(density) { insets.top.dp.roundToPx() }
    }

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int {
        return with(density) { insets.right.dp.roundToPx() }
    }

    override fun getBottom(density: Density): Int {
        return with(density) { insets.bottom.dp.roundToPx() }
    }

    internal fun update(view: UIView, typeMask: Int) {
        UIApplication.sharedApplication.keyWindow?.safeAreaInsets
            ?.useContents {
                InsetsValues(
                    top = top.toInt(),
                    left = left.toInt(),
                    right = right.toInt(),
                    bottom = bottom.toInt(),
                )
            }?.also { insets = it }

//        if (typeMask == 0 || typeMask and type != 0) {
//            insets = InsetsValues(
//                top = uiApplication.statusBarFrame.useContents { size.height.toInt() },
//                left = 0,
//                right = 0,
//                bottom = 0,
//            )

        //            insets = windowInsetsCompat.getInsets(type)
//            isVisible = windowInsetsCompat.isVisible(type)
//        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiKitWindowInsets) return false

        return type == other.type
    }

    override fun hashCode(): Int {
        return type
    }

    override fun toString(): String {
        return "$name(${insets.left}, ${insets.top}, ${insets.right}, ${insets.bottom})"
    }
}

///**
// * Indicates whether access to [WindowInsets] within the [content][ComposeView.setContent]
// * should consume the Android  [android.view.WindowInsets]. The default value is `true`, meaning
// * that access to [WindowInsets.Companion] will consume the Android WindowInsets.
// *
// * This property should be set prior to first composition.
// */
//var ComposeView.consumeWindowInsets: Boolean
//    get() = getTag(R.id.consume_window_insets_tag) as? Boolean ?: true
//    set(value) {
//        setTag(R.id.consume_window_insets_tag, value)
//    }
//
///**
// * For the [WindowInsetsCompat.Type.captionBar].
// */
//val WindowInsets.Companion.captionBar: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().captionBar
//
///**
// * For the [WindowInsetsCompat.Type.displayCutout]. This insets represents the area that the
// * display cutout (e.g. for camera) is and important content should be excluded from.
// */
//val WindowInsets.Companion.displayCutout: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().displayCutout

/**
 * For the [WindowInsetsCompat.Type.ime]. On API level 23 (M) and above, the soft keyboard can be
 * detected and [ime] will update when it shows. On API 30 (R) and above, the [ime] insets will
 * animate synchronously with the actual IME animation.
 *
 * Developers should set `android:windowSoftInputMode="adjustResize"` in their
 * `AndroidManifest.xml` file and call `WindowCompat.setDecorFitsSystemWindows(window, false)`
 * in their [android.app.Activity.onCreate].
 */
val WindowInsets.Companion.ime: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().ime

///**
// * For the [WindowInsetsCompat.Type.mandatorySystemGestures]. These insets represents the
// * space where system gestures have priority over application gestures.
// */
//val WindowInsets.Companion.mandatorySystemGestures: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().mandatorySystemGestures

/**
 * For the [WindowInsetsCompat.Type.navigationBars]. These insets represent where
 * system UI places navigation bars. Interactive UI should avoid the navigation bars
 * area.
 */
val WindowInsets.Companion.navigationBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().navigationBars
//
///**
// * For the [WindowInsetsCompat.Type.statusBars].
// */
//val WindowInsets.Companion.statusBars: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().statusBars

/**
 * For the [WindowInsetsCompat.Type.systemBars].
 */
val WindowInsets.Companion.systemBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().systemBars

///**
// * For the [WindowInsetsCompat.Type.systemGestures].
// */
//val WindowInsets.Companion.systemGestures: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().systemGestures
//
///**
// * For the [WindowInsetsCompat.Type.tappableElement].
// */
//val WindowInsets.Companion.tappableElement: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().tappableElement
//
///**
// * The insets for the curved areas in a waterfall display.
// */
//val WindowInsets.Companion.waterfall: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().waterfall
//
///**
// * The insets that include areas where content may be covered by other drawn content.
// * This includes all [system bars][systemBars], [display cutout][displayCutout], and
// * [soft keyboard][ime].
// */
//val WindowInsets.Companion.safeDrawing: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().safeDrawing
//
///**
// * The insets that include areas where gestures may be confused with other input,
// * including [system gestures][systemGestures],
// * [mandatory system gestures][mandatorySystemGestures],
// * [rounded display areas][waterfall], and [tappable areas][tappableElement].
// */
//val WindowInsets.Companion.safeGestures: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().safeGestures
//
///**
// * The insets that include all areas that may be drawn over or have gesture confusion,
// * including everything in [safeDrawing] and [safeGestures].
// */
//val WindowInsets.Companion.safeContent: WindowInsets
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().safeContent
//
///**
// * The insets that the [WindowInsetsCompat.Type.captionBar] will consume if shown.
// * If it cannot be shown then this will be empty.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.captionBarIgnoringVisibility: WindowInsets
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().captionBarIgnoringVisibility
//
///**
// * The insets that [WindowInsetsCompat.Type.navigationBars] will consume if shown.
// * These insets represent where system UI places navigation bars. Interactive UI should
// * avoid the navigation bars area. If navigation bars cannot be shown, then this will be
// * empty.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.navigationBarsIgnoringVisibility: WindowInsets
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().navigationBarsIgnoringVisibility
//
///**
// * The insets that [WindowInsetsCompat.Type.statusBars] will consume if shown.
// * If the status bar can never be shown, then this will be empty.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.statusBarsIgnoringVisibility: WindowInsets
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().statusBarsIgnoringVisibility
//
///**
// * The insets that [WindowInsetsCompat.Type.systemBars] will consume if shown.
// *
// * If system bars can never be shown, then this will be empty.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.systemBarsIgnoringVisibility: WindowInsets
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().systemBarsIgnoringVisibility
//
///**
// * The insets that [WindowInsetsCompat.Type.tappableElement] will consume if active.
// *
// * If there are never tappable elements then this is empty.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.tappableElementIgnoringVisibility: WindowInsets
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().tappableElementIgnoringVisibility
//
///**
// * `true` when the [caption bar][captionBar] is being displayed, irrespective of
// * whether it intersects with the Window.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.isCaptionBarVisible: Boolean
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().captionBar.isVisible

/**
 * `true` when the [soft keyboard][ime] is being displayed, irrespective of
 * whether it intersects with the Window.
 */
@ExperimentalLayoutApi
val WindowInsets.Companion.isImeVisible: Boolean
    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
    @ExperimentalLayoutApi
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().ime.isVisible

///**
// * `true` when the [statusBars] are being displayed, irrespective of
// * whether they intersects with the Window.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.areStatusBarsVisible: Boolean
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().statusBars.isVisible
//
///**
// * `true` when the [navigationBars] are being displayed, irrespective of
// * whether they intersects with the Window.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.areNavigationBarsVisible: Boolean
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().navigationBars.isVisible
//
///**
// * `true` when the [systemBars] are being displayed, irrespective of
// * whether they intersects with the Window.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.areSystemBarsVisible: Boolean
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().systemBars.isVisible
///**
// * `true` when the [tappableElement] is being displayed, irrespective of
// * whether they intersects with the Window.
// */
//@ExperimentalLayoutApi
//val WindowInsets.Companion.isTappableElementVisible: Boolean
//    @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
//    @ExperimentalLayoutApi
//    @Composable
//    @NonRestartableComposable
//    get() = WindowInsetsHolder.current().tappableElement.isVisible
//
///**
// * The insets for various values in the current window.
// */
internal class WindowInsetsHolder private constructor(val view: UIView?) {
    //    val captionBar =
//        systemInsets(insets, WindowInsetsCompat.Type.captionBar(), "captionBar")
//    val displayCutout =
//        systemInsets(insets, WindowInsetsCompat.Type.displayCutout(), "displayCutout")
//    val ime = systemInsets(view, 1 shl 3, "ime")
    val ime = UiKitWindowInsets(1 shl 3, "ime")

    //    val mandatorySystemGestures = systemInsets(
//        insets,
//        WindowInsetsCompat.Type.mandatorySystemGestures(),
//        "mandatorySystemGestures"
//    )
    val navigationBars =
        systemInsets(view, 1 shl 1, "navigationBars")
//    val statusBars = systemInsets(application, WindowInsetsCompat.Type.statusBars(), "statusBars")
//    val statusBars = systemInsets(view, 1, "statusBars")

    val systemBars =
        systemInsets(view, 1 or 1 shl 1 or 1 shl 2, "systemBars")

    //    val systemGestures =
//        systemInsets(insets, WindowInsetsCompat.Type.systemGestures(), "systemGestures")
//    val tappableElement =
//        systemInsets(insets, WindowInsetsCompat.Type.tappableElement(), "tappableElement")
//    val waterfall =
//        ValueInsets(insets?.displayCutout?.waterfallInsets ?: AndroidXInsets.NONE, "waterfall")
//    val safeDrawing =
//        systemBars.union(ime).union(displayCutout)
//    val safeGestures: WindowInsets =
//        tappableElement.union(mandatorySystemGestures).union(systemGestures).union(waterfall)
//    val safeContent: WindowInsets = safeDrawing.union(safeGestures)
//
//    val captionBarIgnoringVisibility = valueInsetsIgnoringVisibility(
//        insets,
//        WindowInsetsCompat.Type.captionBar(),
//        "captionBarIgnoringVisibility"
//    )
//    val navigationBarsIgnoringVisibility = valueInsetsIgnoringVisibility(
//        insets, WindowInsetsCompat.Type.navigationBars(), "navigationBarsIgnoringVisibility"
//    )
//    val statusBarsIgnoringVisibility = valueInsetsIgnoringVisibility(
//        insets,
//        WindowInsetsCompat.Type.statusBars(),
//        "statusBarsIgnoringVisibility"
//    )
//    val systemBarsIgnoringVisibility = valueInsetsIgnoringVisibility(
//        insets,
//        WindowInsetsCompat.Type.systemBars(),
//        "systemBarsIgnoringVisibility"
//    )
//    val tappableElementIgnoringVisibility = valueInsetsIgnoringVisibility(
//        insets,
//        WindowInsetsCompat.Type.tappableElement(),
//        "tappableElementIgnoringVisibility"
//    )
//
//    /**
//     * `true` unless the `ComposeView` [ComposeView.consumeWindowInsets] is set to `false`.
//     */
//    val consumes = (view.parent as? View)?.getTag(R.id.consume_window_insets_tag)
//        as? Boolean ?: true

    /**
     * The number of accesses to [WindowInsetsHolder]. When this reaches
     * zero, the listeners are removed. When it increases to 1, the listeners are added.
     */
    private var accessCount = 0

    private val keyboardVisibilityListener = KeyboardVisibilityListener(this)

    /**
     * A usage of [WindowInsetsHolder.current] was added. We must track so that when the
     * first one is added, listeners are set and when the last is removed, the listeners
     * are removed.
     */
    fun incrementAccessors() {
        if (accessCount == 0) {
            // add listeners
            NSNotificationCenter.defaultCenter.addObserver(
                observer = keyboardVisibilityListener,
                selector = NSSelectorFromString("keyboardWillShow:"),
                name = platform.UIKit.UIKeyboardWillShowNotification,
                `object` = null
            )
            NSNotificationCenter.defaultCenter.addObserver(
                observer = keyboardVisibilityListener,
                selector = NSSelectorFromString("keyboardWillHide:"),
                name = platform.UIKit.UIKeyboardWillHideNotification,
                `object` = null
            )
        }
        accessCount++
    }

    /**
     * A usage of [WindowInsetsHolder.current] was removed. We must track so that when the
     * first one is added, listeners are set and when the last is removed, the listeners
     * are removed.
     */
    fun decrementAccessors() {
        accessCount--
        if (accessCount == 0) {
            // remove listeners
            NSNotificationCenter.defaultCenter.removeObserver(
                observer = keyboardVisibilityListener,
                name = platform.UIKit.UIKeyboardWillShowNotification,
                `object` = null
            )
            NSNotificationCenter.defaultCenter.removeObserver(
                observer = keyboardVisibilityListener,
                name = platform.UIKit.UIKeyboardWillHideNotification,
                `object` = null
            )
        }
    }

//    /**
//     * Updates the WindowInsets values and notifies changes.
//     */
//    fun update(windowInsets: WindowInsetsCompat, types: Int = 0) {
//        val insets = if (testInsets) {
//            // WindowInsetsCompat erases insets that aren't part of the device.
//            // For example, if there is no navigation bar because of hardware keys,
//            // the bottom navigation bar will be removed. By using the constructor
//            // that doesn't accept a View, it doesn't remove the insets that aren't
//            // possible. This is important for testing on arbitrary hardware.
//            WindowInsetsCompat.toWindowInsetsCompat(windowInsets.toWindowInsets()!!)
//        } else {
//            windowInsets
//        }
//        captionBar.update(insets, types)
//        ime.update(insets, types)
//        displayCutout.update(insets, types)
//        navigationBars.update(insets, types)
//        statusBars.update(insets, types)
//        systemBars.update(insets, types)
//        systemGestures.update(insets, types)
//        tappableElement.update(insets, types)
//        mandatorySystemGestures.update(insets, types)
//
//        if (types == 0) {
//            captionBarIgnoringVisibility.value = insets.getInsetsIgnoringVisibility(
//                WindowInsetsCompat.Type.captionBar()
//            ).toInsetsValues()
//            navigationBarsIgnoringVisibility.value = insets.getInsetsIgnoringVisibility(
//                WindowInsetsCompat.Type.navigationBars()
//            ).toInsetsValues()
//            statusBarsIgnoringVisibility.value = insets.getInsetsIgnoringVisibility(
//                WindowInsetsCompat.Type.statusBars()
//            ).toInsetsValues()
//            systemBarsIgnoringVisibility.value = insets.getInsetsIgnoringVisibility(
//                WindowInsetsCompat.Type.systemBars()
//            ).toInsetsValues()
//            tappableElementIgnoringVisibility.value = insets.getInsetsIgnoringVisibility(
//                WindowInsetsCompat.Type.tappableElement()
//            ).toInsetsValues()
//
//            val cutout = insets.displayCutout
//            if (cutout != null) {
//                val waterfallInsets = cutout.waterfallInsets
//                waterfall.value = waterfallInsets.toInsetsValues()
//            }
//        }
//        Snapshot.sendApplyNotifications()
//    }

    companion object {
        /**
         * A mapping of AndroidComposeView to ComposeWindowInsets. Normally a tag is a great
         * way to do this mapping, but off-UI thread and multithreaded composition don't
         * allow using the tag.
         */
//        private val viewMap = WeakHashMap<UIApplication, WindowInsetsHolder>()
        private val viewMap = HashMap<UIView, WindowInsetsHolder>()

        private var testInsets = false

        /**
         * Testing Window Insets is difficult, so we have this to help eliminate device-specifics
         * from the WindowInsets. This is indirect because `@TestOnly` cannot be applied to a
         * property with a backing field.
         */
        @TestOnly
        fun setUseTestInsets(testInsets: Boolean) {
            Companion.testInsets = testInsets
        }

        @Composable
        fun current(): WindowInsetsHolder {
            val view = LocalLayerContainer.current
            val insets = getOrCreateFor(view)

            DisposableEffect(insets) {
                insets.incrementAccessors()
                onDispose {
                    insets.decrementAccessors()
                }
            }
            return insets
        }

        /**
         * Returns the [WindowInsetsHolder] associated with [view] or creates one and associates
         * it.
         */
        private fun getOrCreateFor(view: UIView): WindowInsetsHolder {
//            return synchronized(view) {
            return viewMap.getOrPut(view) {
                WindowInsetsHolder(view)
            }
//            }
        }

        /**
         * Creates a [ValueInsets] using the value from [windowInsets] if it isn't `null`
         */
        private fun systemInsets(
            view: UIView?,
            type: Int,
            name: String
        ) = UiKitWindowInsets(type, name).apply { view?.let { update(it, type) } }

//        /**
//         * Creates a [ValueInsets] using the "ignoring visibility" value from [windowInsets]
//         * if it isn't `null`
//         */
//        private fun valueInsetsIgnoringVisibility(
//            windowInsets: WindowInsetsCompat?,
//            type: Int,
//            name: String
//        ): ValueInsets {
//            val initial = windowInsets?.getInsetsIgnoringVisibility(type) ?: AndroidXInsets.NONE
//            return ValueInsets(initial, name)
//        }
//    }
    }

    @ExportObjCClass
    private class KeyboardVisibilityListener(
        private val composeInsets: WindowInsetsHolder,
    ) : NSObject() {

        private var currentDisplayLink: CADisplayLink? = null
        private val lock = SynchronizedObject()

        @Suppress("unused")
        @ObjCAction
        fun keyboardWillShow(arg: NSNotification) {
            val keyboardInfo = arg.userInfo!![UIKeyboardFrameEndUserInfoKey] as NSValue
            val keyboardHeight = keyboardInfo.CGRectValue().useContents { size.height }
                .toInt()

            // TODO
//            UIScreen.mainScreen.coordinateSpace.convertPoint

            // TODO incorporate animation curve
//            val options = (arg.userInfo!![UIKeyboardAnimationCurveUserInfoKey] as Long) shl 16

            val duration = arg.userInfo!![UIKeyboardAnimationDurationUserInfoKey] as Double

            animateKeyboard(
                duration = duration,
                toHeight = keyboardHeight,
            )
        }

        @Suppress("unused")
        @ObjCAction
        fun keyboardWillHide(arg: NSNotification) {
            val duration = (arg.userInfo!![UIKeyboardAnimationDurationUserInfoKey] as Double)
            animateKeyboard(
                duration = duration,
                toHeight = 0,
            )
        }

        private fun animateKeyboard(
            duration: Double,
            toHeight: Int,
        ) {
            val selector = NSSelectorFromString("step:")
            val displayLink = CADisplayLink.displayLinkWithTarget(
                DisplayLinkTarget(
                    composeInsets.ime,
                    duration = duration,
                    fromPosition = composeInsets.ime.insets.bottom,
                    toPosition = toHeight,
                ),
                selector
            )
            displayLink.preferredFrameRateRange = CAFrameRateRangeMake(
                minimum = UIScreen.mainScreen.maximumFramesPerSecond.toFloat(),
                maximum = UIScreen.mainScreen.maximumFramesPerSecond.toFloat(),
                preferred = UIScreen.mainScreen.maximumFramesPerSecond.toFloat(),
            )

            synchronized(lock) {
                currentDisplayLink?.also {
//                    it.removeFromRunLoop(
//                        NSRunLoop.currentRunLoop,
//                        NSRunLoop.currentRunLoop.currentMode
//                    )
                }

                currentDisplayLink = displayLink
                displayLink.addToRunLoop(
                    NSRunLoop.currentRunLoop,
                    NSRunLoop.currentRunLoop.currentMode
                )
            }
        }

    }


    @ExportObjCClass
    private class DisplayLinkTarget(
        private val uiKitWindowInsets: UiKitWindowInsets,
        private val startTime: CFTimeInterval = CACurrentMediaTime(),
        private val duration: NSTimeInterval,
        private val fromPosition: Int,
        private val toPosition: Int
    ) : NSObject() {


        @Suppress("unused")
        @ObjCAction
        fun step(sender: CADisplayLink) {
            if (startTime + duration < sender.timestamp) {
                uiKitWindowInsets.insets = InsetsValues(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = toPosition,
                )

                endAnimation(sender)
            } else {
                val progress = minOf(1.0, (sender.timestamp - startTime) / duration)
                val delta = ((toPosition - fromPosition) * progress).roundToInt()

                uiKitWindowInsets.insets = InsetsValues(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = fromPosition + delta,
                )

            }
        }

        fun endAnimation(sender: CADisplayLink) {
            sender.removeFromRunLoop(NSRunLoop.currentRunLoop, NSRunLoop.currentRunLoop.currentMode)
        }

    }

}
