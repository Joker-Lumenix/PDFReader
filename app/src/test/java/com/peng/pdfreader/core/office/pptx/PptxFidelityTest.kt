package com.peng.pdfreader.core.office.pptx

import com.peng.pdfreader.core.office.OoxmlPackage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader of a presentation against a real sample.
 *
 * The sample is a deck of a product launch: it has a bulleted list with a
 * space after its paragraphs, a table and a picture.
 */
class PptxFidelityTest {

    private fun sample(name: String): File {
        val resource = javaClass.classLoader?.getResource("seed/$name")
            ?: error("the sample $name is missing")
        val target = File.createTempFile("sample-", ".tmp")
        target.deleteOnExit()
        File(resource.toURI()).copyTo(target, overwrite = true)
        return target
    }

    private fun deck(): PptxDeck {
        val file = sample("sample_deck.pptx")
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val deck = PptxParser.parse(pkg)
        pkg.close()
        assertNotNull("the deck should parse", deck)
        return deck!!
    }

    /**
     * The text of a paragraph keeps the space and the margin of the file.
     *
     * The list of the second slide has a space of fourteen points below every
     * paragraph and a hanging indent for its bullet.
     */
    @Test
    fun theTextOfAParagraphKeepsItsSpaceAndItsMargin() {
        val deck = deck()
        val paragraphs = deck.slides
            .flatMap { it.shapes }
            .filterIsInstance<PptxShape.TextBox>()
            .flatMap { it.paragraphs }
        val bulleted = paragraphs.filter { it.bullet != null }
        assertTrue("the deck should have a bulleted list", bulleted.isNotEmpty())
        assertTrue(
            "the space below the paragraph should be known",
            bulleted.any { it.spaceAfterPt > 0f },
        )
        assertTrue(
            "the margin of the paragraph should be known",
            bulleted.any { it.marginLeftDp > 0f },
        )
        assertTrue(
            "the indent of the paragraph should hang",
            bulleted.any { it.indentDp < 0f },
        )
    }

    /**
     * The text of a box keeps the space to its border.
     */
    @Test
    fun theTextOfABoxKeepsItsSpaceToTheBorder() {
        val deck = deck()
        val boxes = deck.slides
            .flatMap { it.shapes }
            .filterIsInstance<PptxShape.TextBox>()
        assertTrue("the deck should have text boxes", boxes.isNotEmpty())
        val box = boxes.first()
        assertTrue(
            "the space to the left should be kept, was ${box.insetLeftDp}",
            box.insetLeftDp > 0f,
        )
        assertTrue(
            "the space to the top should be kept, was ${box.insetTopDp}",
            box.insetTopDp > 0f,
        )
    }

    /**
     * The table of the deck carries the style of the office software.
     */
    @Test
    fun theTableOfTheDeckCarriesItsStyle() {
        val deck = deck()
        val table = deck.slides
            .flatMap { it.shapes }
            .filterIsInstance<PptxShape.Table>()
            .firstOrNull()
        assertNotNull("the deck should have a table", table)
        val style = table!!.style
        assertNotNull("the table should have a style", style)
        assertNotNull("the style should paint the first row", style!!.firstRowFill)
        assertNotNull("the style should band the rows", style.bandFill)
    }

    /**
     * The picture of the deck is read.
     */
    @Test
    fun thePictureOfTheDeckIsRead() {
        val deck = deck()
        val pictures = deck.slides
            .flatMap { it.shapes }
            .filterIsInstance<PptxShape.Picture>()
        assertTrue("the deck should have a picture", pictures.isNotEmpty())
        assertTrue("the picture should have bytes", pictures.first().bytes.isNotEmpty())
    }
}
