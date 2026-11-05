package com.michelle.raouf.fetch_jet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FetchJetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    enabled: Boolean = true,
    error: String? = null,
    centered: Boolean = false,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val interaction = remember { MutableInteractionSource() }
    FieldFrame(label, error, enabled, interaction, shape, modifier) { style, cursor, focused ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = textStyle.merge(style)
                .merge(TextStyle(textAlign = if (centered) TextAlign.Center else TextAlign.Start)),
            cursorBrush = cursor,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                FieldBody(
                    value.isEmpty(),
                    placeholder,
                    leading,
                    trailing,
                    singleLine,
                    height,
                    centered,
                    focused,
                    textStyle,
                    inner
                )
            }
        )
    }
}

@Composable
internal fun FetchJetTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    enabled: Boolean = true,
    error: String? = null,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val interaction = remember { MutableInteractionSource() }
    FieldFrame(label, error, enabled, interaction, shape, modifier) { style, cursor, focused ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = textStyle.merge(style),
            cursorBrush = cursor,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                FieldBody(
                    value.text.isEmpty(),
                    placeholder,
                    leading,
                    trailing,
                    singleLine,
                    height,
                    false,
                    focused,
                    textStyle,
                    inner
                )
            }
        )
    }
}

@Composable
internal fun FieldIconButton(
    text: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        tween(150)
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        Text(text, fontSize = 14.sp, color = tint)
    }
}

@Composable
internal fun FieldChipButton(text: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = primary,
            maxLines = 1
        )
    }
}

private object FocusedField {
    var bounds: Rect? = null
}

internal fun Modifier.clearFocusOnTapOutside(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val slop = LocalViewConfiguration.current.touchSlop
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { coords[0] = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() > slop) return@awaitEachGesture
            val bounds = FocusedField.bounds ?: return@awaitEachGesture
            val at = coords[0]?.takeIf { it.isAttached }?.localToWindow(up.position)
                ?: return@awaitEachGesture
            if (!bounds.contains(at)) focusManager.clearFocus()
        }
    }
}

@Composable
private fun FieldFrame(
    label: String?,
    error: String?,
    enabled: Boolean,
    interaction: MutableInteractionSource,
    shape: Shape,
    modifier: Modifier,
    field: @Composable (TextStyle, SolidColor, Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val hasError = !error.isNullOrBlank()
    val ownBounds = remember { arrayOfNulls<Rect>(1) }
    DisposableEffect(focused) {
        if (focused) FocusedField.bounds = ownBounds[0]
        onDispose { if (FocusedField.bounds == ownBounds[0]) FocusedField.bounds = null }
    }
    val accent = if (hasError) scheme.error else scheme.primary

    val border by animateColorAsState(
        when {
            hasError -> scheme.error
            focused -> scheme.primary
            hovered -> scheme.outline
            else -> scheme.outlineVariant.copy(alpha = 0.7f)
        },
        tween(180)
    )
    val borderWidth by animateDpAsState(if (focused || hasError) 1.5.dp else 1.dp, tween(180))
    val container by animateColorAsState(
        if (focused) scheme.primary.copy(alpha = 0.06f).compositeOver(scheme.surface)
        else scheme.surfaceVariant.copy(alpha = 0.35f).compositeOver(scheme.surface),
        tween(180)
    )
    val glow by animateFloatAsState(if (focused || hasError) 1f else 0f, tween(220))
    val labelColor by animateColorAsState(
        when {
            hasError -> scheme.error
            focused -> scheme.primary
            else -> scheme.onSurfaceVariant
        },
        tween(180)
    )

    Column(modifier = modifier.alpha(if (enabled) 1f else 0.6f)) {
        if (label != null) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
            )
        }
        val glowModifier = if (glow > 0.01f) {
            Modifier.dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 10.dp,
                    spread = 0.dp,
                    offset = DpOffset(0.dp, 2.dp),
                    color = accent,
                    alpha = 0.28f * glow
                )
            )
        } else Modifier
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(glowModifier)
                .clip(shape)
                .background(container, shape)
                .border(borderWidth, border, shape)
                .hoverable(interaction, enabled)
                .onGloballyPositioned {
                    ownBounds[0] = it.boundsInWindow()
                    if (focused) FocusedField.bounds = ownBounds[0]
                }
        ) {
            field(TextStyle(color = scheme.onSurface), SolidColor(accent), focused)
        }
        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                error.orEmpty(),
                fontSize = 11.sp,
                color = scheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun FieldBody(
    empty: Boolean,
    placeholder: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    singleLine: Boolean,
    height: Dp,
    centered: Boolean,
    focused: Boolean,
    textStyle: TextStyle,
    inner: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val leadingTint by animateFloatAsState(if (focused) 1f else 0.75f, tween(180))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier.height(height) else Modifier.heightIn(min = height))
            .padding(
                start = if (leading != null) 10.dp else 14.dp,
                end = if (trailing != null) 6.dp else 14.dp,
                top = if (singleLine) 0.dp else 10.dp,
                bottom = if (singleLine) 0.dp else 10.dp
            )
    ) {
        if (leading != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(end = 8.dp).alpha(leadingTint)
            ) { leading() }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
        ) {
            if (empty && placeholder != null) {
                Text(
                    placeholder,
                    style = textStyle,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            inner()
        }
        if (trailing != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 6.dp)
            ) { trailing() }
        }
    }
}
