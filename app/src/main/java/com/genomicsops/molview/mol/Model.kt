package com.genomicsops.molview.mol

/**
 * Chemical element with CPK-ish color and vdW radius for future rendering.
 * color is packed ARGB.
 */
enum class Element(val symbol: String, val vdwRadius: Float, val color: Int) {
    C("C", 1.70f, 0xFF909090.toInt()),
    N("N", 1.55f, 0xFF3050F8.toInt()),
    O("O", 1.52f, 0xFFFF0D0D.toInt()),
    S("S", 1.80f, 0xFFFFFF30.toInt()),
    P("P", 1.80f, 0xFFFF8000.toInt()),
    H("H", 1.20f, 0xFFFFFFFF.toInt()),
    F("F", 1.47f, 0xFF90E050.toInt()),
    CL("Cl", 1.75f, 0xFF1FF01F.toInt()),
    BR("Br", 1.85f, 0xFFA62929.toInt()),
    I("I", 1.98f, 0xFF940094.toInt()),
    FE("Fe", 2.00f, 0xFFE06633.toInt()),
    ZN("Zn", 1.39f, 0xFF7D80B0.toInt()),
    MG("Mg", 1.73f, 0xFF8AFF00.toInt()),
    CA("Ca", 2.31f, 0xFF3DFF00.toInt()),
    NA("Na", 2.27f, 0xFFAB5CF2.toInt()),
    K("K", 2.75f, 0xFF8F40D4.toInt()),
    MN("Mn", 2.00f, 0xFF9C7AC7.toInt()),
    CU("Cu", 1.40f, 0xFFC88033.toInt()),
    CO("Co", 2.00f, 0xFFF090A0.toInt()),
    NI("Ni", 1.63f, 0xFF50D050.toInt()),
    UNKNOWN("X", 1.70f, 0xFFFF00FF.toInt());

    companion object {
        fun fromSymbol(s: String): Element {
            val t = s.trim()
            if (t.isEmpty()) return UNKNOWN
            return entries.firstOrNull { it.symbol.equals(t, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

data class Atom(
    val serial: Int,
    val name: String,           // trimmed atom name, e.g. "CA", "1HG1"
    val element: Element,
    val altLoc: Char,
    val x: Float,
    val y: Float,
    val z: Float,
    val occupancy: Float,
    val bFactor: Float,
    val isHetero: Boolean,
    val charge: Int,
    val residue: Residue? = null,
) {
    fun distanceSq(other: Atom): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }
}

data class Residue(
    val name: String,           // "ALA", "HEM", "HOH"
    val number: Int,
    val insertionCode: Char,
    val chain: Chain? = null,
    val atoms: MutableList<Atom> = mutableListOf(),
) {
    val isWater: Boolean get() = name in WATER_NAMES
    val isStandardAA: Boolean get() = name in AMINO_ACIDS
    val isNucleotide: Boolean get() = name in NUCLEOTIDES
    val isIon: Boolean get() = name in IONS
    val isLigand: Boolean get() = !isWater && !isIon && !isStandardAA && !isNucleotide

    /** Single-character code for cartoon coloring; 'C' = coil/other. */
    val secondaryStructureHint: Char
        get() = if (isStandardAA) 'C' else ' '

    val centroidX: Float get() = atoms.map { it.x }.average().toFloat()
    val centroidY: Float get() = atoms.map { it.y }.average().toFloat()
    val centroidZ: Float get() = atoms.map { it.z }.average().toFloat()

    companion object {
        val WATER_NAMES = setOf("HOH", "WAT", "DOD", "H2O", "TIP3", "TIP")
        val AMINO_ACIDS = setOf(
            "ALA","ARG","ASN","ASP","CYS","GLN","GLU","GLY","HIS","ILE",
            "LEU","LYS","MET","PHE","PRO","SER","THR","TRP","TYR","VAL",
            "SEC","PYL","MSE","ASX","GLX","UNK"
        )
        val NUCLEOTIDES = setOf(
            "A","C","G","U","I",
            "DA","DC","DG","DT","DI","DU"
        )
        val IONS = setOf(
            "NA","CL","MG","ZN","CA","K","MN","FE","CU","CO","NI","CD","HG",
            "SO4","PO4","NO3","CO3","F","BR","I","BA","SR","CS","LI","AL"
        )
    }
}

data class Chain(
    val id: Char,
    val residues: MutableList<Residue> = mutableListOf(),
) {
    val atomCount: Int get() = residues.sumOf { it.atoms.size }
}

data class Bond(val atomA: Int, val atomB: Int, val order: Int = 1)

data class Model(
    val atoms: List<Atom>,
    val residues: List<Residue>,
    val chains: List<Chain>,
    val bonds: List<Bond> = emptyList(),
    val name: String = "",
) {
    val atomCount: Int get() = atoms.size
    val residueCount: Int get() = residues.size
    val chainCount: Int get() = chains.size
    val ligandResidues: List<Residue> get() = residues.filter { it.isLigand }
}
