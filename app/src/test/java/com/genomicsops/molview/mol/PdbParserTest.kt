package com.genomicsops.molview.mol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PdbParserTest {

    // Two residues, one chain, three atoms. Columns are fixed-width per PDB spec.
    private val tinyPdb = buildString {
        appendLine("ATOM      1  N   ALA A   1      11.104   6.134  -6.504  1.00 20.00           N")
        appendLine("ATOM      2  CA  ALA A   1      11.639   6.071  -5.147  1.00 20.00           C")
        appendLine("ATOM      3  C   ALA A   1      13.149   6.523  -5.174  1.00 20.00           C")
        appendLine("ATOM      4  N   GLY A   2      13.722   6.965  -3.961  1.00 21.00           N")
        appendLine("HETATM    5  O   HOH A 101      10.000   5.000  -7.000  1.00 30.00           O")
        appendLine("END")
    }

    @Test
    fun parsesAtomsResiduesChains() {
        val model = PdbParser.parse(ByteArrayInputStream(tinyPdb.toByteArray()))
        assertEquals(5, model.atoms.size)
        assertEquals(3, model.residues.size)
        assertEquals(1, model.chains.size)
        assertEquals('A', model.chains[0].id)
        assertEquals(2, model.chains[0].residues.count { it.isStandardAA })
    }

    @Test
    fun classifiesWaterAndStandardAminoAcids() {
        val model = PdbParser.parse(ByteArrayInputStream(tinyPdb.toByteArray()))
        val ala = model.residues.first { it.name == "ALA" }
        val water = model.residues.first { it.name == "HOH" }
        assertTrue(ala.isStandardAA)
        assertTrue(water.isWater)
        assertTrue(!water.isLigand)
    }

    @Test
    fun parsesCoordinatesAndElements() {
        val model = PdbParser.parse(ByteArrayInputStream(tinyPdb.toByteArray()))
        val n = model.atoms[0]
        assertEquals("N", n.name)
        assertEquals(Element.N, n.element)
        assertEquals(11.104f, n.x, 0.001f)
        assertEquals(6.134f, n.y, 0.001f)
        assertEquals(-6.504f, n.z, 0.001f)
        assertNotNull(n.residue)
        assertEquals("ALA", n.residue?.name)
    }

    @Test
    fun stopsAtEndRecord() {
        val withTrailing = tinyPdb + "ATOM      9  X   ALA A   9       0.000   0.000   0.000  1.00 20.00           X\n"
        val model = PdbParser.parse(ByteArrayInputStream(withTrailing.toByteArray()))
        assertEquals(5, model.atoms.size)   // trailing atom not counted
    }
}
