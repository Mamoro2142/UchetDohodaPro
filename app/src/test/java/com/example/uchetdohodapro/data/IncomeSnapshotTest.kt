package com.example.uchetdohodapro.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class IncomeSnapshotTest {
    private val shift = ShiftEntity(id = 2, startDate = "16.11.2026", plannedWorkDays = 20)
    private val day = WorkDayEntity(id = 1, date = "2026-11-24", picks = 8.0, picksRate = 1000.0, shiftId = 2)

    @Test fun firstSnapshotWaitsForAllTables() = runBlocking {
        val pauses = MutableSharedFlow<List<ShiftPauseEntity>>(replay = 1)
        val stream = observeIncomeSnapshots(flowOf(listOf(day)), flowOf(listOf(shift)),
            flowOf(emptyList()), pauses)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val cached: StateFlow<IncomeSnapshot?> = stream.stateIn(scope, SharingStarted.Eagerly, null)
            check(withTimeoutOrNull(150) { cached.filterNotNull().first() } == null)
            pauses.emit(emptyList())
            val loaded = withTimeout(5000) { cached.filterNotNull().first() }
            check(loaded.shifts.single() == shift)
            check(loaded.days.single().calculateIncome() == 8000.0)
            check(loaded.pauses.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun repeatedScreenReadsKeepLatestDataAndDoNotRestartQueries() = runBlocking {
        val subscriptions = AtomicInteger(0)
        val days = MutableStateFlow(listOf(day))
        val shifts = MutableStateFlow(listOf(shift))
        val operations = MutableStateFlow<List<MoneyOperationEntity>>(emptyList())
        val pauses = MutableStateFlow<List<ShiftPauseEntity>>(emptyList())
        fun <T> counted(flow: Flow<T>): Flow<T> = flow.onStart { subscriptions.incrementAndGet() }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val cached: StateFlow<IncomeSnapshot?> = observeIncomeSnapshots(
                counted(days), counted(shifts), counted(operations), counted(pauses)
            ).stateIn(scope, SharingStarted.Eagerly, null)
            withTimeout(5000) { cached.filterNotNull().first() }
            repeat(30) {
                val current = checkNotNull(cached.first())
                check(current.shifts.single().isActive)
                check(current.days.sumOf { it.calculateIncome() } == 8000.0)
            }
            check(subscriptions.get() == 4)
            days.value = listOf(day.copy(picks = 9.0))
            operations.value = listOf(MoneyOperationEntity(shiftId = 2, date = "2026-11-24", type = ADVANCE, amountMinor = 10000))
            pauses.value = listOf(ShiftPauseEntity(shiftId = 2, startDate = "2026-11-25"))
            val changed = withTimeout(5000) { cached.filterNotNull().first {
                it.days.singleOrNull()?.picks == 9.0 && it.operations.isNotEmpty() && it.pauses.isNotEmpty()
            } }
            check(changed.days.single().calculateIncome() == 9000.0)
            shifts.value = listOf(shift.copy(isActive = false, endDate = "25.11.2026"))
            withTimeout(5000) { cached.filterNotNull().first { it.shifts.none { shift -> shift.isActive } } }
            days.value = emptyList()
            withTimeout(5000) { cached.filterNotNull().first { it.days.isEmpty() } }
            check(subscriptions.get() == 4)
        } finally { scope.cancel() }
    }

    @Test fun trulyEmptyDatabaseIsAValidLoadedSnapshot() = runBlocking {
        val result = withTimeout(5000) {
            observeIncomeSnapshots(flowOf(emptyList()), flowOf(emptyList()), flowOf(emptyList()), flowOf(emptyList())).first()
        }
        check(result == IncomeSnapshot(emptyList(), emptyList(), emptyList(), emptyList()))
    }
}
