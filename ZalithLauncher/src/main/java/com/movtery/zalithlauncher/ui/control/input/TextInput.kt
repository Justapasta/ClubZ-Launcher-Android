/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.control.input

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HidableInputLayout(
    onSend: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onClose: () -> Unit,
    keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current,
) {
    var view by remember { mutableStateOf<TouchCharInput?>(null) }
    val localView = LocalView.current

    AndroidView(
        modifier = Modifier
            .alpha(0f)
            .size(1.dp),
        factory = { context ->
            TouchCharInput(context).apply {
                id = View.generateViewId()

                imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                        EditorInfo.IME_ACTION_DONE
                inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                        InputType.TYPE_TEXT_VARIATION_FILTER or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE

                setEms(10)

                isFocusableInTouchMode = true
                nextFocusDownId = id
                nextFocusUpId = id
                nextFocusLeftId = id
                nextFocusRightId = id
            }.also { view0 ->
                view = view0.also {
                    it.setListener(
                        object : InputListener {
                            override fun onSend(char: Char) {
                                onSend(char.toString())
                            }
                            override fun onBackspace() {
                                onBackspace()
                            }
                            override fun onEnter() {
                                onEnter()
                                onClose()
                            }
                        }
                    )
                }
            }
        }
    )

    LaunchedEffect(view) {
        val input = view ?: return@LaunchedEffect
        // CLUB Z: en HyperOS/MIUI el teclado no salía (SHOW_IMPLICIT ignorado y el menú que se cierra roba el foco).
        // Esperamos a que cierre el menú, pedimos el teclado con la API moderna y reintentamos si no aparece.
        kotlinx.coroutines.delay(250)
        input.enableKeyboard()
        repeat(4) {
            kotlinx.coroutines.delay(300)
            val visible = androidx.core.view.ViewCompat.getRootWindowInsets(input)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            if (visible) return@LaunchedEffect
            input.requestFocus()
            androidx.core.view.WindowCompat.getInsetsController(
                (input.context as? android.app.Activity)?.window ?: return@LaunchedEffect, input
            ).show(androidx.core.view.WindowInsetsCompat.Type.ime())
            (input.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            view?.disableKeyboard()
            keyboardController?.hide()
            view = null
            localView.requestFocus()
        }
    }

    OnKeyboardClosed {
        keyboardController?.hide()
        onClose()
    }
}