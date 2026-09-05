package com.us.copilot.domain.usecase

import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.Profile
import javax.inject.Inject

/** A repair starter is one sentence you can actually send right now. */
data class RepairStarter(val category: String, val text: String, val when_: String)

/**
 * Adapted from the Gottman repair checklist, then filtered by the partner's attachment and
 * conflict style so the suggestions fit the person, not a generic couple.
 */
class GetRepairStartersUseCase @Inject constructor() {

    operator fun invoke(partner: Profile?, limit: Int = 6): List<RepairStarter> {
        val base = universal.toMutableList()
        partner?.attachmentStyle?.let { base += byAttachment[it].orEmpty() }
        partner?.conflictStyle?.let { base += byConflict[it].orEmpty() }
        partner?.primaryLoveLanguage?.let { base += byLoveLanguage[it].orEmpty() }
        return base.distinctBy { it.text }.shuffled().take(limit)
    }

    private val universal = listOf(
        RepairStarter("Slow it down", "Can we take a breath? I do not want to say something I will regret.", "Mid-argument"),
        RepairStarter("Own your part", "You are right about that part. I am sorry.", "When you were wrong"),
        RepairStarter("Name the feeling", "I am not angry at you. I am scared we are drifting.", "When it turns cold"),
        RepairStarter("Ask, do not assume", "Help me understand what that felt like from your side.", "When you are confused"),
        RepairStarter("Reconnect", "I love you and I hate fighting with you. Can we restart this conversation?", "After the heat"),
        RepairStarter("Time-out with a promise", "I need twenty minutes to calm down. I will come back to this at nine.", "When flooded"),
    )

    private val byAttachment = mapOf(
        AttachmentStyle.ANXIOUS to listOf(
            RepairStarter("Reassure first", "We are okay. I am not going anywhere. Can we talk about it now?", "Before problem-solving"),
            RepairStarter("Close the loop", "I saw your message and I am thinking about it. I will reply properly in an hour.", "When you cannot reply yet"),
        ),
        AttachmentStyle.AVOIDANT to listOf(
            RepairStarter("Give space, keep the thread", "Take the time you need. I am here when you are ready, no pressure.", "When she withdraws"),
            RepairStarter("Make it small", "One thing only, then we drop it. Deal?", "Before a hard talk"),
        ),
        AttachmentStyle.FEARFUL to listOf(
            RepairStarter("Be predictable", "Nothing about this changes how I feel about you. Let us go slowly.", "When it feels unsafe"),
        ),
        AttachmentStyle.SECURE to listOf(
            RepairStarter("Straight and warm", "Here is what bothered me, and here is what I want for us.", "Anytime"),
        ),
    )

    private val byConflict = mapOf(
        ConflictStyle.WITHDRAWER to listOf(
            RepairStarter("Lower the stakes", "You do not have to solve it now. I just wanted you to know how I felt.", "When she goes quiet"),
        ),
        ConflictStyle.ENGAGER to listOf(
            RepairStarter("Agree to finish it", "Let us finish this tonight so neither of us sleeps on it.", "When she needs closure"),
        ),
        ConflictStyle.ACCOMMODATOR to listOf(
            RepairStarter("Invite her truth", "Do not agree just to keep the peace. I want the real answer.", "When she gives in too fast"),
        ),
        ConflictStyle.VOLATILE to listOf(
            RepairStarter("Ride it out kindly", "I can handle your big feelings. Say all of it, I am not leaving.", "During the storm"),
        ),
        ConflictStyle.ANALYST to listOf(
            RepairStarter("Feelings before facts", "I do not need to be right here. I need us to feel okay again.", "When it turns into a debate"),
        ),
    )

    private val byLoveLanguage = mapOf(
        LoveLanguage.WORDS to listOf(
            RepairStarter("Say it out loud", "Even in the middle of this, I am glad it is you.", "After a fight"),
        ),
        LoveLanguage.QUALITY_TIME to listOf(
            RepairStarter("Offer presence", "Phones away, thirty minutes, just us. Tonight?", "After a fight"),
        ),
        LoveLanguage.ACTS to listOf(
            RepairStarter("Do the thing", "I already handled the thing you were dreading. One less weight today.", "When words are not landing"),
        ),
        LoveLanguage.TOUCH to listOf(
            RepairStarter("Close the distance", "Can I just hold you for a minute before we talk?", "When words are not landing"),
        ),
        LoveLanguage.GIFTS to listOf(
            RepairStarter("Small proof", "I got you the thing you mentioned last week. I was listening.", "After a fight"),
        ),
    )
}
