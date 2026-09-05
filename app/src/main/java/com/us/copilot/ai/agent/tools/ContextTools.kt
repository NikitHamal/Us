package com.us.copilot.ai.agent.tools

import com.us.copilot.ai.agent.AgentTool
import com.us.copilot.ai.agent.ToolResult
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.domain.repository.CheckInRepository
import com.us.copilot.domain.repository.NotificationRepository
import com.us.copilot.domain.repository.MemoryRepository
import com.us.copilot.domain.repository.ProfileRepository
import com.us.copilot.pattern.PatternEngine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** Reads the structured psychological profiles. */
class ReadProfileTool @Inject constructor(
    private val profiles: ProfileRepository,
) : AgentTool {

    override val name = "read_profile"

    override val description =
        "Read a structured psychological profile: attachment style, love languages, conflict " +
            "style, triggers, soothers, Big Five, stress patterns. Use this before giving advice " +
            "so it fits how this specific person actually works."

    override val parameters = schema(
        "owner" to stringProperty("Whose profile: ME or PARTNER."),
        required = listOf("owner"),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val owner = arguments.stringOr("owner")
            ?.let { name -> ProfileOwner.entries.firstOrNull { it.name.equals(name, true) } }
            ?: return ToolResult.Failure("owner must be ME or PARTNER")

        val profile = profiles.get(owner)
            ?: return ToolResult.Success("No profile saved for $owner yet.")

        return ToolResult.Success(
            buildString {
                appendLine("Profile: ${owner.name}")
                appendLine("Name: ${profile.name.ifBlank { "(unnamed)" }}")
                appendLine("Attachment: ${profile.attachmentStyle.label}")
                appendLine(
                    "Love languages (ranked): " +
                        profile.loveLanguages.joinToString(", ") { it.label }
                            .ifBlank { "not set" },
                )
                appendLine("Conflict style: ${profile.conflictStyle.label}")
                appendLine("Triggers: " + profile.triggers.joinToString(", ").ifBlank { "none" })
                appendLine("Soothers: " + profile.soothers.joinToString(", ").ifBlank { "none" })
                appendLine(
                    "Stress patterns: " +
                        profile.stressPatterns.joinToString(", ").ifBlank { "none" },
                )
                append(
                    "Communication preferences: " +
                        profile.commPreferences.joinToString(", ").ifBlank { "none" },
                )
            },
        )
    }
}

/** Surfaces computed relationship patterns rather than making the model infer them. */
class ReadPatternsTool @Inject constructor(
    private val patternEngine: PatternEngine,
    private val memories: MemoryRepository,
    private val checkIns: CheckInRepository,
    private val profiles: ProfileRepository,
) : AgentTool {

    override val name = "read_patterns"

    override val description =
        "Get computed relationship patterns: conflict frequency and trend, recurring triggers, " +
            "Gottman Four Horsemen counts, repair-attempt ratio. These are calculated from real " +
            "data, so prefer them over guessing at trends."

    override val parameters = schema()

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val summary = patternEngine.summarise(
            memories = memories.all(),
            checkIns = checkIns.observeRecent(90).first(),
            partner = profiles.get(ProfileOwner.PARTNER),
        )
        if (!summary.hasData) return ToolResult.Success("Not enough data to detect patterns yet.")

        return ToolResult.Success(
            buildString {
                appendLine("Total moments: ${summary.totalMemories}")
                appendLine("Unresolved: ${summary.unresolvedCount}")
                appendLine("Conflicts last 30d: ${summary.conflictsLast30Days}")
                appendLine("Conflicts previous 30d: ${summary.conflictsPrevious30Days}")
                appendLine("Delta: ${summary.conflictDelta}")
                appendLine("Repair attempts: ${summary.repairAttempts}")
                appendLine(
                    "Positive:negative ratio: %.1f (Gottman 5:1 met: %s)".format(
                        summary.positiveToNegativeRatio,
                        summary.meetsMagicRatio,
                    ),
                )
                appendLine(
                    "Top triggers: " +
                        summary.topTriggers.joinToString(", ") { "${it.trigger} x${it.count}" }
                            .ifBlank { "none detected" },
                )
                append(
                    "Four Horsemen: " +
                        summary.horsemen.joinToString(", ") { "${it.horseman.label}=${it.count}" }
                            .ifBlank { "none detected" },
                )
            },
        )
    }
}

/** Recent mood check-ins, for spotting drift the user has not named. */
class ReadCheckInsTool @Inject constructor(
    private val checkIns: CheckInRepository,
) : AgentTool {

    override val name = "read_check_ins"

    override val description =
        "Read recent daily mood check-ins (mood, energy, connection ratings out of 5). Use this " +
            "to see how the user has actually been feeling over time."

    override val parameters = schema(
        "limit" to intProperty("How many recent check-ins. Default 14, max 60."),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val limit = arguments.intOr("limit", default = 14).coerceIn(1, 60)
        val items = checkIns.observeRecent(limit).first()
        if (items.isEmpty()) return ToolResult.Success("No check-ins recorded yet.")
        return ToolResult.Success(
            items.joinToString("\n") { checkIn ->
                "- day ${checkIn.epochDay}: mood=${checkIn.mood}/5 " +
                    "energy=${checkIn.energy}/5 connection=${checkIn.connection}/5" +
                    checkIn.note.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
            },
        )
    }
}

/**
 * Reads notifications the user explicitly shared.
 *
 * This tool can only ever see entries with `sharedWithAi = true`. Captured-but-unshared
 * notifications are invisible to the model by construction, not by prompt instruction.
 */
class ReadSharedNotificationsTool @Inject constructor(
    private val notifications: NotificationRepository,
) : AgentTool {

    override val name = "read_shared_notifications"

    override val description =
        "Read messages the user explicitly shared with you from their notification history. " +
            "You cannot see anything they have not shared. Use this for real conversational " +
            "context when the user refers to something that was said."

    override val parameters = schema(
        "limit" to intProperty("How many shared messages. Default 20, max 50."),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val limit = arguments.intOr("limit", default = 20).coerceIn(1, 50)
        val items = notifications.contextForAi(limit)
        if (items.isEmpty()) {
            return ToolResult.Success(
                "The user has not shared any notifications with you.",
            )
        }
        return ToolResult.Success(
            items.joinToString("\n") { item ->
                "- [${TimeUtils.relative(item.postedAt)}] ${item.appLabel}" +
                    item.title.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty() +
                    ": ${item.text.replace('\n', ' ').take(240)}"
            },
        )
    }
}
