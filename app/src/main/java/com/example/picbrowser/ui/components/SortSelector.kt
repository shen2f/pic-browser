package com.example.picbrowser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.picbrowser.data.model.SortDirection
import com.example.picbrowser.data.model.SortType

@Composable
fun SortSelector(
    sortType: SortType,
    sortDirection: SortDirection,
    onSortTypeChanged: (SortType) -> Unit,
    onSortDirectionToggled: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 排序类型 - 点击弹出菜单
        Column {
            Text(
                text = sortType.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        expanded = true
                    }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SortType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = type.displayName,
                                style = if (type == sortType) {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                }
                            )
                        },
                        onClick = {
                            onSortTypeChanged(type)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 排序方向箭头 - 点击切换
        Icon(
            imageVector = if (sortDirection == SortDirection.DESCENDING) {
                Icons.Default.ArrowDropDown
            } else {
                Icons.Default.ArrowDropUp
            },
            contentDescription = if (sortDirection == SortDirection.DESCENDING) {
                "降序"
            } else {
                "升序"
            },
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onSortDirectionToggled()
                }
        )
    }
}