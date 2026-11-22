package com.peng.pdfreader.core.office.docx

import com.peng.pdfreader.core.office.OoxmlPackage
import org.junit.Test

class MergeDiagnosticTest {

    @Test
    fun theMergesOfTheUserDocumentAreFound() {
        val file = java.io.File("../模型_lightgbm_release_v1_h1.docx")
        if (!file.exists()) {
            println("MISSING")
            return
        }
        val pkg = OoxmlPackage.open(file)!!
        val document = DocxParser.parse(pkg)
        pkg.close()
        var restart = 0
        var cont = 0
        var spanned = 0
        var maxSpan = 0
        var tables = 0
        document!!.blocks.filterIsInstance<DocxBlock.Table>().forEach { table ->
            tables++
            table.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell.vMerge == "restart") {
                        restart++
                        spanned += cell.rowSpan
                        if (cell.rowSpan > maxSpan) maxSpan = cell.rowSpan
                    }
                    if (cell.vMerge == "continue") cont++
                }
            }
        }
        println("tables: $tables")
        println("merge starts: $restart, continuations: $cont")
        println("rows covered by the merges: $spanned, the longest merge: $maxSpan")
    }
}
