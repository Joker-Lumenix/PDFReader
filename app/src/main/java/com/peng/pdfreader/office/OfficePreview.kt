package com.peng.pdfreader.office

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.peng.pdfreader.office.docx.DocxParser
import com.peng.pdfreader.office.docx.DocxPreview
import com.peng.pdfreader.office.pptx.PptxParser
import com.peng.pdfreader.office.pptx.PptxPreview
import com.peng.pdfreader.office.xlsx.XlsxParser
import com.peng.pdfreader.office.xlsx.XlsxPreview
import com.peng.pdfreader.ui.components.EmptyState
import com.peng.pdfreader.ui.components.PdfTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The Office formats that can be previewed offline. */
enum class OfficeFormat(val label: String) {
    WORD("Word"),
    EXCEL("Excel"),
    PPT("PowerPoint");

    companion object {
        /** Detects the preview format from a file name, or null when it is not an Office file. */
        fun of(fileName: String): OfficeFormat? =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "doc", "docx" -> WORD
                "xls", "xlsx" -> EXCEL
                "ppt", "pptx" -> PPT
                else -> null
            }
    }
}

/**
 * Offline preview shell for the Office formats.
 *
 * The document is parsed on a background thread and rendered by the format
 * specific preview composable (Word / Excel / PowerPoint).
 */
@Composable
fun OfficePreviewScreen(file: File, format: OfficeFormat, onBack: () -> Unit) {
    var state by remember(file.path) { mutableStateOf<PreviewState>(PreviewState.Loading) }

    LaunchedEffect(file.path, format) {
        state = PreviewState.Loading
        state = withContext(Dispatchers.IO) { parseDocument(file, format) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PdfTopBar(
            title = file.name,
            subtitle = "${format.label} 预览 · 离线解析",
            onBack = onBack,
        )
        when (val current = state) {
            is PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is PreviewState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = current.message,
                    hint = "该文件可能已损坏或使用了本应用尚未支持的高级特性",
                    actionText = "返回",
                    onAction = onBack,
                )
            }

            is PreviewState.Word -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                DocxPreview(current.document)
            }

            is PreviewState.Excel -> Box(Modifier.fillMaxSize()) {
                XlsxPreview(current.workbook)
            }

            is PreviewState.PowerPoint -> Box(Modifier.fillMaxSize()) {
                PptxPreview(current.deck)
            }
        }
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Failed(val message: String) : PreviewState
    data class Word(val document: com.peng.pdfreader.office.docx.DocxDocument) : PreviewState
    data class Excel(val workbook: com.peng.pdfreader.office.xlsx.XlsxWorkbook) : PreviewState
    data class PowerPoint(val deck: com.peng.pdfreader.office.pptx.PptxDeck) : PreviewState
}

private fun parseDocument(file: File, format: OfficeFormat): PreviewState {
    if (!file.exists()) return PreviewState.Failed("文件已不存在")
    val pkg = OoxmlPackage.open(file) ?: return PreviewState.Failed("无法读取该文件")
    return try {
        when (format) {
            OfficeFormat.WORD -> DocxParser.parse(pkg)?.let { PreviewState.Word(it) }
                ?: PreviewState.Failed("Word 文档解析失败")

            OfficeFormat.EXCEL -> XlsxParser.parse(pkg)?.let { PreviewState.Excel(it) }
                ?: PreviewState.Failed("Excel 工作簿解析失败")

            OfficeFormat.PPT -> PptxParser.parse(pkg)?.let { PreviewState.PowerPoint(it) }
                ?: PreviewState.Failed("PowerPoint 演示文稿解析失败")
        }
    } catch (error: Throwable) {
        PreviewState.Failed("解析失败：${error.message ?: error.javaClass.simpleName}")
    } finally {
        pkg.close()
    }
}
