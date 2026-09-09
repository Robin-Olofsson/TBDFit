package com.tbdfit.phone.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.localstorage.LocalRecordEntity
import com.tbdfit.phone.sync.LocalRecordSyncCoordinator
import com.tbdfit.phone.wearreplication.WearReplicaDao
import kotlinx.coroutines.launch
import java.util.UUID

// REAL, production-backed technical-proof screen — unchanged behavior, relocated out of
// MainActivity's old always-visible top-level layout (see MainActivity.kt's own top comment: it was
// written before any product IA existed) and into You → Settings → Developer, since the corrected
// product IA has no primary-navigation place for local-persistence/Supabase-sync/Wear-replica proof
// UI. Still fully reachable — not deleted, not replaced with prototype data.
@Composable
internal fun DeveloperSyncProofScreen(
    dao: LocalRecordDao,
    syncCoordinator: LocalRecordSyncCoordinator,
    wearReplicaDao: WearReplicaDao,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val records by dao.getAll().collectAsState(initial = emptyList())
    val wearReplicas by wearReplicaDao.getAll().collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
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

        Text(text = "Received from Wear")
        LazyColumn {
            items(wearReplicas) { replica ->
                Text("${replica.id.take(8)} · ${replica.createdAt} · received ${replica.receivedAt}")
            }
        }
    }
}
