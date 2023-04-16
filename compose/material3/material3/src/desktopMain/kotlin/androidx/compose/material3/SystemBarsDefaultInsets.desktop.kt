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

package androidx.compose.material3

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@Composable
internal actual fun WindowInsets.Companion.systemBarsForVisualComponents(): WindowInsets {
    return with(LocalDensity.current) {
        WindowInsets(
            top = 200.toDp(),
            left = 0.dp,
            right = 0.dp,
            bottom = 200.dp)
    }
}
//    @Composable
//    get() = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
