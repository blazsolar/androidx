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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.createSkiaLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.interop.LocalLayerContainer
import androidx.compose.ui.interop.LocalUIViewController
import androidx.compose.ui.native.ComposeLayer
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import kotlin.math.roundToInt
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import org.jetbrains.skiko.SkikoUIView
import org.jetbrains.skiko.TextActions
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.Foundation.NSCoder
import platform.Foundation.NSNotification
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.UIKit.addSubview
import platform.UIKit.backgroundColor
import platform.UIKit.reloadInputViews
import platform.UIKit.setAutoresizesSubviews
import platform.UIKit.setAutoresizingMask
import platform.UIKit.setClipsToBounds
import platform.UIKit.setNeedsDisplay
import platform.darwin.NSObject

fun ComposeUIViewController(content: @Composable () -> Unit): UIViewController =
    ComposeWindow().apply {
        setContent(content)
    }

// The only difference with macos' Window is that
// it has return type of UIViewController rather than unit.
@Deprecated(
    "use ComposeUIViewController instead",
    replaceWith = ReplaceWith(
        "ComposeUIViewController(content = content)",
        "androidx.compose.ui.window"
    )
)
fun Application(
    title: String = "JetpackNativeWindow",
    content: @Composable () -> Unit = { }
): UIViewController = ComposeUIViewController(content)

