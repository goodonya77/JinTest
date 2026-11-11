package com.jintest.exchangecalc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ＋를 누르면 아래에서 올라오는 통화 선택 목록 (검색 가능). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCurrencySheet(options: List<Currency>, onPick: (Currency) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = options.filter { it.matches(query) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("통화 추가", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("통화 이름·코드 검색") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(filtered, key = { it.code }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.flag, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(c.code, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            c.koreanName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.Add, contentDescription = "추가", tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "추가할 수 있는 통화가 없어요",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}
