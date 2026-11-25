package com.example.uchetdohodapro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** No synthetic empty data: the first value is emitted only after all queries return. */
internal data class IncomeSnapshot(
    val days: List<WorkDayEntity>,
    val shifts: List<ShiftEntity>,
    val operations: List<MoneyOperationEntity>,
    val pauses: List<ShiftPauseEntity>
)

internal fun observeIncomeSnapshots(
    days: Flow<List<WorkDayEntity>>,
    shifts: Flow<List<ShiftEntity>>,
    operations: Flow<List<MoneyOperationEntity>>,
    pauses: Flow<List<ShiftPauseEntity>>
): Flow<IncomeSnapshot> = combine(days, shifts, operations, pauses, ::IncomeSnapshot)

internal fun WorkDao.observeIncomeSnapshots(): Flow<IncomeSnapshot> = observeIncomeSnapshots(
    observeWorkDays(), observeShifts(), observeMoneyOperations(), observePauses()
)
