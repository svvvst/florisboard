/*
 * Copyright (C) 2020 Patrick Goldinger
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

package dev.patrickgold.florisboard.ime.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.prefs.florisPreferenceModel
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.res.ZipUtils
import dev.patrickgold.florisboard.res.ext.ExtensionComponentName
import dev.patrickgold.florisboard.snygg.SnyggStylesheet
import dev.patrickgold.florisboard.snygg.SnyggStylesheetJsonConfig
import dev.patrickgold.florisboard.snygg.ui.solidColor
import dev.patrickgold.florisboard.snygg.value.SnyggSolidColorValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlin.properties.Delegates

/**
 * Core class which manages the keyboard theme. Note, that this does not affect the UI theme of the
 * Settings Activities.
 */
class ThemeManager(context: Context) {
    private val prefs by florisPreferenceModel()
    private val appContext by context.appContext()
    private val extensionManager by context.extensionManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _indexedThemeConfigs = MutableLiveData(mapOf<ExtensionComponentName, ThemeExtensionComponent>())
    val indexedThemeConfigs: LiveData<Map<ExtensionComponentName, ThemeExtensionComponent>> get() = _indexedThemeConfigs
    var previewThemeId: ExtensionComponentName? by Delegates.observable(null) { _, _, _ ->
        updateActiveTheme()
    }

    private val cachedThemeInfos = mutableListOf<ThemeInfo>()
    private val activeThemeGuard = Mutex(locked = false)
    private val _activeThemeInfo = MutableLiveData(ThemeInfo.DEFAULT)
    val activeThemeInfo: LiveData<ThemeInfo> get() = _activeThemeInfo

