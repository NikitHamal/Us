package com.us.copilot.data.repository

import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.BigFive
import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.model.Speaker
import com.us.copilot.data.local.db.Converters
import com.us.copilot.data.local.entity.CheckInEntity
import com.us.copilot.data.local.entity.MemoryEntity
import com.us.copilot.data.local.entity.ProfileEntity

/** Entity ⇄ domain mapping. Kept in one small file so the layers stay decoupled. */

internal inline fun <reified E : Enum<E>> String?.toEnum(fallback: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: fallback

fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    owner = owner.toEnum(ProfileOwner.ME),
    name = name,
    attachmentStyle = attachmentStyle.toEnum(AttachmentStyle.UNKNOWN),
    loveLanguages = Converters.decodeList(loveLanguages)
        .mapNotNull { value -> LoveLanguage.entries.firstOrNull { it.name == value } },
    conflictStyle = conflictStyle.toEnum(ConflictStyle.UNKNOWN),
    triggers = Converters.decodeList(triggers),
    soothers = Converters.decodeList(soothers),
    bigFive = BigFive(openness, conscientiousness, extraversion, agreeableness, neuroticism),
    stressPatterns = Converters.decodeList(stressPatterns),
    commPreferences = Converters.decodeList(commPreferences),
    note = note,
    version = version,
    isActive = isActive,
    updatedAt = updatedAt,
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    owner = owner.name,
    name = name,
    attachmentStyle = attachmentStyle.name,
    loveLanguages = Converters.encodeList(loveLanguages.map { it.name }),
    conflictStyle = conflictStyle.name,
    triggers = Converters.encodeList(triggers),
    soothers = Converters.encodeList(soothers),
    openness = bigFive.openness,
    conscientiousness = bigFive.conscientiousness,
    extraversion = bigFive.extraversion,
    agreeableness = bigFive.agreeableness,
    neuroticism = bigFive.neuroticism,
    stressPatterns = Converters.encodeList(stressPatterns),
    commPreferences = Converters.encodeList(commPreferences),
    note = note,
    version = version,
    isActive = isActive,
    updatedAt = updatedAt,
)

fun MemoryEntity.toDomain(): Memory = Memory(
    id = id,
    text = text,
    emotion = emotion.toEnum(Emotion.NEUTRAL),
    intensity = intensity,
    timestamp = timestamp,
    source = source.toEnum(MemorySource.MANUAL),
    speaker = speaker.toEnum(Speaker.BOTH),
    tags = Converters.decodeList(tags),
    isUnresolved = isUnresolved,
    resolvedAt = resolvedAt,
    embedding = Converters.decodeEmbedding(embedding),
    appPackage = appPackage,
)

fun Memory.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    text = text,
    emotion = emotion.name,
    intensity = intensity,
    timestamp = timestamp,
    source = source.name,
    speaker = speaker.name,
    tags = Converters.encodeList(tags),
    isUnresolved = isUnresolved,
    resolvedAt = resolvedAt,
    embedding = Converters.encodeEmbedding(embedding),
    appPackage = appPackage,
)

fun CheckInEntity.toDomain(): CheckIn = CheckIn(
    id = id,
    epochDay = epochDay,
    mood = mood,
    energy = energy,
    connection = connection,
    note = note,
    gratitude = gratitude,
    createdAt = createdAt,
)

fun CheckIn.toEntity(existingId: Long = id): CheckInEntity = CheckInEntity(
    id = existingId,
    epochDay = epochDay,
    mood = mood,
    energy = energy,
    connection = connection,
    note = note,
    gratitude = gratitude,
    createdAt = createdAt,
)
