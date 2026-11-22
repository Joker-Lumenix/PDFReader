package com.peng.pdfreader.core.office.pptx

import com.peng.pdfreader.core.office.OoxmlPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The reader of the charts of a presentation.
 *
 * The chart of the test is built by the test itself, so the values of the
 * test are known.
 */
class PptxChartTest {

    /** The chart of the test: two rows of three groups. */
    private val chartXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
          <c:chart>
            <c:title>
              <c:tx><c:rich><a:p><a:r><a:t>Sales</a:t></a:r></a:p></c:rich></c:tx>
            </c:title>
            <c:plotArea>
              <c:barChart>
                <c:barDir val="col"/>
                <c:grouping val="clustered"/>
                <c:ser>
                  <c:tx><c:strRef><c:strCache><c:pt idx="0"><c:v>2024</c:v></c:pt></c:strCache></c:strRef></c:tx>
                  <c:cat><c:strRef><c:strCache>
                    <c:pt idx="0"><c:v>Q1</c:v></c:pt>
                    <c:pt idx="1"><c:v>Q2</c:v></c:pt>
                    <c:pt idx="2"><c:v>Q3</c:v></c:pt>
                  </c:strCache></c:strRef></c:cat>
                  <c:val><c:numRef><c:numCache>
                    <c:pt idx="0"><c:v>10</c:v></c:pt>
                    <c:pt idx="1"><c:v>20</c:v></c:pt>
                    <c:pt idx="2"><c:v>15</c:v></c:pt>
                  </c:numCache></c:numRef></c:val>
                </c:ser>
                <c:ser>
                  <c:tx><c:strRef><c:strCache><c:pt idx="0"><c:v>2025</c:v></c:pt></c:strCache></c:strRef></c:tx>
                  <c:cat><c:strRef><c:strCache>
                    <c:pt idx="0"><c:v>Q1</c:v></c:pt>
                    <c:pt idx="1"><c:v>Q2</c:v></c:pt>
                    <c:pt idx="2"><c:v>Q3</c:v></c:pt>
                  </c:strCache></c:strRef></c:cat>
                  <c:val><c:numRef><c:numCache>
                    <c:pt idx="0"><c:v>12</c:v></c:pt>
                    <c:pt idx="1"><c:v>18</c:v></c:pt>
                    <c:pt idx="2"><c:v>22</c:v></c:pt>
                  </c:numCache></c:numRef></c:val>
                </c:ser>
              </c:barChart>
              <c:catAx><c:delete val="0"/></c:catAx>
              <c:valAx>
                <c:delete val="0"/>
                <c:scaling><c:max val="30"/><c:min val="0"/></c:scaling>
              </c:valAx>
            </c:plotArea>
            <c:legend/>
          </c:chart>
        </c:chartSpace>
    """.trimIndent()

    /** A package that holds the chart of the test. */
    private fun packageOfChart(): File {
        val file = File.createTempFile("chart-", ".pptx")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("ppt/charts/chart1.xml"))
            out.write(chartXml.toByteArray())
            out.closeEntry()
        }
        return file
    }

    /**
     * The chart of the package is read.
     */
    @Test
    fun theChartOfThePackageIsRead() {
        val file = packageOfChart()
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val chart = PptxChartParser.parse(pkg, "ppt/charts/chart1.xml", emptyMap())
        pkg.close()
        assertNotNull("the chart should be read", chart)
        assertEquals("the kind of the chart", "column", chart!!.kind)
        assertEquals("the title of the chart", "Sales", chart.title)
        assertEquals("the groups of the chart", listOf("Q1", "Q2", "Q3"), chart.categories)
        assertEquals("the number of the rows", 2, chart.series.size)
        assertEquals("the name of the first row", "2024", chart.series[0].name)
        assertEquals("the values of the first row", listOf(10f, 20f, 15f), chart.series[0].values)
        assertEquals("the values of the second row", listOf(12f, 18f, 22f), chart.series[1].values)
        assertTrue("the chart should show a legend", chart.legend)
        assertTrue("the chart should show the axis of the values", chart.showValueAxis)
        assertEquals("the highest value of the chart", 30f, chart.maxValue)
    }

    /**
     * A package without a chart does not break the reader.
     */
    @Test
    fun aPackageWithoutAChartIsRead() {
        val file = File.createTempFile("empty-", ".pptx")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("[Content_Types].xml"))
            out.write("<Types/>".toByteArray())
            out.closeEntry()
        }
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val chart = PptxChartParser.parse(pkg, "ppt/charts/chart1.xml", emptyMap())
        pkg.close()
        assertEquals("there should be no chart", null, chart)
    }
}
