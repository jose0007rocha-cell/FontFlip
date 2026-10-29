package app.fontflip.ui

import android.content.ComponentName
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fontflip.FontViewModel
import app.fontflip.R
import app.fontflip.UiEvent
import app.fontflip.install.FontStatus
import app.fontflip.install.SystemActions
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontFlipApp(vm: FontViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sample by rememberSaveable { mutableStateOf(context.getString(R.string.sample_default)) }

    var showPick by remember { mutableStateOf(false) }
    val candidates = remember(showPick) { if (showPick) SystemActions.restoreCandidates(context) else emptyList() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.importFonts(it)
    }
    val uninstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onUninstallResult()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Message ->
                    scope.launch { snackbar.showSnackbar(context.getString(event.res, *event.args.toTypedArray())) }
                is UiEvent.Install ->
                    if (!SystemActions.install(context, event.apk)) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.allow_install)) }
                    }
                UiEvent.Uninstall -> uninstaller.launch(SystemActions.uninstallIntent())
                UiEvent.OpenRestore -> {
                    Toast.makeText(context, context.getString(R.string.toast_restore), Toast.LENGTH_LONG).show()
                    SystemActions.openBackupRestore(context, vm.restoreTarget())
                }
            }
        }
    }

    if (showPick) {
        PickDialog(
            candidates = candidates,
            onPick = { cn ->
                if (SystemActions.openComponent(context, cn)) {
                    vm.saveRestoreTarget(cn?.flattenToString() ?: SystemActions.PRIVACY)
                    Toast.makeText(context, cn?.className ?: context.getString(R.string.pick_privacy), Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showPick = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_font)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item {
                FlowCard(
                    status = state.status,
                    backupDone = state.backupDone,
                    onStore = {
                        if (!SystemActions.openGalaxyStore(context)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.err_store)) }
                        }
                    },
                    onFontStyle = {
                        if (!SystemActions.openFontStyle(context)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.err_cloud)) }
                        }
                    },
                    onPick = { showPick = true },
                    onCloud = {
                        if (!SystemActions.openBackupRestore(context, vm.restoreTarget())) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.err_cloud)) }
                        }
                    },
                    onBackupDone = vm::setBackupDone,
                    onUninstall = vm::uninstallCurrent,
                )
            }
            item { HowItWorks() }
            item {
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = { Text(stringResource(R.string.sample_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(stringResource(R.string.your_fonts), style = MaterialTheme.typography.titleMedium)
            }
            if (state.fonts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.empty_fonts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.fonts, key = { it.name }) { font ->
                    FontCard(
                        file = font,
                        sample = sample,
                        busy = state.busy,
                        onInstall = { vm.install(font) },
                        onDelete = { vm.delete(font) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickDialog(
    candidates: List<ComponentName>,
    onPick: (ComponentName?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.pick_title)) },
        text = {
            LazyColumn {
                item { Text(stringResource(R.string.pick_hint), style = MaterialTheme.typography.bodySmall) }
                item { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.pick_privacy)) } }
                items(candidates) { cn ->
                    TextButton(onClick = { onPick(cn) }) { Text(cn.className.substringAfterLast('.')) }
                }
            }
        },
    )
}

@Composable
private fun FlowCard(
    status: FontStatus,
    backupDone: Boolean,
    onStore: () -> Unit,
    onFontStyle: () -> Unit,
    onCloud: () -> Unit,
    onPick: () -> Unit,
    onBackupDone: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        status == FontStatus.NONE -> colors.errorContainer
        status == FontStatus.CUSTOM && backupDone -> colors.tertiaryContainer
        else -> colors.secondaryContainer
    }
    val icon = when {
        status == FontStatus.NONE -> Icons.Default.Warning
        status == FontStatus.CUSTOM && backupDone -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }
    val title = when (status) {
        FontStatus.NONE -> R.string.status_none_title
        FontStatus.ORIGINAL -> R.string.status_original_title
        FontStatus.CUSTOM -> R.string.status_custom_title
    }
    val body = when {
        status == FontStatus.NONE -> R.string.status_none_body
        !backupDone -> R.string.backup_hint
        status == FontStatus.ORIGINAL -> R.string.status_original_body
        else -> R.string.status_custom_body
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)

            when {
                status == FontStatus.NONE ->
                    Button(onClick = onStore) { Text(stringResource(R.string.btn_store)) }

                !backupDone -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onFontStyle) { Text(stringResource(R.string.btn_font_style)) }
                        OutlinedButton(onClick = onCloud) { Text(stringResource(R.string.btn_cloud)) }
                    }
                    TextButton(onClick = onPick) { Text(stringResource(R.string.pick_screen)) }
                    Row(
                        Modifier.fillMaxWidth().clickable { onBackupDone(true) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = false, onCheckedChange = { onBackupDone(true) })
                        Text(stringResource(R.string.backup_done), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                status == FontStatus.ORIGINAL ->
                    OutlinedButton(onClick = onFontStyle) { Text(stringResource(R.string.btn_font_style)) }

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCloud) { Text(stringResource(R.string.btn_restore)) }
                        OutlinedButton(onClick = onFontStyle) { Text(stringResource(R.string.btn_font_style)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPick) { Text(stringResource(R.string.pick_screen)) }
                        TextButton(onClick = onUninstall) { Text(stringResource(R.string.btn_uninstall)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.how_title), style = MaterialTheme.typography.titleSmall)
            if (open) Text(stringResource(R.string.how_steps), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FontCard(file: File, sample: String, busy: Boolean, onInstall: () -> Unit, onDelete: () -> Unit) {
    val family = remember(file.path, file.lastModified()) {
        runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                file.nameWithoutExtension,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                sample,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = family ?: FontFamily.Default),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.remove))
                }
                Button(onClick = onInstall, enabled = !busy) { Text(stringResource(R.string.install)) }
            }
        }
    }
}