    companion object {
        /**
         * Creates a new inline suggestion UI bundle based on the attributes of the given [style].
         *
         * @param context The context of the parent view/controller.
         * @param style The theme from which the color attributes should be fetched. Defaults to
         *  [FlorisImeThemeBaseStyle].
         *
         * @return A bundle containing all necessary attributes for the inline suggestion views to properly display.
         */
        @SuppressLint("RestrictedApi")
        @RequiresApi(Build.VERSION_CODES.R)
        fun createInlineSuggestionUiStyleBundle(context: Context, style: SnyggStylesheet = FlorisImeThemeBaseStyle): Bundle {
            val chipStyle = style.getStatic(FlorisImeUi.SmartbarPrimaryActionRowToggle)
            val bgColor = chipStyle.background.solidColor().toArgb()
            val fgColor = chipStyle.foreground.solidColor().toArgb()
            val bgDrawableId = R.drawable.chip_background
            val stylesBuilder = UiVersions.newStylesBuilder()
            val suggestionStyle = InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                    ViewStyle.Builder()
                        .setBackground(
                            Icon.createWithResource(context, bgDrawableId).setTint(bgColor)
                        )
                        .setPadding(0, 0, 0, 0)
                        .build()
                )
                .setChipStyle(
                    ViewStyle.Builder()
                        .setBackground(
                            Icon.createWithResource(context, bgDrawableId).setTint(bgColor)
                        )
                        .setPadding(
                            context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_start).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_top).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_end).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_bg_padding_bottom).toInt(),
                        )
                        .build()
                )
                .setStartIconStyle(
                    ImageViewStyle.Builder()
                        .setLayoutMargin(0, 0, 0, 0)
                        .build()
                )
                .setTitleStyle(
                    TextViewStyle.Builder()
                        .setLayoutMargin(
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_start).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_top).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_end).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_title_margin_bottom).toInt(),
                        )
                        .setTextColor(fgColor)
                        .setTextSize(16f)
                        .build()
                )
                .setSubtitleStyle(
                    TextViewStyle.Builder()
                        .setLayoutMargin(
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_start).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_top).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_end).toInt(),
                            context.resources.getDimension(R.dimen.suggestion_chip_fg_subtitle_margin_bottom).toInt(),
                        )
                        .setTextColor(ColorUtils.setAlphaComponent(fgColor, 150))
                        .setTextSize(14f)
                        .build()
                )
                .setEndIconStyle(
                    ImageViewStyle.Builder()
                        .setLayoutMargin(0, 0, 0, 0)
                        .build()
                )
                .build()
            stylesBuilder.addStyle(suggestionStyle)
            return stylesBuilder.build()
        }
    }

    init {
        extensionManager.themes.observeForever { themeExtensions ->
            val map = buildMap {
                for (themeExtension in themeExtensions) {
                    for (themeComponent in themeExtension.themes) {
                        put(ExtensionComponentName(themeExtension.meta.id, themeComponent.id), themeComponent)
                    }
                }
            }
            _indexedThemeConfigs.postValue(map)
        }
        indexedThemeConfigs.observeForever {
            updateActiveTheme {
                cachedThemeInfos.clear()
            }
        }
        prefs.theme.mode.observeForever {
            updateActiveTheme()
        }
        prefs.theme.dayThemeId.observeForever {
            updateActiveTheme()
        }
        prefs.theme.nightThemeId.observeForever {
            updateActiveTheme()
        }
    }

    /**
     * Updates the current theme ref and loads the corresponding theme, as well as notifies all
     * callback receivers about the new theme.
     */
    private fun updateActiveTheme(action: () -> Unit = { }) = scope.launch {
        activeThemeGuard.withLock {
            action()
            val activeName = evaluateActiveThemeName()
            val cachedInfo = cachedThemeInfos.find { it.name == activeName }
            if (cachedInfo != null) {
                _activeThemeInfo.postValue(cachedInfo)
            } else {
                val themeExt = extensionManager.getExtensionById(activeName.extensionId) as? ThemeExtension
                val themeExtRef = themeExt?.sourceRef
                if (themeExtRef != null) {
                    val themeConfig = themeExt.themes.find { it.id == activeName.componentId }
                    if (themeConfig != null) {
                        val newStylesheet = ZipUtils.readFileFromArchive(
                            appContext, themeExtRef, themeConfig.stylesheetPath(),
                        ).getOrNull()?.let { raw -> SnyggStylesheetJsonConfig.decodeFromString<SnyggStylesheet>(raw) }
                        if (newStylesheet != null) {
                            val newCompiledStylesheet = (newStylesheet)
                                .compileToFullyQualified(FlorisImeUiSpec)
                            val newInfo = ThemeInfo(activeName, themeConfig, newCompiledStylesheet)
                            cachedThemeInfos.add(newInfo)
                            _activeThemeInfo.postValue(newInfo)
                        }
                    }
                }
            }
        }
    }

    private fun evaluateActiveThemeName(): ExtensionComponentName {
        previewThemeId?.let { return it }
        return when (prefs.theme.mode.get()) {
            ThemeMode.ALWAYS_DAY -> {
                prefs.theme.dayThemeId.get()
            }
            ThemeMode.ALWAYS_NIGHT -> {
                prefs.theme.nightThemeId.get()
            }
            ThemeMode.FOLLOW_SYSTEM -> if (appContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            ) {
                prefs.theme.nightThemeId.get()
            } else {
                prefs.theme.dayThemeId.get()
            }
            ThemeMode.FOLLOW_TIME -> {
                //if (AndroidVersion.ATLEAST_API26_O) {
                //    val current = LocalTime.now()
                //    val sunrise = prefs.theme.sunriseTime.get()
                //    val sunset = prefs.theme.sunsetTime.get()
                //    if (current in sunrise..sunset) {
                //        prefs.theme.dayThemeId.get()
                //    } else {
                //        prefs.theme.nightThemeId.get()
                //    }
                //} else {
                    prefs.theme.nightThemeId.get()
                //}
            }
        }
    }

    private fun getColorFromThemeAttribute(
        context: Context, typedValue: TypedValue, @AttrRes attr: Int
    ): Int? {
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type == TypedValue.TYPE_REFERENCE) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            null
        }
    }

    data class ThemeInfo(
        val name: ExtensionComponentName,
        val config: ThemeExtensionComponent,
        val stylesheet: SnyggStylesheet,
    ) {
        companion object {
            val DEFAULT = ThemeInfo(
                name = extCoreTheme("base"),
                config = ThemeExtensionComponent(id = "base", label = "Base", authors = listOf()),
                stylesheet = FlorisImeThemeBaseStyle.compileToFullyQualified(FlorisImeUiSpec),
            )
        }
    }

    data class RemoteColors(
        val packageName: String,
        val colorPrimary: SnyggSolidColorValue?,
        val colorPrimaryVariant: SnyggSolidColorValue?,
        val colorSecondary: SnyggSolidColorValue?,
    ) {
        companion object {
            val DEFAULT = RemoteColors("undefined", null, null, null)
        }
    }
}
