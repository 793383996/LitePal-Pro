package org.litepal.litepalsample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CrudScreen(uiState: SampleDataUiState, viewModel: SampleDataViewModel) {
    var name by remember { mutableStateOf("compose_singer") }
    var age by remember { mutableStateOf("21") }
    var isMale by remember { mutableStateOf(true) }
    var updateId by remember { mutableStateOf("") }
    var updateAge by remember { mutableStateOf("30") }
    var deleteId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Text("Create Singer", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(modifier = Modifier.weight(1f), onClick = { isMale = true }) {
                        Text(if (isMale) "Male" else "Set Male")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { isMale = false }) {
                        Text(if (!isMale) "Female" else "Set Female")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val validAge = age.toIntOrNull() ?: return@Button
                        viewModel.saveSinger(name.trim(), validAge, isMale)
                    }
                ) {
                    Text("Save Singer")
                }
            }
        }

        item {
            SectionCard {
                Text("Update / Delete", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = updateId,
                        onValueChange = { updateId = it },
                        label = { Text("ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = updateAge,
                        onValueChange = { updateAge = it },
                        label = { Text("New Age") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val id = updateId.toLongOrNull() ?: return@Button
                            val newAge = updateAge.toIntOrNull() ?: return@Button
                            viewModel.updateSingerAge(id, newAge)
                        }
                    ) { Text("Update") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.refreshSingers() }
                    ) { Text("Refresh") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = deleteId,
                    onValueChange = { deleteId = it },
                    label = { Text("Delete ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val id = deleteId.toLongOrNull() ?: return@Button
                        viewModel.deleteSinger(id)
                    }
                ) { Text("Delete Singer") }
            }
        }

        item {
            Text("Recent Singers", fontWeight = FontWeight.Bold)
        }

        if (uiState.singers.isEmpty()) {
            item {
                Text("No singers yet. Create one above to start.")
            }
        } else {
            items(uiState.singers, key = { it.id }) { singer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "id=${singer.id}, ${singer.name}, age=${singer.age}, male=${singer.isMale}",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
