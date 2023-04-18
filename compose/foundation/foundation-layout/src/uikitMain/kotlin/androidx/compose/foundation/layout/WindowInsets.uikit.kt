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
import androidx.compose.ui.interop.LocalUIViewController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTimeInterval
import platform.CoreGraphics.CGPoint
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
import platform.UIKit.UICubicTimingParameters
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationCurve
import platform.UIKit.UIViewController
import platform.UIKit.safeAreaInsets
import platform.UIKit.safeAreaInsetsDidChange
import platform.UIKit.viewSafeAreaInsetsDidChange
import platform.darwin.NSObject

/**
 * [WindowInsets] provided by the Android framework. These can be used in
 * [rememberWindowInsetsConnection] to control the insets.
 */
@Stable
class UiKitWindowInsets(
    private val name: String
) : WindowInsets {
    //    internal var insets by mutableStateOf(AndroidXInsets.NONE)
    internal var insets by mutableStateOf(InsetsValues(0, 0, 0, 0))

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

    internal fun update(viewController: UIViewController) {
        UIApplication.sharedApplication.keyWindow?.safeAreaInsets?.useContents {
            // TODO viewController.view.window is null at start and therefore no insets are present.
            //  This should be resolved together with [viewSafeAreaInsetsDidChange] as it is called
            //  immediately after viewDidLoad, and that is where window is set.
//        viewController.view.safeAreaInsets.useContents {
            InsetsValues(
                top = top.toInt(),
                left = left.toInt(),
                right = right.toInt(),
                bottom = bottom.toInt(),
            )
        }?.also { insets = it }

    }

    override fun toString(): String {
        return "$name(${insets.left}, ${insets.top}, ${insets.right}, ${insets.bottom})"
    }
}

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

/**
 * For the [WindowInsetsCompat.Type.navigationBars]. These insets represent where
 * system UI places navigation bars. Interactive UI should avoid the navigation bars
 * area.
 */
val WindowInsets.Companion.navigationBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().navigationBars

/**
 * For the [WindowInsetsCompat.Type.statusBars].
 */
val WindowInsets.Companion.statusBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().statusBars

/**
 * For the [WindowInsetsCompat.Type.systemBars].
 */
val WindowInsets.Companion.systemBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().systemBars

/**
 * The insets that include areas where content may be covered by other drawn content.
 * This includes all [system bars][systemBars], [display cutout][displayCutout], and
 * [soft keyboard][ime].
 */
val WindowInsets.Companion.safeDrawing: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsetsHolder.current().safeDrawing

/**
 * The insets for various values in the current window.
 */
internal class WindowInsetsHolder private constructor(private val viewController: UIViewController) {
    val ime = UiKitWindowInsets("ime")

    val systemBars = systemInsets(viewController, "systemBars")

    val navigationBars = systemBars.only(WindowInsetsSides.Bottom)

    val statusBars = systemBars.only(WindowInsetsSides.Top)

    val safeDrawing = systemBars.union(ime)

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

