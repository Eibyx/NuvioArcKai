package com.nuvio.tv.kai.segments

import com.nuvio.tv.kai.segments.ArcSkipDiag.Case
import org.junit.Assert.assertEquals
import org.junit.Test

class ArcSkipDiagTest {
    @Test
    fun `cases are classified from what the pipeline saw`() {
        assertEquals(Case.SIN_FUENTE, ArcSkipDiag.classify(0, 0, 0, sawActiveInterval = false, sawButton = false))
        assertEquals(Case.CAPITULOS_NO_RECONOCIDOS, ArcSkipDiag.classify(5, 0, 0, sawActiveInterval = false, sawButton = false))
        assertEquals(Case.DATOS_SIN_BOTON, ArcSkipDiag.classify(5, 2, 2, sawActiveInterval = true, sawButton = false))
        assertEquals(Case.OK, ArcSkipDiag.classify(5, 2, 2, sawActiveInterval = true, sawButton = true))
        assertEquals(Case.OK, ArcSkipDiag.classify(0, 0, 3, sawActiveInterval = false, sawButton = false))
    }
}
