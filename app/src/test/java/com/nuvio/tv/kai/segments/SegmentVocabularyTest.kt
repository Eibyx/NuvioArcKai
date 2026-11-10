package com.nuvio.tv.kai.segments

import com.nuvio.tv.kai.segments.SegmentVocabulary.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentVocabularyTest {

    private fun assertKind(kind: Kind?, vararg names: String) =
        names.forEach { assertEquals(it, kind, SegmentVocabulary.classify(it)) }

    @Test
    fun `openings in every supported language`() = assertKind(
        Kind.OPENING,
        "OP", "OP2", "op 1", "Opening", "Opening Credits", "Opening Theme", "OPENING",
        "Apertura", "apertura 2", "Tema de apertura", "Cabecera", "Abertura",
        "Générique", "Générique de début", "Vorspann", "Sigla", "Sigla iniziale", "オープニング"
    )

    @Test
    fun `endings, outros and credits are told apart`() {
        assertKind(Kind.ENDING, "ED", "ED 2", "Ending", "Ending Theme", "Cierre", "Tema de cierre", "Encerramento", "Générique de fin", "エンディング")
        assertKind(Kind.OUTRO, "Outro", "outro 1")
        assertKind(Kind.CREDITS, "Credits", "End Credits", "Closing Credits", "Créditos", "Créditos finales", "Créditos finais", "Crédits", "Abspann", "Nachspann", "Titoli di coda")
    }

    @Test
    fun `recaps and intros`() {
        assertKind(Kind.RECAP, "Recap", "Previously", "Previously on The Harbour", "Resumen", "Recapitulación", "Anteriormente", "Resumo", "Rückblick", "Résumé", "Riassunto", "あらすじ")
        assertKind(Kind.INTRO, "Intro", "INTRO 2", "イントロ")
    }

    @Test
    fun `post-credits scenes are never credits`() = assertKind(
        Kind.POST_CREDITS,
        "Post-Credits Scene", "Mid-Credits", "After Credits", "Escena post-créditos", "Poscréditos", "Pós-créditos",
        "Scène post-générique", "Nach dem Abspann"
    )

    @Test
    fun `scene names and ordinary chapters never match`() = assertKind(
        null,
        "The Opening Scene", "The Opening", "Alternate Ending", "The Ending", "Ed arrives", "Introduction", "Intro to the Harbour",
        "Recapture", "Opera", "Operation", "Preview", "Next time", "Chapter 1", "Apertura del caso", "Créditos y deudas",
        "Prólogo", "Parte A", "Avance", ""
    )

    @Test
    fun `any of a chapter's names decides, a post-credits name wins`() {
        assertEquals(Kind.OPENING, SegmentVocabulary.classifyAny(listOf("Capítulo 2", "Opening")))
        assertEquals(Kind.CREDITS, SegmentVocabulary.classifyAny(listOf(null, "Créditos")))
        assertEquals(Kind.POST_CREDITS, SegmentVocabulary.classifyAny(listOf("Credits", "Post-credits")))
        assertNull(SegmentVocabulary.classifyAny(listOf("Prologue", "Prólogo")))
    }

    @Test
    fun `only leading segments leave the chapter list`() {
        listOf("Apertura", "Resumen", "Intro", "OP").forEach { assertEquals(it, true, SegmentVocabulary.isLeadingSegment(it)) }
        listOf("Créditos", "Ending", "Outro", "Post-credits", "Parte A").forEach { assertEquals(it, false, SegmentVocabulary.isLeadingSegment(it)) }
    }
}
