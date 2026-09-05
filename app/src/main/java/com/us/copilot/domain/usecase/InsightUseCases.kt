package com.us.copilot.domain.usecase

import com.us.copilot.ai.LlmRouter
import com.us.copilot.ai.model.PatternEntry
import com.us.copilot.ai.model.PatternReport
import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.model.InsightSummary
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.util.Outcome
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.domain.repository.CheckInRepository
import com.us.copilot.domain.repository.MemoryRepository
import com.us.copilot.domain.repository.ProfileRepository
import com.us.copilot.pattern.PatternEngine
import javax.inject.Inject

class BuildInsightsUseCase @Inject constructor(
    private val memories: MemoryRepository,
    private val checkIns: CheckInRepository,
    private val profiles: ProfileRepository,
    private val engine: PatternEngine,
) {
    data class Insights(
        val summary: InsightSummary,
        val cadence: PatternEngine.Cadence,
        val partner: Profile?,
    )

    suspend operator fun invoke(): Insights {
        val allMemories = memories.all()
        val today = TimeUtils.epochDay(System.currentTimeMillis())
        val recentCheckIns = checkIns.range(today - 60, today)
        val partner = profiles.get(ProfileOwner.PARTNER)
        return Insights(
            summary = engine.summarise(allMemories, recentCheckIns, partner),
            cadence = engine.cadence(allMemories),
            partner = partner,
        )
    }
}

/** Asks the AI layer for narrative patterns on top of the deterministic numbers. */
class ExtractPatternsUseCase @Inject constructor(
    private val memories: MemoryRepository,
    private val router: LlmRouter,
    private val buildContext: BuildProfileContextUseCase,
) {
    suspend operator fun invoke(maxEntries: Int = 120): Outcome<PatternReport> {
        val entries = memories.all().takeLast(maxEntries).map { memory ->
            PatternEntry(
                text = memory.text,
                timestamp = memory.timestamp,
                emotion = memory.emotion.name,
                speaker = memory.speaker.name,
                isUnresolved = memory.isUnresolved,
            )
        }
        return router.extractPatterns(
            PatternRequest(entries = entries, partner = buildContext(ProfileOwner.PARTNER)),
        )
    }
}

class SaveCheckInUseCase @Inject constructor(
    private val repository: CheckInRepository,
) {
    suspend operator fun invoke(checkIn: CheckIn) = repository.upsert(checkIn)
}

class ObserveTodayCheckInUseCase @Inject constructor(
    private val repository: CheckInRepository,
) {
    operator fun invoke(now: Long = System.currentTimeMillis()) =
        repository.observeToday(TimeUtils.epochDay(now))
}

class ObserveRecentCheckInsUseCase @Inject constructor(
    private val repository: CheckInRepository,
) {
    operator fun invoke(limit: Int = 30) = repository.observeRecent(limit)
}

/** Wipes every trace of user data. Used by Settings → Delete everything. */
class WipeAllDataUseCase @Inject constructor(
    private val memories: MemoryRepository,
    private val checkIns: CheckInRepository,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke() {
        memories.deleteAll()
        checkIns.deleteAll()
        profiles.deleteAll()
    }
}
