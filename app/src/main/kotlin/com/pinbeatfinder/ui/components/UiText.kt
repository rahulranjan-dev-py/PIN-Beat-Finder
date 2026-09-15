package com.pinbeatfinder.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** A message the ViewModel can emit without holding a Context: either a resource or literal text. */
sealed interface UiText {
    data class Raw(val text: String) : UiText
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText

    fun asString(context: Context): String = when (this) {
        is Raw -> text
        is Res -> context.getString(id, *args)
    }

    @Composable
    fun asString(): String = asString(LocalContext.current)
}
