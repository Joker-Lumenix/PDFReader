package com.peng.pdfreader.data

import android.content.Context
import java.io.File

/**
 * The sample documents the app starts with.
 *
 * The bundled Office samples are copied into the sandbox on the first start so
 * that every preview has something to show, and a small PDF is generated locally
 * for the reader. Everything stays on the device.
 */
object SampleDocuments {

    private const val ASSET_FOLDER = "seed"

    /** The name of the generated PDF. */
    const val DEMO_PDF_NAME = "员工手册与差旅报销制度.pdf"

    /**
     * Copies the bundled samples into the sandbox.
     *
     * @return the files that were added.
     */
    fun install(context: Context, sandbox: File): List<File> {
        val names = runCatching { context.assets.list(ASSET_FOLDER)?.toList() }.getOrNull()
            ?: return emptyList()
        val installed = mutableListOf<File>()
        names.forEach { name ->
            val target = File(sandbox, name)
            if (target.exists()) return@forEach
            val copied = runCatching {
                context.assets.open("$ASSET_FOLDER/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)
            if (copied) installed += target
        }
        return installed
    }

    /**
     * Writes a small PDF into the sandbox.
     *
     * The document is written with the standard Helvetica font, so its text can
     * be searched in the reader.
     *
     * @return the file, or null when it could not be written.
     */
    fun createDemoPdf(sandbox: File): File? = runCatching {
        val target = File(sandbox, DEMO_PDF_NAME)
        if (target.exists()) return@runCatching target

        val pdf = MiniPdf()
        pdf.addPage(
            listOf(
                MiniPdf.Line("Employee Handbook and Travel Reimbursement", 22f, 48f, 750f),
                MiniPdf.Line("Chapter 1  General Provisions", 17f, 48f, 700f),
                MiniPdf.Line("Article 2  Travel Standards", 17f, 48f, 660f),
                MiniPdf.Line("Generated offline by Let's PDF to demonstrate reading,", 13f, 48f, 620f),
                MiniPdf.Line("annotation and export. Nothing ever leaves the device.", 13f, 48f, 596f),
            ),
        )
        pdf.addPage(
            listOf(
                MiniPdf.Line("Chapter 3  Reimbursement Process", 17f, 48f, 750f),
                MiniPdf.Line("1. Submit the request before the trip.", 13f, 48f, 700f),
                MiniPdf.Line("2. Hand in the receipts within three working days.", 13f, 48f, 670f),
                MiniPdf.Line("3. Finance reviews and pays within one working day.", 13f, 48f, 640f),
            ),
        )
        pdf.writeTo(target)
        target
    }.getOrNull()

    /** The type of a sample file, derived from its name. */
    fun typeOf(name: String): DocType = DocType.of(name)
}
