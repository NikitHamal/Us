package com.us.copilot.domain

import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.domain.usecase.GetRepairStartersUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairStartersTest {

    private val useCase = GetRepairStartersUseCase()

    @Test
    fun `returns universal starters without a profile`() {
        val starters = useCase(null, limit = 4)
        assertEquals(4, starters.size)
        assertTrue(starters.all { it.text.isNotBlank() })
    }

    @Test
    fun `never returns duplicates`() {
        val partner = Profile.empty(ProfileOwner.PARTNER).copy(
            attachmentStyle = AttachmentStyle.ANXIOUS,
            conflictStyle = ConflictStyle.WITHDRAWER,
            loveLanguages = listOf(LoveLanguage.WORDS),
        )
        val starters = useCase(partner, limit = 20)
        assertEquals(starters.size, starters.distinctBy { it.text }.size)
    }

    @Test
    fun `respects the limit`() {
        val partner = Profile.empty(ProfileOwner.PARTNER).copy(
            attachmentStyle = AttachmentStyle.AVOIDANT,
        )
        assertEquals(3, useCase(partner, limit = 3).size)
    }
}
