package com.tbdfit.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.localstorage.LocalRecordEntity
import kotlinx.coroutines.launch
import java.util.UUID

// Foundation-only entry point; no workout feature exists yet. This screen exists only to prove
// the local-persistence slice (create -> durable commit -> read back), not as product UI.
class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.build(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LocalRecordsScreen(dao = database.localRecordDao())
                }
            }
        }
    }
}

@Composable
private fun LocalRecordsScreen(dao: LocalRecordDao) {
    val scope = rememberCoroutineScope()
    val records by dao.getAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "TBDFit — phone local persistence proof")
        Button(onClick = {
            scope.launch {
                dao.insert(
                    LocalRecordEntity(
                        id = UUID.randomUUID().toString(),
                        createdAt = System.currentTimeMillis(),
                        value = "created at ${System.currentTimeMillis()}",
                    )
                )
            }
        }) {
            Text("Create record")
        }
        LazyColumn {
            items(records) { record ->
                Text("${record.id.take(8)} · ${record.createdAt} · ${record.value}")
            }
        }
    }
}
