package com.example.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BeatifyAppleCrimson
import com.example.ui.theme.HighlightFavoritePink
import com.example.ui.theme.LabelPrimary
import com.example.ui.theme.SecondaryTextZinc
import kotlinx.coroutines.delay

/**
 * Reusable, responsive `SearchBar` component with a built-in coroutine debounce function
 * for querying music news/releases, YouTube channel RSS feeds, and YouTube Music metadata.
 *
 * Features:
 * - Immediate UI text updates via [onQueryChange] paired with debounced query emission via [onDebouncedQuery].
 * - Fixed 22.dp leading icon slot that swaps between the Search icon and a loading spinner without layout shift.
 * - Accessible 48.dp minimum touch targets for Clear and Quick/Voice actions.
 * - Compact single-line layout that adapts cleanly to small and wide viewports.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDebouncedQuery: (String) -> Unit = {},
    debounceMs: Long = 350L,
    onSearchSubmit: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    onQuickSuggestClick: () -> Unit = {},
    isLoading: Boolean = false,
    isError: Boolean = false,
    placeholder: String = "Search YouTube channels, music news & songs...",
    compactPlaceholder: String = "Channels, news, songs..."
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val currentOnDebouncedQuery by rememberUpdatedState(onDebouncedQuery)

    // Coroutine debounce function for querying news and YouTube content
    LaunchedEffect(query, debounceMs) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            currentOnDebouncedQuery("")
        } else {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            currentOnDebouncedQuery(cleaned)
        }
    }

    val borderColor = when {
        isError -> HighlightFavoritePink
        isFocused -> BeatifyAppleCrimson
        else -> Color.White.copy(alpha = 0.08f)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactPhone = maxWidth < 360.dp
        val searchBarHeight = 52.dp
        val horizontalPadding = if (isCompactPhone) 10.dp else 14.dp
        val elementGap = if (isCompactPhone) 8.dp else 12.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(searchBarHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF1C1C1E))
                .border(1.dp, borderColor, RoundedCornerShape(999.dp))
                .padding(horizontal = horizontalPadding)
                .testTag("responsive_music_search_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(elementGap)
        ) {
            // Leading Search / Loading Indicator Slot (Fixed 22.dp width so bar dimensions never jump)
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BeatifyAppleCrimson,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("search_loading_spinner")
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = if (isFocused) BeatifyAppleCrimson else SecondaryTextZinc,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Vertically centered single-line input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = if (isCompactPhone) compactPlaceholder else placeholder,
                        color = SecondaryTextZinc,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { updated ->
                        onQueryChange(updated)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(BeatifyAppleCrimson),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSearchSubmit(query.trim())
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            onFocusChange(focusState.isFocused)
                        }
                        .testTag("search_screen_query_input")
                )
            }

            // Trailing Clear or Quick/Voice Action Button (48.dp touch target)
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        currentOnDebouncedQuery("")
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .size(48.dp)
                        .testTag("search_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Search Query",
                        tint = LabelPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onQuickSuggestClick,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .size(48.dp)
                        .testTag("search_voice_or_quick_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Quick Search Suggestion",
                        tint = SecondaryTextZinc,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
