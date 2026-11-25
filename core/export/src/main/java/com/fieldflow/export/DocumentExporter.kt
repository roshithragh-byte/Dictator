package com.fieldflow.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.TextUtils
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface DocumentExporter {
    fun printDocument(activity: Activity, title: String, markdownContent: String)
    suspend fun exportPdf(context: Context, title: String, markdownContent: String): Uri
    fun shareFile(context: Context, fileUri: Uri, mimeType: String)
    fun openPlaceInGoogleMaps(context: Context, placeQuery: String)
}

/**
 * S4-1 & S4-2: PrintManager HTML->Print adapter, multi-page PdfDocument generator,
 * FileProvider Share Sheet, and zero-auth geo: Google Maps intent helper.
 */
class AndroidDocumentExporter : DocumentExporter {

    private var activePrintWebView: WebView? = null

    override fun printDocument(activity: Activity, title: String, markdownContent: String) {
        val webView = WebView(activity)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter(title.ifBlank { "FieldFlow Note" })
                printManager.print(
                    title.ifBlank { "FieldFlow Note" },
                    adapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
                activePrintWebView = null
            }
        }
        val html = markdownToPrintHtml(title, markdownContent)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        activePrintWebView = webView
    }

    override suspend fun exportPdf(
        context: Context,
        title: String,
        markdownContent: String
    ): Uri = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "recordings/exports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "FieldFlow_Note" }
        val outFile = File(exportDir, "$safeName.pdf")

        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val margin = 48f
        val maxLineWidth = pageWidth - margin * 2

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11.5f
            isAntiAlias = true
        }

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 18f

        canvas.drawText(title.ifBlank { "FieldFlow Note" }, margin, y, titlePaint)
        y += 28f

        val lines = wrapLines(markdownContent, bodyPaint, maxLineWidth)
        for (line in lines) {
            if (y > pageHeight - margin) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
            }
            canvas.drawText(line, margin, y, bodyPaint)
            y += 17f
        }
        pdf.finishPage(page)

        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    override fun shareFile(context: Context, fileUri: Uri, mimeType: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share FieldFlow Document"))
    }

    override fun openPlaceInGoogleMaps(context: Context, placeQuery: String) {
        val encoded = Uri.encode(placeQuery.trim())
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.lines()) {
            if (paragraph.isBlank()) {
                result.add("")
                continue
            }
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                val splitAt = if (count < remaining.length) {
                    val lastSpace = remaining.lastIndexOf(' ', count - 1)
                    if (lastSpace > 0) lastSpace else count
                } else {
                    count
                }
                result.add(remaining.substring(0, splitAt).trimEnd())
                remaining = remaining.substring(splitAt).trimStart()
            }
        }
        return result
    }

    private fun markdownToPrintHtml(title: String, md: String): String {
        val escapedTitle = TextUtils.htmlEncode(title.ifBlank { "FieldFlow Note" })
        val bodyHtml = md.lines().joinToString("\n") { line ->
            val escaped = TextUtils.htmlEncode(line)
            when {
                line.startsWith("### ") -> "<h3>${escaped.removePrefix("### ")}</h3>"
                line.startsWith("## ") -> "<h2>${escaped.removePrefix("## ")}</h2>"
                line.startsWith("# ") -> "<h1>${escaped.removePrefix("# ")}</h1>"
                line.startsWith("- [ ] ") -> "<p>&#9744; ${escaped.removePrefix("- [ ] ")}</p>"
                line.startsWith("- ") -> "<p>&bull; ${escaped.removePrefix("- ")}</p>"
                line.isBlank() -> "<br/>"
                else -> "<p>$escaped</p>"
            }
        }
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <style>
                body { font-family: sans-serif; margin: 24px; color: #111827; line-height: 1.5; }
                h1 { font-size: 20px; border-bottom: 1px solid #d1d5db; padding-bottom: 6px; }
                h2 { font-size: 16px; margin-top: 18px; }
                h3 { font-size: 14px; margin-top: 14px; }
                p { margin: 4px 0; font-size: 12.5px; }
              </style>
            </head>
            <body>
              <h1>$escapedTitle</h1>
              $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }
}
