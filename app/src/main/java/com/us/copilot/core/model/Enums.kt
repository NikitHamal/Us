package com.us.copilot.core.model

/** Whose profile a record belongs to. */
enum class ProfileOwner { ME, PARTNER }

/** Adult attachment styles (Bartholomew & Horowitz four-category model). */
enum class AttachmentStyle(val label: String, val blurb: String) {
    SECURE("Secure", "Comfortable with closeness and with space. Repairs quickly."),
    ANXIOUS("Anxious / Preoccupied", "Fears distance. Needs reassurance, protests disconnection."),
    AVOIDANT("Avoidant / Dismissive", "Values independence. Withdraws when flooded."),
    FEARFUL("Fearful / Disorganised", "Wants closeness and fears it at the same time."),
    UNKNOWN("Not sure yet", "We will learn this together over time.");
}

/** Chapman's five love languages. Stored ranked, most important first. */
enum class LoveLanguage(val label: String, val example: String) {
    WORDS("Words of Affirmation", "\"I noticed how hard you worked today.\""),
    QUALITY_TIME("Quality Time", "Phones down, twenty undivided minutes."),
    ACTS("Acts of Service", "Doing the thing she was dreading, before she asks."),
    GIFTS("Thoughtful Gifts", "Something small that proves you were listening."),
    TOUCH("Physical Touch", "A hand on her back while she talks.");
}

/** How a person behaves in the middle of a disagreement. */
enum class ConflictStyle(val label: String, val blurb: String) {
    ENGAGER("Engager", "Wants to solve it now, out loud."),
    WITHDRAWER("Withdrawer", "Goes quiet, needs to cool down before talking."),
    ACCOMMODATOR("Accommodator", "Gives in early to keep the peace, resents it later."),
    ANALYST("Analyst", "Debates facts and logic, can miss the feeling."),
    VOLATILE("Volatile", "Big feelings fast, warm again fast."),
    UNKNOWN("Not sure yet", "We will figure it out from real moments.");
}

/** Coarse emotional label attached to a memory. */
enum class Emotion(val label: String, val emoji: String, val isNegative: Boolean) {
    JOY("Joy", "\uD83D\uDE0A", false),
    LOVE("Love", "\u2764\uFE0F", false),
    CALM("Calm", "\uD83D\uDE0C", false),
    GRATITUDE("Gratitude", "\uD83D\uDE4F", false),
    NEUTRAL("Neutral", "\uD83D\uDE10", false),
    ANXIETY("Anxiety", "\uD83D\uDE1F", true),
    SADNESS("Sadness", "\uD83D\uDE22", true),
    ANGER("Anger", "\uD83D\uDE20", true),
    HURT("Hurt", "\uD83D\uDC94", true),
    LONELINESS("Loneliness", "\uD83C\uDF11", true),
    SHAME("Shame", "\uD83D\uDE14", true);
}

/** Where a memory came from. Nothing is ever scraped. */
enum class MemorySource(val label: String) {
    SHARE("Shared into Us"),
    JOURNAL("Journal"),
    MANUAL("Written by me"),
    NOTIFICATION("Notification capture"),
    CHECK_IN("Check-in");
}

/** Who said the thing being remembered. */
enum class Speaker(val label: String) { ME("Me"), PARTNER("Her"), BOTH("Both of us") }

/** Gottman's Four Horsemen plus their antidotes. */
enum class Horseman(val label: String, val definition: String, val antidote: String) {
    CRITICISM(
        "Criticism",
        "Attacking character instead of naming a behaviour.",
        "Describe what happened and what you need, using \"I\".",
    ),
    CONTEMPT(
        "Contempt",
        "Sarcasm, mockery, superiority. The strongest predictor of breakup.",
        "Build a culture of appreciation. Say what you respect out loud.",
    ),
    DEFENSIVENESS(
        "Defensiveness",
        "Counter-attacking or playing the victim instead of hearing it.",
        "Accept your part, even if it is only ten percent.",
    ),
    STONEWALLING(
        "Stonewalling",
        "Shutting down and going silent, usually from being flooded.",
        "Ask for a twenty minute break, and promise a time to return.",
    );
}

/** The three rewrite voices the coach offers. */
enum class RephraseStyle(val label: String, val intent: String) {
    SOFT("Soft", "Gentle start-up, protects her nervous system."),
    DIRECT("Direct", "Clear and honest without blame."),
    PLAYFUL("Playful", "Warm humour to lower the temperature.");
}

/** Which engine answered a request. */
enum class ProviderId(val label: String) {
    OFFLINE("On-device"),
    NEBIANS("Nebians"),
    CLOUD("Cloud model"),
}
