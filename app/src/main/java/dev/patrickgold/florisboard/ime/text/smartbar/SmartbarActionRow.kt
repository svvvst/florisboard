/*
 * Copyright (C) 2021 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.smartbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.prefs.florisPreferenceModel
import dev.patrickgold.florisboard.common.android.showShortToast
import dev.patrickgold.florisboard.common.observeAsNonNullState
import dev.patrickgold.florisboard.ime.core.InputKeyEvent
import dev.patrickgold.florisboard.ime.keyboard.computeIconResId
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.snygg.ui.snyggBackground
import dev.patrickgold.florisboard.snygg.ui.solidColor
import dev.patrickgold.jetpref.datastore.model.observeAsState

private val SmartbarActionPadding = 4.dp

@Composable
fun SmartbarActionRow() = with(LocalDensity.current) {
    val prefs by florisPreferenceModel()
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val primaryRowFlipToggles by prefs.smartbar.primaryRowFlipToggles.observeAsState()
    val renderInfo by keyboardManager.renderInfo.observeAsNonNullState()
    val smartbarActions by keyboardManager.smartbarActions.observeAsNonNullState()
    val rowStyle = FlorisImeTheme.style.get(FlorisImeUi.SmartbarActionRow)
    val buttonStyle = FlorisImeTheme.style.get(FlorisImeUi.SmartbarActionButton)
    val moreStyle = FlorisImeTheme.style.get(FlorisImeUi.SmartbarPrimarySecondaryRowToggle)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .snyggBackground(rowStyle.background),
    ) {
        val width = constraints.maxWidth.toDp()
        val height = constraints.maxHeight.toDp()
        val numActionsToShow = (width / height).toInt()
        val visibleSmartbarActions = smartbarActions
            .filterIsInstance(SmartbarAction.Key::class.java)
            .subList(0, numActionsToShow.coerceAtMost(smartbarActions.size))

        @Composable
        fun MoreButton() {
            IconButton(
                modifier = Modifier
                    .padding(SmartbarActionPadding)
                    .fillMaxHeight()
                    .aspectRatio(1f),
                onClick = {
                    // TODO
                    context.showShortToast("TODO: implement actions overflow menu")
                },
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .snyggBackground(moreStyle.background, moreStyle.shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.padding(2.dp),
                        painter = painterResource(R.drawable.ic_more_horiz),
                        contentDescription = null,
                        tint = moreStyle.foreground.solidColor(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (primaryRowFlipToggles) {
                MoreButton()
            }
            for (smartbarAction in visibleSmartbarActions) {
                val icon = renderInfo.evaluator.computeIconResId(smartbarAction.data)
                IconButton(
                    modifier = Modifier
                        .padding(SmartbarActionPadding)
                        .fillMaxHeight()
                        .aspectRatio(1f),
                    onClick = {
                        keyboardManager.inputEventDispatcher.send(InputKeyEvent.downUp(smartbarAction.data))
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .snyggBackground(buttonStyle.background, buttonStyle.shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            Icon(
                                modifier = Modifier.padding(2.dp),
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = buttonStyle.foreground.solidColor(),
                            )
                        }
                    }
                }
            }
            if (!primaryRowFlipToggles) {
                MoreButton()
            }
        }
    }
}
