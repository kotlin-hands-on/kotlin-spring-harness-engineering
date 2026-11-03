package com.example.harnessengineeringspring.web

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/** Placeholder entry point; the application shell arrives with the `web-shell` change. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        BasicText("harness-engineering-spring")
    }
}
