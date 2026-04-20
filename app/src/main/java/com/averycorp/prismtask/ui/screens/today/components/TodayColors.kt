package com.averycorp.prismtask.ui.screens.today.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.averycorp.prismtask.ui.theme.LocalPrismColors

@Composable
internal fun neutralGray(): Color = LocalPrismColors.current.muted

@Composable
internal fun completedGreen(): Color = LocalPrismColors.current.successColor
