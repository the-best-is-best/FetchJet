package com.michelle.raouf.fetch_jet.domain.engine

import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.coroutines.flow.Flow

interface DownloadEngine {
    val engineType: EngineType
    fun observeTask(taskId: String): Flow<DownloadTask>
    suspend fun start(task: DownloadTask): Result<Unit>
    suspend fun pause(taskId: String): Result<Unit>
    suspend fun resume(taskId: String): Result<Unit>
    suspend fun cancel(taskId: String): Result<Unit>
}