package com.tbdfit.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.tbdfit.wear.localstorage.AppDatabase
import com.tbdfit.wear.localstorage.LocalRecordDao
import com.tbdfit.wear.localstorage.LocalRecordEntity
import com.tbdfit.wear.replication.WearReplicationCoordinator
import com.tbdfit.wear.replication.transport.DataClientWearRecordTransport
import kotlinx.coroutines.launch
import java.util.UUID

// Foundation-only entry point; no workout feature exists yet. This screen exists only to prove the
// Wear local-persistence and Wear-to-Phone replication slices, independent of Supabase/backend.
class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var replicationCoordinator: WearReplicationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.build(applicationContext)
        replicationCoordinator = WearReplicationCoordinator(
            dao = database.localRecordDao(),
            transport = DataClientWearRecordTransport(applicationContext),
        )

        setContent {
            MaterialTheme {
                LocalRecordsScreen(dao = database.localRecordDao(), replicationCoordinator = replicationCoordinator)
            }
        }
    }
}

@Composable
private fun LocalRecordsScreen(dao: LocalRecordDao, replicationCoordinator: WearReplicationCoordinator) {
    val scope = rememberCoroutineScope()
    val records by dao.getAll().collectAsState(initial = emptyList())

    ScalingLazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
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
        }
        item {
            Button(onClick = { scope.launch { replicationCoordinator.replicate() } }) {
                Text("Replicate to phone")
            }
        }
        items(records) { record ->
            val status = if (record.phoneSyncedAt != null) "on phone" else "pending"
            Text("${record.id.take(8)} · $status")
        }
    }
}