@ExportObjCClass
internal actual class ComposeWindow : UIViewController {
    @OverrideInit
    actual constructor() : super(nibName = null, bundle = null)

    @OverrideInit
    constructor(coder: NSCoder) : super(coder)

    private val density: Density
        get() = Density(layer.layer.contentScale)

    private lateinit var layer: ComposeLayer
    private lateinit var content: @Composable () -> Unit

    private val keyboardVisibilityListener = object : NSObject() {
        @Suppress("unused")
        @ObjCAction
        fun keyboardWillShow(arg: NSNotification) {
            val keyboardInfo = arg.userInfo!!["UIKeyboardFrameEndUserInfoKey"] as NSValue
            val keyboardHeight = keyboardInfo.CGRectValue().useContents { size.height }
            val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
            val magicMultiplier = density.density - 1 // todo magic number
            val viewY = UIScreen.mainScreen.coordinateSpace.convertPoint(
                point = CGPointMake(0.0, 0.0),
                fromCoordinateSpace = view.coordinateSpace
            ).useContents { y } * magicMultiplier
            val focused = layer.getActiveFocusRect()
            if (focused != null) {
                val focusedBottom = focused.bottom.value + getTopLeftOffset().y
                val hiddenPartOfFocusedElement =
                    focusedBottom + keyboardHeight - screenHeight - viewY
                if (hiddenPartOfFocusedElement > 0) {
                    // If focused element hidden by keyboard, then change UIView bounds.
                    // Focused element will be visible
                    val focusedTop = focused.top.value
                    val composeOffsetY = if (hiddenPartOfFocusedElement < focusedTop) {
                        hiddenPartOfFocusedElement
                    } else {
                        maxOf(focusedTop, 0f).toDouble()
                    }
                    view.setClipsToBounds(true)
                    val (width, height) = getViewFrameSize()
                    view.layer.setBounds(
                        CGRectMake(
                            x = 0.0,
                            y = composeOffsetY,
                            width = width.toDouble(),
                            height = height.toDouble()
                        )
                    )
                }
            }
        }

        @Suppress("unused")
        @ObjCAction
        fun keyboardWillHide(arg: NSNotification) {
            val (width, height) = getViewFrameSize()
            view.layer.setBounds(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        }

        @Suppress("unused")
        @ObjCAction
        fun keyboardDidHide(arg: NSNotification) {
            view.setClipsToBounds(false)
        }
    }

    override fun loadView() {
        val skiaLayer = createSkiaLayer()
        val skikoUIView = SkikoUIView(
            skiaLayer = skiaLayer,
            pointInside = { point, _ ->
                !layer.hitInteropView(point, isTouchEvent = true)
            },
        ).load()
        val rootView = UIView() // rootView needs to interop with UIKit
        rootView.backgroundColor = UIColor.whiteColor
        rootView.addSubview(skikoUIView)
        rootView.setAutoresizesSubviews(true)
        skikoUIView.setAutoresizingMask(
            UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        )
        view = rootView
        val uiKitTextInputService = UIKitTextInputService(
            showSoftwareKeyboard = {
                skikoUIView.showScreenKeyboard()
            },
            hideSoftwareKeyboard = {
                skikoUIView.hideScreenKeyboard()
            },
            updateView = {
                skikoUIView.setNeedsDisplay() // redraw on next frame
                platform.QuartzCore.CATransaction.flush() // clear all animations
                skikoUIView.reloadInputViews() // update input (like screen keyboard)
            },
            textWillChange = { skikoUIView.textWillChange() },
            textDidChange = { skikoUIView.textDidChange() },
            selectionWillChange = { skikoUIView.selectionWillChange() },
            selectionDidChange = { skikoUIView.selectionDidChange() },
        )
        val uiKitPlatform = object : Platform by Platform.Empty {
            override val textInputService: PlatformTextInputService = uiKitTextInputService
            override val viewConfiguration =
                object : ViewConfiguration {
                    override val longPressTimeoutMillis: Long get() = 500
                    override val doubleTapTimeoutMillis: Long get() = 300
                    override val doubleTapMinTimeMillis: Long get() = 40
                    override val touchSlop: Float get() = with(density) { 3.dp.toPx() }
                }
            override val textToolbar = object : TextToolbar {
                override fun showMenu(
                    rect: Rect,
                    onCopyRequested: (() -> Unit)?,
                    onPasteRequested: (() -> Unit)?,
                    onCutRequested: (() -> Unit)?,
                    onSelectAllRequested: (() -> Unit)?
                ) {
                    val skiaRect = with(density) {
                        org.jetbrains.skia.Rect.makeLTRB(
                            l = rect.left / density,
                            t = rect.top / density,
                            r = rect.right / density,
                            b = rect.bottom / density,
                        )
                    }
                    skikoUIView.showTextMenu(
                        targetRect = skiaRect,
                        textActions = object : TextActions {
                            override val copy: (() -> Unit)? = onCopyRequested
                            override val cut: (() -> Unit)? = onCutRequested
                            override val paste: (() -> Unit)? = onPasteRequested
                            override val selectAll: (() -> Unit)? = onSelectAllRequested
                        }
                    )
                }

                /**
                 * TODO on UIKit native behaviour is hide text menu, when touch outside
                 */
                override fun hide() = skikoUIView.hideTextMenu()

                override val status: TextToolbarStatus
                    get() = if (skikoUIView.isTextMenuShown())
                        TextToolbarStatus.Shown
                    else
                        TextToolbarStatus.Hidden
            }

            override val inputModeManager = DefaultInputModeManager(InputMode.Touch)
        }
        layer = ComposeLayer(
            layer = skiaLayer,
            platform = uiKitPlatform,
            getTopLeftOffset = ::getTopLeftOffset,
            input = uiKitTextInputService.skikoInput,
        )
        layer.setContent(content = {
            CompositionLocalProvider(
                LocalLayerContainer provides rootView,
                LocalUIViewController provides this,
            ) {
                content()
            }
        })
    }

    override fun viewWillTransitionToSize(
        size: CValue<CGSize>,
        withTransitionCoordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        layer.setDensity(density)
        val scale = density.density
        val width = size.useContents { width } * scale
        val height = size.useContents { height } * scale
        layer.setSize(width.roundToInt(), height.roundToInt())
        layer.layer.needRedraw() // TODO: remove? the following block should be enough
        withTransitionCoordinator.animateAlongsideTransition(animation = null) {
            // Docs: https://developer.apple.com/documentation/uikit/uiviewcontrollertransitioncoordinator/1619295-animatealongsidetransition
            // Request a frame once more on animation completion.
            // Consider adding redrawImmediately() in SkiaLayer for ios to sync with current frame.
            // This fixes an interop use case when Compose is embedded in SwiftUi.
            layer.layer.needRedraw()
        }
        super.viewWillTransitionToSize(size, withTransitionCoordinator)
    }

    override fun viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        val (width, height) = getViewFrameSize()
        layer.setDensity(density)
        val scale = density.density
        layer.setSize((width * scale).roundToInt(), (height * scale).roundToInt())
    }

    // viewDidUnload() is deprecated and not called.

    override fun didReceiveMemoryWarning() {
        println("didReceiveMemoryWarning")
        kotlin.native.internal.GC.collect()
        super.didReceiveMemoryWarning()
    }

    actual fun setContent(
        content: @Composable () -> Unit
    ) {
        this.content = content
    }

    override fun viewDidUnload() {
        super.viewDidUnload()
        this.dispose()
    }

    actual fun dispose() {
        layer.dispose()
    }

    // TODO override method and notify updates!
//    override fun viewSafeAreaInsetsDidChange() {
//    }

    private fun getViewFrameSize(): IntSize {
        val (width, height) = view.frame().useContents { this.size.width to this.size.height }
        return IntSize(width.toInt(), height.toInt())
    }

    private fun getTopLeftOffset(): Offset {
        val topLeftPoint =
            view.coordinateSpace().convertPoint(
                point = CGPointMake(0.0, 0.0),
                toCoordinateSpace = UIScreen.mainScreen.coordinateSpace()
            )
        return topLeftPoint.useContents { DpOffset(x.dp, y.dp).toOffset(density) }
    }

}
