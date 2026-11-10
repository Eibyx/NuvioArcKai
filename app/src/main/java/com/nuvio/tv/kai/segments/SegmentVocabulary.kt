package com.nuvio.tv.kai.segments

/**
 * The one list of chapter names that mean a skippable segment, shared by the chapter list
 * (which hides openings, intros and recaps) and by the skip button (which builds intervals from them).
 *
 * Names must be essentially the label itself, optionally numbered ("OP", "OP 2", "Opening Credits",
 * "Apertura", "オープニング"); scene names such as "The Opening Scene" or "Alternate Ending" never match.
 * Post-, mid- and after-credits scenes are recognised first so they are never skipped as credits.
 *
 * Languages: English, Spanish, Portuguese, French, German, Italian, Dutch (recaps) and Japanese.
 */
internal object SegmentVocabulary {

    enum class Kind { OPENING, INTRO, RECAP, ENDING, OUTRO, CREDITS, POST_CREDITS }

    private const val NUMBER_SUFFIX = "(?:[\\s._#-]*\\d+)?[.:!]?"

    private fun anchored(alternatives: String) = Regex("(?iu)^(?:$alternatives)$NUMBER_SUFFIX$")

    private val postCredits = Regex(
        "(?iu)(?:post|mid|after)[\\s-]?credits?|p[óo]s[\\s-]?cr[ée]ditos|post[\\s-]?cr[ée]ditos|" +
            "sc[èe]ne[s]? post[\\s-]?g[ée]n[ée]rique|apr[èe]s le g[ée]n[ée]rique|nach dem abspann|dopo i titoli|" +
            "ポストクレジット"
    )

    private val opening = anchored(
        "op(?:ening)?(?:\\s*(?:credits|titles|theme|song))?|" +
            "apertura|tema de apertura|cabecera|abertura|tema de abertura|" +
            "g[ée]n[ée]rique(?: de d[ée]but| d'ouverture| d’ouverture)?|vorspann|sigla(?: iniziale| d'apertura)?|" +
            "オープニング(?:テーマ)?"
    )

    private val intro = anchored("intro|イントロ")

    private val recap = anchored(
        "recap|previously|previously on\\b.*|r[üu]ckblick|was bisher geschah|zuvor bei\\b.*|" +
            "r[ée]sum[ée]|pr[ée]c[ée]demment(?: dans\\b.*)?|riassunto|nelle puntate precedenti|" +
            "resumen|recapitulaci[óo]n|anteriormente(?: en\\b.*)?|resumo|recapitula[çc][ãa]o|anteriormente em\\b.*|" +
            "eerder(?: in\\b.*)?|あらすじ|前回のあらすじ"
    )

    private val ending = anchored(
        "ed|ending(?:\\s*(?:credits|theme|song))?|tema de cierre|cierre|encerramento|tema de encerramento|" +
            "g[ée]n[ée]rique de fin|sigla (?:finale|di chiusura)|エンディング(?:テーマ)?"
    )

    private val outro = anchored("outro")

    private val credits = anchored(
        "(?:end|closing)?\\s*credits|cr[ée]ditos(?: finales| finais| de cierre)?|cr[ée]dits(?: de fin)?|" +
            "titoli di coda|abspann|nachspann|スタッフロール|クレジット"
    )

    /** The segment one chapter name stands for, or null for an ordinary chapter. */
    fun classify(name: String?): Kind? {
        val text = name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
        if (postCredits.containsMatchIn(text)) return Kind.POST_CREDITS
        return when {
            opening.matches(text) -> Kind.OPENING
            ending.matches(text) -> Kind.ENDING
            credits.matches(text) -> Kind.CREDITS
            recap.matches(text) -> Kind.RECAP
            outro.matches(text) -> Kind.OUTRO
            intro.matches(text) -> Kind.INTRO
            else -> null
        }
    }

    /**
     * A chapter carries one name per language; the player shows one of them but the segment can be told
     * apart by any. A post-credits name in any language wins, so such a scene is never skipped.
     */
    fun classifyAny(names: List<String?>): Kind? {
        val kinds = names.mapNotNull(::classify)
        if (Kind.POST_CREDITS in kinds) return Kind.POST_CREDITS
        return kinds.firstOrNull()
    }

    /** Openings, intros and recaps come before the story: the chapter list leaves them out. */
    fun isLeadingSegment(name: String): Boolean = when (classify(name)) {
        Kind.OPENING, Kind.INTRO, Kind.RECAP -> true
        else -> false
    }
}