    companion object {
        /**
         * A mapping of AndroidComposeView to ComposeWindowInsets. Normally a tag is a great
         * way to do this mapping, but off-UI thread and multithreaded composition don't
         * allow using the tag.
         */
//        private val viewMap = WeakHashMap<UIApplication, WindowInsetsHolder>()
        private val viewMap = HashMap<UIViewController, WindowInsetsHolder>()

        @Composable
        fun current(): WindowInsetsHolder {
            val viewController = LocalUIViewController.current
            val insets = getOrCreateFor(viewController)

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
        private fun getOrCreateFor(viewController: UIViewController): WindowInsetsHolder {
//            return synchronized(view) {
            return viewMap.getOrPut(viewController) {
                WindowInsetsHolder(viewController)
            }
//            }
        }

        /**
         * Creates a [ValueInsets] using the value from [windowInsets] if it isn't `null`
         */
        private fun systemInsets(
            viewController: UIViewController,
            name: String
        ) = UiKitWindowInsets(name).apply { update(viewController)}

    }

    @ExportObjCClass
    private class KeyboardVisibilityListener(
        private val composeInsets: WindowInsetsHolder,
    ) : NSObject() {

        private var currentDisplayLink: CADisplayLink? = null
        private var currentDisplayLinkTarget: DisplayLinkTarget? = null
        private val lock = SynchronizedObject()

        @Suppress("unused")
        @ObjCAction
        fun keyboardWillShow(arg: NSNotification) {
            val keyboardInfo = arg.userInfo!![UIKeyboardFrameEndUserInfoKey] as NSValue
            val keyboardHeight = keyboardInfo.CGRectValue().useContents { size.height }
                .toInt()

            keyboardWillChange(arg = arg, toHeight = keyboardHeight)
        }

        @Suppress("unused")
        @ObjCAction
        fun keyboardWillHide(arg: NSNotification) {
            keyboardWillChange(arg = arg, toHeight = 0)
        }

        private fun keyboardWillChange(
            arg: NSNotification,
            toHeight: Int,
        ) {
            // TODO
//            UIScreen.mainScreen.coordinateSpace.convertPoint

            // TODO incorporate animation curve
            //  something like https://gist.github.com/raphaelschaad/6739676
            val curve = when (arg.userInfo!![UIKeyboardAnimationCurveUserInfoKey] as Long) {
                UIViewAnimationCurve.UIViewAnimationCurveEaseInOut.value -> UIViewAnimationCurve.UIViewAnimationCurveEaseInOut
                UIViewAnimationCurve.UIViewAnimationCurveEaseOut.value -> UIViewAnimationCurve.UIViewAnimationCurveEaseOut
                UIViewAnimationCurve.UIViewAnimationCurveEaseIn.value -> UIViewAnimationCurve.UIViewAnimationCurveEaseIn
                UIViewAnimationCurve.UIViewAnimationCurveLinear.value -> UIViewAnimationCurve.UIViewAnimationCurveLinear
                // Default values should be linear, as by default `UIKeyboardAnimationCurveUserInfoKey` contains value 7,
                //  that is not port for public enum values. By testing this manually, curve returned by raw value 7 is
                //  [0.0, 0.0], [1.0, 1.0].
                //
                //  Testing code:
                //  ```swift
                //        var animationCurve = UIView.AnimationCurve(rawValue: 7)
                //        let param = UICubicTimingParameters(animationCurve: animationCurve)
                //
                //        print(param.controlPoint1)
                //        print(param.controlPoint2)
                //  ```
                else -> UIViewAnimationCurve.UIViewAnimationCurveLinear
            }

            val timingFunction = TimingFunction(UICubicTimingParameters(curve))
            val duration = (arg.userInfo!![UIKeyboardAnimationDurationUserInfoKey] as Double)

            animateKeyboard(
                duration = duration,
                toHeight = toHeight,
                timingFunction = timingFunction,
            )
        }

        private fun animateKeyboard(
            duration: Double,
            toHeight: Int,
            timingFunction: TimingFunction,
        ) {
            val selector = NSSelectorFromString("step:")
            val target = DisplayLinkTarget(
                composeInsets.ime,
                timingFunction = timingFunction,
                duration = duration,
                fromPosition = composeInsets.ime.insets.bottom,
                toPosition = toHeight,
            )
            val displayLink = CADisplayLink.displayLinkWithTarget(
                target,
                selector
            )
            displayLink.preferredFrameRateRange = CAFrameRateRangeMake(
                minimum = UIScreen.mainScreen.maximumFramesPerSecond.toFloat(),
                maximum = UIScreen.mainScreen.maximumFramesPerSecond.toFloat(),
                preferred = UIScreen.mainScreen.maximumFramesPerSecond.toFloat(),
            )

            synchronized(lock) {
                currentDisplayLinkTarget?.endAnimation(currentDisplayLink!!)

                currentDisplayLink = displayLink
                currentDisplayLinkTarget = target

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
        private val timingFunction: TimingFunction,
        private val startTime: CFTimeInterval = CACurrentMediaTime(),
        private val duration: NSTimeInterval,
        private val fromPosition: Int,
        private val toPosition: Int
    ) : NSObject() {

        private var isDone: Boolean = false

        @Suppress("unused")
        @ObjCAction
        fun step(sender: CADisplayLink) {
            if (startTime + duration < sender.targetTimestamp) {
                uiKitWindowInsets.insets = InsetsValues(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = toPosition,
                )

                endAnimation(sender)
            } else {
                val progress = minOf(1.0, (sender.targetTimestamp - startTime) / duration)
                val delta =
                    ((toPosition - fromPosition) * timingFunction.progress(progress.toFloat())).roundToInt()

                uiKitWindowInsets.insets = InsetsValues(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = fromPosition + delta,
                )

            }
        }

        fun endAnimation(sender: CADisplayLink) {
            if (!isDone) {
                sender.removeFromRunLoop(
                    NSRunLoop.currentRunLoop,
                    NSRunLoop.currentRunLoop.currentMode
                )
                isDone = true
            }
        }

    }

}

/**
 * Based on: https://gist.github.com/raphaelschaad/6739676
 */
class TimingFunction {
    private val controlPoint1: CGPoint
    private val controlPoint2: CGPoint
    private val unitBezier: UnitBezier
    private val epsilon: Float;

    constructor(timingParameters: UICubicTimingParameters, duration: Float = 1F) {
        controlPoint1 = timingParameters.controlPoint1.useContents { this }
        controlPoint2 = timingParameters.controlPoint2.useContents { this }
        unitBezier = UnitBezier(controlPoint1, controlPoint2);
        epsilon = 1.0f / (200.0f * duration);
    }

    constructor(controlPoint1: CGPoint, controlPoint2: CGPoint, duration: Float = 1F) {
        this.controlPoint1 = controlPoint1;
        this.controlPoint2 = controlPoint2;
        unitBezier = UnitBezier(this.controlPoint1, this.controlPoint2);
        epsilon = 1.0f / (200.0f * duration);
    }

    /**
     * Returns the progress along the timing function for the given time (`fractionComplete`)
     * with `0.0` equal to the start of the curve, and `1.0` equal to the end of the curve
     */
    fun progress(fractionComplete: Float): Float {
        return unitBezier.value(fractionComplete, epsilon);
    }
}

class UnitBezier(controlPoint1: CGPoint, controlPoint2: CGPoint) {

    private val ax: Float
    private val bx: Float
    private val cx: Float

    private val ay: Float
    private val by: Float
    private val cy: Float

    /**
     * Calculate the polynomial coefficients, implicit first
     * and last control points are (0,0) and (1,1).
     */
    init {
        cx = (3.0f * controlPoint1.x).toFloat()
        bx = (3.0f * (controlPoint2.x - controlPoint1.x) - cx).toFloat()
        ax = 1.0f - cx - bx
        cy = (3.0f * controlPoint1.y).toFloat()
        by = (3.0f * (controlPoint2.y - controlPoint1.y) - cy).toFloat()
        ay = 1.0f - cy - by
    }

    fun value(x: Float, epsilon: Float): Float {
        return sampleCurveY(solveCurveX(x, epsilon))
    }

    /**
     * `ax t^3 + bx t^2 + cx t' expanded using Horner's rule.
     */
    private fun sampleCurveX(t: Float): Float {
        return ((ax * t + bx) * t + cx) * t
    }

    private fun sampleCurveY(t: Float): Float {
        return ((ay * t + by) * t + cy) * t
    }

    private fun sampleCurveDerivativeX(t: Float): Float {
        return (3.0f * ax * t + 2.0f * bx) * t + cx
    }

    private fun solveCurveX(x: Float, epsilon: Float): Float {
        var t0: Float
        var t1: Float
        var t2: Float
        var x2: Float
        var d2: Float

        // First try a few iterations of Newton's method -- normally very fast.
        t2 = x
        for (index in 0 until 8) {
            x2 = sampleCurveX(t2) - x
            if (abs(x2) < epsilon) {
                return t2
            }

            d2 = sampleCurveDerivativeX(t2)
            if (abs(d2) < 0.00000001f) {
                break
            }

            t2 -= x2 / d2
        }

        // Fall back to the bisection method for reliability.
        t0 = 0.0f
        t1 = 1.0f
        t2 = x

        if (t2 < t0) {
            return t0
        }

        if (t2 > t1) {
            return t1
        }

        while (t0 < t1) {
            x2 = sampleCurveX(t2)

            if (abs(x2 - x) < epsilon) {
                return t2
            }

            if (x > x2) {
                t0 = t2
            } else {
                t1 = t2
            }

            t2 = t0 + ((t1 - t0) / 2)
        }

        // Failure
        return t2
    }
}
