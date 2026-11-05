package com.genomicsops.molview.mol

import java.io.BufferedReader
import java.io.InputStream

class PdbParseException(message: String) : Exception(message)

/**
 * Minimal fixed-width PDB parser. Retains only the first MODEL if the file contains multiple.
 * Does not compute bonds — that is deferred to a separate step (needs spatial indexing for performance).
 */
object PdbParser {

    fun parse(input: InputStream): Model = input.bufferedReader().use { parse(it) }

    fun parse(reader: BufferedReader): Model {
        val atoms = ArrayList<Atom>(4096)
        val residues = ArrayList<Residue>(1024)
        val chains = LinkedHashMap<Char, Chain>()

        var currentChainId: Char = '\u0000'
        var currentResidue: Residue? = null
        var done = false
        var modelName = ""

        reader.forEachLine { raw ->
            if (done) return@forEachLine
            val line = raw.padEnd(80)
            val record = line.substring(0, 6).trim()

            when (record) {
                "MODEL" -> {
                    if (atoms.isNotEmpty()) { done = true; return@forEachLine }
                    modelName = line.substring(10, 14).trim()
                }
                "ENDMDL" -> {
                    if (atoms.isNotEmpty()) done = true
                }
                "ATOM", "HETATM" -> {
                    val atom = parseAtom(line, isHetero = record == "HETATM") ?: return@forEachLine

                    val chainId = line[21]
                    if (chainId != currentChainId) {
                        currentChainId = chainId
                        currentResidue = null
                    }

                    val resName = line.substring(17, 20).trim()
                    val resSeq = line.substring(22, 26).trim().toIntOrNull() ?: 0
                    val iCode = line[26]

                    val existing = currentResidue
                    if (existing == null ||
                        existing.name != resName ||
                        existing.number != resSeq ||
                        existing.insertionCode != iCode
                    ) {
                        val chain = chains.getOrPut(chainId) { Chain(chainId) }
                        val residue = Residue(resName, resSeq, iCode, chain)
                        chain.residues.add(residue)
                        residues.add(residue)
                        currentResidue = residue
                    }

                    val withResidue = atom.copy(residue = currentResidue)
                    currentResidue!!.atoms.add(withResidue)
                    atoms.add(withResidue)
                }
                "TER" -> currentResidue = null
                "END" -> {
                    // Stop once we've collected atoms; ignore trailing noise.
                    if (atoms.isNotEmpty()) done = true
                }
            }
        }

        return Model(
            atoms = atoms,
            residues = residues,
            chains = chains.values.toList(),
            name = modelName,
        )
    }

    private fun parseAtom(line: String, isHetero: Boolean): Atom? = try {
        val serial = line.substring(6, 11).trim().toIntOrNull() ?: 0
        val rawName = line.substring(12, 16)
        val name = rawName.trim()
        val altLoc = line[16]

        val x = line.substring(30, 38).trim().toFloatOrNull() ?: return null
        val y = line.substring(38, 46).trim().toFloatOrNull() ?: return null
        val z = line.substring(46, 54).trim().toFloatOrNull() ?: return null

        val occupancy = line.substring(54, 60).trim().toFloatOrNull() ?: 1.0f
        val bFactor = line.substring(60, 66).trim().toFloatOrNull() ?: 0f

        val elementField = line.substring(76, 78).trim()
        val elementSymbol = elementField.ifEmpty { guessElement(rawName) }
        val charge = parseCharge(line.substring(78, 80).trim())

        Atom(
            serial = serial,
            name = name,
            element = Element.fromSymbol(elementSymbol),
            altLoc = altLoc,
            x = x, y = y, z = z,
            occupancy = occupancy,
            bFactor = bFactor,
            isHetero = isHetero,
            charge = charge,
        )
    } catch (_: Exception) {
        null
    }

    /**
     * PDB convention: if atom name has a leading space or digit, element is the first letter.
     * If it starts flush-left with a letter, it's a two-letter element (FE, ZN, MG...).
     */
    private fun guessElement(rawName: String): String {
        if (rawName.length < 4) return ""
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return ""
        val first = rawName[0]
        return if (first == ' ' || first.isDigit()) {
            trimmed.firstOrNull { it.isLetter() }?.toString() ?: ""
        } else {
            trimmed.take(2)
        }
    }

    private fun parseCharge(s: String): Int {
        if (s.length < 2) return 0
        val magnitude = s[0].digitToIntOrNull() ?: return 0
        val sign = if (s[1] == '-') -1 else 1
        return magnitude * sign
    }
}
