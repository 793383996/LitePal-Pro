package org.litepal.litepalsample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
internal fun AggregateScreen(uiState: SampleDataUiState, viewModel: SampleDataViewModel) {
    var prefix by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard {
            Text("Aggregate over Singer.age", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = prefix,
                onValueChange = { prefix = it },
                label = { Text("Name prefix (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.refreshAggregate(prefix.trim()) }
                ) { Text("Calculate") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.refreshAggregate() }
                ) { Text("All Data") }
            }
        }

        SectionCard {
            val summary = uiState.aggregateSummary
            Text("Summary", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Count: ${summary.count}")
            Text("Max: ${summary.max}")
            Text("Min: ${summary.min}")
            Text("Sum: ${summary.sum}")
            Text("Avg: ${"%.2f".format(summary.avg)}")
        }
    }
}
