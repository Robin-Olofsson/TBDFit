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
import com.tbdfit.phone.backend.localrecords.SupabaseLocalRecordRemoteStore
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.localstorage.LocalRecordEntity
import com.tbdfit.phone.sync.LocalRecordSyncCoordinator
import kotlinx.coroutines.launch
import java.util.UUID

// Foundation-only entry point; no workout feature exists yet. This screen exists only to prove
// the local-persistence and local-to-Supabase sync slices, not as product UI.
class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var syncCoordinator: LocalRecordSyncCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.build(applicationContext)
        syncCoordinator = LocalRecordSyncCoordinator(
            dao = database.localRecordDao(),
            remoteStore = SupabaseLocalRecordRemoteStore(),
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LocalRecordsScreen(dao = database.localRecordDao(), syncCoordinator = syncCoordinator)
                }
            }
        }
    }
}

@Composable
private fun LocalRecordsScreen(dao: LocalRecordDao, syncCoordinator: LocalRecordSyncCoordinator) {
    val scope = rememberCoroutineScope()
    val records by dao.getAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "TBDFit — phone local persistence + sync proof")
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
        Button(onClick = { scope.launch { syncCoordinator.sync() } }) {
            Text("Sync now")
        }
        LazyColumn {
            items(records) { record ->
                val status = if (record.syncedAt != null) "synced" else "pending"
                Text("${record.id.take(8)} · ${record.createdAt} · ${record.value} · $status")
            }
        }
    }
}
