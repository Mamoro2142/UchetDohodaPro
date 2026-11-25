package com.example.uchetdohodapro

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uchetdohodapro.data.IncomeSnapshot
import com.example.uchetdohodapro.data.WorkDatabase
import com.example.uchetdohodapro.data.observeIncomeSnapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal sealed interface IncomeLoadState {
    object Loading : IncomeLoadState
    object Failed : IncomeLoadState
    data class Ready(val data: IncomeSnapshot) : IncomeLoadState
}

/** Activity-scoped: one observer for each table, independent of the selected screen. */
internal class IncomeDataViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = WorkDatabase.getInstance(application).workDao()
    private val _state = MutableStateFlow<IncomeLoadState>(IncomeLoadState.Loading)
    val state = _state.asStateFlow()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        _state.value = IncomeLoadState.Loading
        observation = viewModelScope.launch {
            try {
                dao.observeIncomeSnapshots().collect { snapshot ->
                    _state.value = IncomeLoadState.Ready(snapshot)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = IncomeLoadState.Failed
            }
        }
    }
}

internal val LocalIncomeSnapshot = compositionLocalOf<IncomeSnapshot> {
    error("IncomeDataHost must provide a loaded snapshot")
}

@Composable
internal fun IncomeDataHost(content: @Composable () -> Unit) {
    val model: IncomeDataViewModel = viewModel()
    val state by model.state.collectAsState()
    when (val current = state) {
        is IncomeLoadState.Ready -> CompositionLocalProvider(LocalIncomeSnapshot provides current.data) {
            content()
        }
        IncomeLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Загрузка данных…")
            }
        }
        IncomeLoadState.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Не удалось загрузить данные")
                Button(onClick = model::reload) { Text("Повторить") }
            }
        }
    }
}
