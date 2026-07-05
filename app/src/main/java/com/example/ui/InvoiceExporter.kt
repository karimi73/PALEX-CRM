package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.Customer
import com.example.data.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

// Multi-item details structured representation
data class InvoiceItemDetail(
    val title: String,
    val quantity: Int,
    val unitPrice: Double
)

// Customizable Invoice styles and properties
data class InvoiceConfig(
    val workshopName: String = "صنایع چوب، مبلمان و دکوراسیون کارگاه",
    val phone: String = "۰۹۱۲۳۴۵۶۷۸۹",
    val address: String = "تهران، شهرک صنعتی خاوران، بازار مبل",
    val headerColor: String = "#0F172A",
    val showLogo: Boolean = true,
    val showTerms: Boolean = true,
    val terms1: String = "۱. کلیه کالاها شامل گارانتی ۱۲ ماهه کلاف و اتصالات می‌باشند.",
    val terms2: String = "۲. تحویل نهایی مبل حداکثر تا ۲۵ روز کاری پس از واریز بیعانه خواهد بود.",
    val terms3: String = "۳. پرداخت نهایی بلافاصله پس از نصب و تایید فنی در کارگاه الزامی است.",
    val terms4: String = "۴. این پیش‌فاکتور فاقد مهر برجسته رسمی، به عنوان سند برآورد است.",
    val borderStyle: String = "GOLD_ACCENT", // "GOLD_ACCENT", "SIMPLE", "DOUBLE", "NONE"
    val logoPath: String? = null,
    val useCustomLogo: Boolean = false,
    val watermarkText: String = "",
    val showWatermark: Boolean = false,
    val useSignature: Boolean = false,
    val signaturePath: String? = null,
    val fontStyle: String = "SANS_SERIF", // "SANS_SERIF", "SERIF", "MONOSPACE"
    val defaultTaxRate: Double = 0.0,
    val defaultDiscountRate: Double = 0.0
)

object InvoiceExporter {

    private fun formatPrice(amount: Double): String {
        val formatter = NumberFormat.getInstance(Locale.US)
        return "${formatter.format(amount)} تومان"
    }

    private fun formatDate(timestamp: Long): String {
        return getPersianDate(timestamp)
    }

    fun getPersianDate(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)
        return gregorianToPersian(gYear, gMonth, gDay)
    }

    private fun gregorianToPersian(g_y: Int, g_m: Int, g_d: Int): String {
        val g_days_in_month = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val p_days_in_month = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy = g_y - 1600
        var gm = g_m - 1
        var gd = g_d - 1

        var g_day_no = 365 * gy + (gy + 4) / 4 - (gy + 100) / 100 + (gy + 400) / 400
        var i = 0
        while (i < gm) {
            g_day_no += g_days_in_month[i]
            i++
        }
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            g_day_no++
        }
        g_day_no += gd

        var p_day_no = g_day_no - 79

        val j_np = p_day_no / 12053
        p_day_no %= 12053

        var jy = 979 + 33 * j_np + 4 * (p_day_no / 1461)
        p_day_no %= 1461

        if (p_day_no >= 366) {
            jy += (p_day_no - 1) / 365
            p_day_no = (p_day_no - 1) % 365
        }

        var jm = 0
        while (jm < 11 && p_day_no >= p_days_in_month[jm]) {
            p_day_no -= p_days_in_month[jm]
            jm++
        }
        val jd = p_day_no + 1
        return "$jy/${jm + 1}/$jd"
    }

    // High fidelity serializer for multi-items list and custom invoice notes
    fun serializeItems(items: List<InvoiceItemDetail>, notes: String): String {
        val itemsJson = items.joinToString(separator = ",") { item ->
            """{"title":"${item.title.replace("\"", "\\\"")}","quantity":${item.quantity},"unitPrice":${item.unitPrice}}"""
        }
        return "[ITEMS_JSON][$itemsJson][NOTES_JSON]$notes"
    }

    // High fidelity regex-based deserializer that falls back to single-item gracefully
    fun deserializeItems(description: String, fallbackTitle: String, fallbackQty: Int, fallbackUnitPrice: Double): Pair<List<InvoiceItemDetail>, String> {
        if (description.startsWith("[ITEMS_JSON]")) {
            try {
                val itemsPart = description.substringAfter("[ITEMS_JSON]").substringBefore("[NOTES_JSON]")
                val notesPart = description.substringAfter("[NOTES_JSON]")
                
                val items = mutableListOf<InvoiceItemDetail>()
                val pattern = java.util.regex.Pattern.compile("""\{"title":"(.*?)","quantity":(\d+),"unitPrice":([\d.]+)\}""")
                val matcher = pattern.matcher(itemsPart)
                while (matcher.find()) {
                    val title = matcher.group(1) ?: ""
                    val qty = matcher.group(2)?.toIntOrNull() ?: 1
                    val price = matcher.group(3)?.toDoubleOrNull() ?: 0.0
                    items.add(InvoiceItemDetail(title, qty, price))
                }
                if (items.isNotEmpty()) {
                    return Pair(items, notesPart)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Backward compatible single item fallback
        return Pair(listOf(InvoiceItemDetail(fallbackTitle, fallbackQty, fallbackUnitPrice)), description)
    }

    // Persistent settings loader from Shared preferences
    fun loadConfig(context: Context): InvoiceConfig {
        val prefs = context.getSharedPreferences("crm_settings", Context.MODE_PRIVATE)
        return InvoiceConfig(
            workshopName = prefs.getString("invoiceWorkshopName", "صنایع چوب، مبلمان و دکوراسیون کارگاه") ?: "صنایع چوب، مبلمان و دکوراسیون کارگاه",
            phone = prefs.getString("invoicePhone", "۰۹۱۲۳۴۵۶۷۸۹") ?: "۰۹۱۲۳۴۵۶۷۸۹",
            address = prefs.getString("invoiceAddress", "تهران، شهرک صنعتی خاوران، بازار مبل") ?: "تهران، شهرک صنعتی خاوران، بازار مبل",
            headerColor = prefs.getString("invoiceHeaderColor", "#0F172A") ?: "#0F172A",
            showLogo = prefs.getBoolean("invoiceShowLogo", true),
            showTerms = prefs.getBoolean("invoiceShowTerms", true),
            terms1 = prefs.getString("invoiceTerms1", "۱. کلیه کالاها شامل گارانتی ۱۲ ماهه کلاف و اتصالات می‌باشند.") ?: "۱. کلیه کالاها شامل گارانتی ۱۲ ماهه کلاف و اتصالات می‌باشند.",
            terms2 = prefs.getString("invoiceTerms2", "۲. تحویل نهایی مبل حداکثر تا ۲۵ روز کاری پس از واریز بیعانه خواهد بود.") ?: "۲. تحویل نهایی مبل حداکثر تا ۲۵ روز کاری پس از واریز بیعانه خواهد بود.",
            terms3 = prefs.getString("invoiceTerms3", "۳. پرداخت نهایی بلافاصله پس از نصب و تایید فنی در کارگاه الزامی است.") ?: "۳. پرداخت نهایی بلافاصله پس از نصب و تایید فنی در کارگاه الزامی است.",
            terms4 = prefs.getString("invoiceTerms4", "۴. این پیش‌فاکتور فاقد مهر برجسته رسمی، به عنوان سند برآورد است.") ?: "۴. این پیش‌فاکتور فاقد مهر برجسته رسمی، به عنوان سند برآورد است.",
            borderStyle = prefs.getString("invoiceBorderStyle", "GOLD_ACCENT") ?: "GOLD_ACCENT",
            logoPath = prefs.getString("invoiceLogoPath", null),
            useCustomLogo = prefs.getBoolean("invoiceUseCustomLogo", false),
            watermarkText = prefs.getString("invoiceWatermarkText", "") ?: "",
            showWatermark = prefs.getBoolean("invoiceShowWatermark", false),
            useSignature = prefs.getBoolean("invoiceUseSignature", false),
            signaturePath = prefs.getString("invoiceSignaturePath", null),
            fontStyle = prefs.getString("invoiceFontStyle", "SANS_SERIF") ?: "SANS_SERIF",
            defaultTaxRate = prefs.getFloat("invoiceDefaultTaxRate", 0f).toDouble(),
            defaultDiscountRate = prefs.getFloat("invoiceDefaultDiscountRate", 0f).toDouble()
        )
    }

    // Dynamic high-fidelity premium drawing system
    fun drawInvoice(
        canvas: Canvas,
        width: Float,
        height: Float,
        transaction: Transaction,
        customer: Customer?,
        config: InvoiceConfig = InvoiceConfig()
    ) {
        val baseTypeface = when (config.fontStyle) {
            "SERIF" -> Typeface.SERIF
            "MONOSPACE" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }

        // White Background
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Render customizable borders
        when (config.borderStyle) {
            "GOLD_ACCENT" -> {
                val slateBorder = Paint().apply {
                    color = Color.parseColor("#475569")
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.003f
                }
                canvas.drawRect(12f, 12f, width - 12f, height - 12f, slateBorder)

                val goldBorder = Paint().apply {
                    color = Color.parseColor("#D97706")
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.001f
                }
                canvas.drawRect(16f, 16f, width - 16f, height - 16f, goldBorder)
            }
            "DOUBLE" -> {
                val dBorder = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.002f
                }
                canvas.drawRect(12f, 12f, width - 12f, height - 12f, dBorder)
                canvas.drawRect(17f, 17f, width - 17f, height - 17f, dBorder)
            }
            "SIMPLE" -> {
                val grayBorder = Paint().apply {
                    color = Color.parseColor("#94A3B8")
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.002f
                }
                canvas.drawRect(12f, 12f, width - 12f, height - 12f, grayBorder)
            }
        }

        val padding = width * 0.04f
        val rightEdge = width - padding
        val leftEdge = padding

        // Header color parsing (with fallback to dark blue)
        val hColor = try {
            Color.parseColor(config.headerColor)
        } catch (e: Exception) {
            Color.parseColor("#0F172A")
        }

        val headerHeight = width * 0.11f
        val headerRect = RectF(padding, padding, rightEdge, padding + headerHeight)
        val headerPaint = Paint().apply {
            color = hColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(headerRect, 14f, 14f, headerPaint)

        // Draw primary gold/accent bar under header
        val accentBarPaint = Paint().apply {
            color = Color.parseColor("#D97706")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(padding + 20f, padding + headerHeight - 10f, rightEdge - 20f, padding + headerHeight - 6f),
            2f, 2f, accentBarPaint
        )

        // Header Text
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.033f
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = width * 0.016f
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        canvas.drawText("پیش‌فاکتور فروش رسمی", rightEdge - (width * 0.035f), padding + (headerHeight * 0.45f), titlePaint)
        canvas.drawText(config.workshopName, rightEdge - (width * 0.035f), padding + (headerHeight * 0.76f), subTitlePaint)

        // Draw Customizable Logo
        if (config.showLogo) {
            val logoRadius = width * 0.032f
            val logoCenterX = leftEdge + width * 0.07f
            val logoCenterY = padding + (headerHeight * 0.5f)
            
            var drawnCustom = false
            if (config.useCustomLogo) {
                if (config.logoPath != null) {
                    if (config.logoPath.startsWith("preset_")) {
                        // Draw Preset Vector Logos!
                        val goldPaint = Paint().apply {
                            color = Color.parseColor("#D97706")
                            style = Paint.Style.STROKE
                            strokeWidth = width * 0.003f
                            isAntiAlias = true
                        }
                        val goldFill = Paint().apply {
                            color = Color.parseColor("#D97706")
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        
                        // Draw beautiful circle background first
                        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, goldPaint)
                        canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 4f, Paint(goldPaint).apply { strokeWidth = width * 0.001f })

                        when (config.logoPath) {
                            "preset_wood" -> {
                                // Draw stylized tree rings or wood log
                                val path = Path().apply {
                                    // A stylized evergreen pine tree shape
                                    moveTo(logoCenterX, logoCenterY - logoRadius * 0.6f)
                                    lineTo(logoCenterX - logoRadius * 0.4f, logoCenterY + logoRadius * 0.1f)
                                    lineTo(logoCenterX - logoRadius * 0.2f, logoCenterY + logoRadius * 0.1f)
                                    lineTo(logoCenterX - logoRadius * 0.5f, logoCenterY + logoRadius * 0.5f)
                                    lineTo(logoCenterX + logoRadius * 0.5f, logoCenterY + logoRadius * 0.5f)
                                    lineTo(logoCenterX + logoRadius * 0.2f, logoCenterY + logoRadius * 0.1f)
                                    lineTo(logoCenterX + logoRadius * 0.4f, logoCenterY + logoRadius * 0.1f)
                                    close()
                                }
                                canvas.drawPath(path, goldFill)
                                // Draw trunk
                                canvas.drawRect(
                                    logoCenterX - logoRadius * 0.1f,
                                    logoCenterY + logoRadius * 0.5f,
                                    logoCenterX + logoRadius * 0.1f,
                                    logoCenterY + logoRadius * 0.65f,
                                    goldFill
                                )
                                drawnCustom = true
                            }
                            "preset_sofa" -> {
                                // Draw a beautiful minimalist Sofa vector
                                val path = Path().apply {
                                    // Sofa seat
                                    moveTo(logoCenterX - logoRadius * 0.5f, logoCenterY + logoRadius * 0.1f)
                                    lineTo(logoCenterX + logoRadius * 0.5f, logoCenterY + logoRadius * 0.1f)
                                    lineTo(logoCenterX + logoRadius * 0.5f, logoCenterY + logoRadius * 0.35f)
                                    lineTo(logoCenterX - logoRadius * 0.5f, logoCenterY + logoRadius * 0.35f)
                                    close()
                                    // Backrest
                                    moveTo(logoCenterX - logoRadius * 0.4f, logoCenterY - logoRadius * 0.4f)
                                    lineTo(logoCenterX + logoRadius * 0.4f, logoCenterY - logoRadius * 0.4f)
                                    lineTo(logoCenterX + logoRadius * 0.4f, logoCenterY + logoRadius * 0.1f)
                                    lineTo(logoCenterX - logoRadius * 0.4f, logoCenterY + logoRadius * 0.1f)
                                    close()
                                    // Armrests
                                    moveTo(logoCenterX - logoRadius * 0.6f, logoCenterY - logoRadius * 0.1f)
                                    lineTo(logoCenterX - logoRadius * 0.4f, logoCenterY - logoRadius * 0.1f)
                                    lineTo(logoCenterX - logoRadius * 0.4f, logoCenterY + logoRadius * 0.35f)
                                    lineTo(logoCenterX - logoRadius * 0.6f, logoCenterY + logoRadius * 0.35f)
                                    close()
                                    
                                    moveTo(logoCenterX + logoRadius * 0.4f, logoCenterY - logoRadius * 0.1f)
                                    lineTo(logoCenterX + logoRadius * 0.6f, logoCenterY - logoRadius * 0.1f)
                                    lineTo(logoCenterX + logoRadius * 0.6f, logoCenterY + logoRadius * 0.35f)
                                    lineTo(logoCenterX + logoRadius * 0.4f, logoCenterY + logoRadius * 0.35f)
                                    close()
                                }
                                canvas.drawPath(path, goldFill)
                                // Sofa legs
                                canvas.drawRect(logoCenterX - logoRadius * 0.45f, logoCenterY + logoRadius * 0.35f, logoCenterX - logoRadius * 0.35f, logoCenterY + logoRadius * 0.5f, goldFill)
                                canvas.drawRect(logoCenterX + logoRadius * 0.35f, logoCenterY + logoRadius * 0.35f, logoCenterX + logoRadius * 0.45f, logoCenterY + logoRadius * 0.5f, goldFill)
                                drawnCustom = true
                            }
                            "preset_crown" -> {
                                // Draw an elegant Crown
                                val path = Path().apply {
                                    moveTo(logoCenterX - logoRadius * 0.5f, logoCenterY + logoRadius * 0.3f)
                                    lineTo(logoCenterX - logoRadius * 0.6f, logoCenterY - logoRadius * 0.3f)
                                    lineTo(logoCenterX - logoRadius * 0.25f, logoCenterY)
                                    lineTo(logoCenterX, logoCenterY - logoRadius * 0.4f)
                                    lineTo(logoCenterX + logoRadius * 0.25f, logoCenterY)
                                    lineTo(logoCenterX + logoRadius * 0.6f, logoCenterY - logoRadius * 0.3f)
                                    lineTo(logoCenterX + logoRadius * 0.5f, logoCenterY + logoRadius * 0.3f)
                                    close()
                                }
                                canvas.drawPath(path, goldFill)
                                // Gems under crown
                                canvas.drawCircle(logoCenterX - logoRadius * 0.6f, logoCenterY - logoRadius * 0.33f, 4f, goldFill)
                                canvas.drawCircle(logoCenterX, logoCenterY - logoRadius * 0.44f, 4f, goldFill)
                                canvas.drawCircle(logoCenterX + logoRadius * 0.6f, logoCenterY - logoRadius * 0.33f, 4f, goldFill)
                                drawnCustom = true
                            }
                            "preset_minimal" -> {
                                // Double overlapping square monogram
                                val strokePaint = Paint(goldPaint).apply { strokeWidth = width * 0.0025f }
                                canvas.save()
                                canvas.rotate(45f, logoCenterX, logoCenterY)
                                canvas.drawRect(logoCenterX - logoRadius * 0.5f, logoCenterY - logoRadius * 0.5f, logoCenterX + logoRadius * 0.5f, logoCenterY + logoRadius * 0.5f, strokePaint)
                                canvas.restore()
                                canvas.drawCircle(logoCenterX, logoCenterY, logoRadius * 0.4f, strokePaint)
                                val textPaint = Paint().apply {
                                    color = Color.parseColor("#D97706")
                                    textSize = width * 0.022f
                                    typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                                    textAlign = Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                canvas.drawText("M", logoCenterX, logoCenterY + width * 0.008f, textPaint)
                                drawnCustom = true
                            }
                            "preset_star" -> {
                                // Draw a beautiful star
                                val path = Path()
                                val points = 5
                                val outerRad = logoRadius * 0.6f
                                val innerRad = logoRadius * 0.25f
                                var angle = -Math.PI / 2
                                val increment = Math.PI / points
                                path.moveTo(
                                    (logoCenterX + Math.cos(angle) * outerRad).toFloat(),
                                    (logoCenterY + Math.sin(angle) * outerRad).toFloat()
                                )
                                for (i in 1..points * 2) {
                                    val r = if (i % 2 == 0) outerRad else innerRad
                                    angle += increment
                                    path.lineTo(
                                        (logoCenterX + Math.cos(angle) * r).toFloat(),
                                        (logoCenterY + Math.sin(angle) * r).toFloat()
                                    )
                                }
                                path.close()
                                canvas.drawPath(path, goldFill)
                                drawnCustom = true
                            }
                        }
                    } else {
                        try {
                            val logoFile = File(config.logoPath)
                            if (logoFile.exists()) {
                                val bitmap = BitmapFactory.decodeFile(config.logoPath)
                                if (bitmap != null) {
                                    val size = (logoRadius * 2).toInt()
                                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
                                    val path = Path().apply {
                                        addCircle(logoCenterX, logoCenterY, logoRadius, Path.Direction.CCW)
                                    }
                                    canvas.save()
                                    canvas.clipPath(path)
                                    canvas.drawBitmap(scaledBitmap, logoCenterX - logoRadius, logoCenterY - logoRadius, Paint(Paint.FILTER_BITMAP_FLAG))
                                    canvas.restore()
                                    
                                    val borderPaint = Paint().apply {
                                        color = Color.parseColor("#D97706")
                                        style = Paint.Style.STROKE
                                        strokeWidth = width * 0.002f
                                        isAntiAlias = true
                                    }
                                    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, borderPaint)
                                    drawnCustom = true
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                // Fallback to beautiful monogram initials if no file or preset matches
                if (!drawnCustom) {
                    val outerPaint = Paint().apply {
                        color = Color.parseColor("#D97706")
                        style = Paint.Style.STROKE
                        strokeWidth = width * 0.003f
                        isAntiAlias = true
                    }
                    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, outerPaint)
                    canvas.drawCircle(logoCenterX, logoCenterY, logoRadius - 4f, Paint(outerPaint).apply { strokeWidth = width * 0.001f })
                    
                    val initialText = if (config.workshopName.length >= 2) config.workshopName.take(2) else "ک"
                    val initialPaint = Paint().apply {
                        color = Color.parseColor("#D97706")
                        textSize = width * 0.024f
                        typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText(initialText, logoCenterX, logoCenterY + width * 0.008f, initialPaint)
                    drawnCustom = true
                }
            }
            
            if (!drawnCustom) {
                val logoPaint = Paint().apply {
                    color = Color.parseColor("#D97706")
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.004f
                    isAntiAlias = true
                }
                canvas.drawCircle(logoCenterX, logoCenterY, logoRadius, logoPaint)
                
                val logoFillPaint = Paint().apply {
                    color = Color.parseColor("#D97706")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(logoCenterX, logoCenterY, width * 0.009f, logoFillPaint)
                
                for (angle in 0..360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val startX = (logoCenterX + Math.cos(rad) * logoRadius).toFloat()
                    val startY = (logoCenterY + Math.sin(rad) * logoRadius).toFloat()
                    val endX = (logoCenterX + Math.cos(rad) * (logoRadius + width * 0.008f)).toFloat()
                    val endY = (logoCenterY + Math.sin(rad) * (logoRadius + width * 0.008f)).toFloat()
                    canvas.drawLine(startX, startY, endX, endY, logoPaint)
                }
            }
        }

        // Invoice Metadata
        val metaPaint = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.016f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
        val metaOffset = if (config.showLogo) width * 0.14f else width * 0.03f
        canvas.drawText("شماره فاکتور: #${transaction.id + 1000}", leftEdge + metaOffset, padding + (headerHeight * 0.45f), metaPaint)
        canvas.drawText("تاریخ صدور: ${formatDate(transaction.date)}", leftEdge + metaOffset, padding + (headerHeight * 0.76f), metaPaint)

        // Buyer / Seller Boxes
        val sectionY = padding + headerHeight + width * 0.04f
        val boxWidth = (width - 2 * padding - 20f) / 2
        val boxHeight = width * 0.18f

        val boxBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val boxBorderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }

        // Buyer box
        val buyerBoxRect = RectF(rightEdge - boxWidth, sectionY, rightEdge, sectionY + boxHeight)
        canvas.drawRoundRect(buyerBoxRect, 14f, 14f, boxBgPaint)
        canvas.drawRoundRect(buyerBoxRect, 14f, 14f, boxBorderPaint)

        // Seller box
        val sellerBoxRect = RectF(leftEdge, sectionY, leftEdge + boxWidth, sectionY + boxHeight)
        canvas.drawRoundRect(sellerBoxRect, 14f, 14f, boxBgPaint)
        canvas.drawRoundRect(sellerBoxRect, 14f, 14f, boxBorderPaint)

        // Draw modern vertical accent lines inside the right edge of each card (Farsi / RTL)
        val cardAccentPaint = Paint().apply {
            color = hColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        // Buyer accent bar (RTL: right side of buyer box)
        canvas.drawRoundRect(
            RectF(rightEdge - 8f, sectionY + 12f, rightEdge, sectionY + boxHeight - 12f),
            4f, 4f, cardAccentPaint
        )
        // Seller accent bar (RTL: right side of seller box)
        canvas.drawRoundRect(
            RectF(leftEdge + boxWidth - 8f, sectionY + 12f, leftEdge + boxWidth, sectionY + boxHeight - 12f),
            4f, 4f, cardAccentPaint
        )

        val boxTitlePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = width * 0.018f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        
        val boxTextPaint = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = width * 0.015f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        // Write Buyer info
        val rBoxX = rightEdge - width * 0.035f
        canvas.drawText("مشخصات خریدار / کارفرما", rBoxX, sectionY + width * 0.04f, boxTitlePaint)
        canvas.drawText("نام مشتری: ${transaction.customerName}", rBoxX, sectionY + width * 0.08f, boxTextPaint)
        canvas.drawText("تلفن همراه: ${customer?.phone ?: "-"}", rBoxX, sectionY + width * 0.115f, boxTextPaint)
        canvas.drawText("شرکت/واحد: ${customer?.company ?: "شخصی"}", rBoxX, sectionY + width * 0.15f, boxTextPaint)

        // Write Seller info
        val lBoxX = leftEdge + boxWidth - width * 0.035f
        canvas.drawText("مشخصات فروشنده / پیمانکار", lBoxX, sectionY + width * 0.04f, boxTitlePaint)
        canvas.drawText("نام واحد: ${config.workshopName}", lBoxX, sectionY + width * 0.08f, boxTextPaint)
        canvas.drawText("شماره تماس: ${config.phone}", lBoxX, sectionY + width * 0.115f, boxTextPaint)
        canvas.drawText("آدرس: ${config.address}", lBoxX, sectionY + width * 0.15f, boxTextPaint)

        // Deserialize multi-items or fallback
        val decoded = deserializeItems(transaction.description, transaction.title, transaction.quantity, transaction.unitPrice)
        val items = decoded.first
        val notes = decoded.second

        // Table headers Y
        val tableY = sectionY + boxHeight + width * 0.03f
        val tableHeaderHeight = width * 0.048f

        val tableHeaderPaint = Paint().apply {
            color = hColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val tableHeaderRect = RectF(leftEdge, tableY, rightEdge, tableY + tableHeaderHeight)
        canvas.drawRoundRect(tableHeaderRect, 10f, 10f, tableHeaderPaint)

        val thTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.015f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Col width ratios: 35 (Row), 180 (Item), 40 (Qty), 90 (Unit Price), 60 (Discount), 50 (Tax), 80 (Final) -> sum is 535
        val baseWidth = width - 2 * padding
        val colWidths = floatArrayOf(
            baseWidth * (35f / 535f),
            baseWidth * (180f / 535f),
            baseWidth * (40f / 535f),
            baseWidth * (90f / 535f),
            baseWidth * (60f / 535f),
            baseWidth * (50f / 535f),
            baseWidth * (80f / 535f)
        )
        
        fun getColCenterX(index: Int): Float {
            var sum = 0f
            for (i in 0 until index) {
                sum += colWidths[i]
            }
            return rightEdge - sum - (colWidths[index] / 2f)
        }

        canvas.drawText("ردیف", getColCenterX(0), tableY + tableHeaderHeight * 0.62f, thTextPaint)
        canvas.drawText("شرح کالا یا خدمات", rightEdge - colWidths[0] - width * 0.015f, tableY + tableHeaderHeight * 0.62f, Paint(thTextPaint).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("تعداد", getColCenterX(2), tableY + tableHeaderHeight * 0.62f, thTextPaint)
        canvas.drawText("قیمت واحد", getColCenterX(3), tableY + tableHeaderHeight * 0.62f, thTextPaint)
        canvas.drawText("تخفیف", getColCenterX(4), tableY + tableHeaderHeight * 0.62f, thTextPaint)
        canvas.drawText("مالیات", getColCenterX(5), tableY + tableHeaderHeight * 0.62f, thTextPaint)
        canvas.drawText("جمع کل", getColCenterX(6), tableY + tableHeaderHeight * 0.62f, thTextPaint)

        // Grid colors
        val gridPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0") // softer and much more professional
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
            isAntiAlias = true
        }

        val trTextPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = width * 0.014f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Draw multiple items row by row
        var currentY = tableY + tableHeaderHeight
        val rowHeight = width * 0.055f

        for (i in items.indices) {
            val item = items[i]
            val rowY = currentY + (i * rowHeight)

            // Alternate Row backgrounds
            val rowBg = Paint().apply {
                color = if (i % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            canvas.drawRect(leftEdge, rowY, rightEdge, rowY + rowHeight, rowBg)

            // Bottom horizontal border only (clean modern look)
            canvas.drawLine(leftEdge, rowY + rowHeight, rightEdge, rowY + rowHeight, gridPaint)
            
            // Side borders to close the table container
            canvas.drawLine(leftEdge, rowY, leftEdge, rowY + rowHeight, gridPaint)
            canvas.drawLine(rightEdge, rowY, rightEdge, rowY + rowHeight, gridPaint)

            // Subtle vertical dividers
            var colX = 0f
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#F1F5F9") // ultra subtle vertical dividers
                style = Paint.Style.STROKE
                strokeWidth = 1.0f
            }
            for (c in 0 until colWidths.size - 1) {
                colX += colWidths[c]
                canvas.drawLine(rightEdge - colX, rowY, rightEdge - colX, rowY + rowHeight, dividerPaint)
            }

            // Values
            canvas.drawText((i + 1).toString(), getColCenterX(0), rowY + rowHeight * 0.6f, trTextPaint)
            canvas.drawText(item.title, rightEdge - colWidths[0] - width * 0.015f, rowY + rowHeight * 0.6f, Paint(trTextPaint).apply {
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                color = Color.parseColor("#0F172A")
            })
            canvas.drawText(item.quantity.toString(), getColCenterX(2), rowY + rowHeight * 0.6f, trTextPaint)
            canvas.drawText(formatPrice(item.unitPrice), getColCenterX(3), rowY + rowHeight * 0.6f, trTextPaint)
            
            // Single row discount & tax is displayed as aggregated at the bottom or empty in row list
            canvas.drawText("-", getColCenterX(4), rowY + rowHeight * 0.6f, trTextPaint)
            canvas.drawText("-", getColCenterX(5), rowY + rowHeight * 0.6f, trTextPaint)

            val rowTotal = item.quantity * item.unitPrice
            canvas.drawText(formatPrice(rowTotal), getColCenterX(6), rowY + rowHeight * 0.6f, Paint(trTextPaint).apply {
                typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                color = Color.parseColor("#0F172A")
            })
        }

        // Dynamic summary positioning
        val tableBottomY = currentY + (items.size * rowHeight)
        val summaryY = tableBottomY + width * 0.03f
        val sumBoxWidth = width * 0.42f
        val sumBoxX = leftEdge + sumBoxWidth
        val sumBoxHeight = width * 0.22f

        // Draw calculation summary box
        val summaryBoxRect = RectF(leftEdge, summaryY, sumBoxX, summaryY + sumBoxHeight)
        val sumBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(summaryBoxRect, 14f, 14f, sumBgPaint)
        canvas.drawRoundRect(summaryBoxRect, 14f, 14f, boxBorderPaint)

        // Draw an elegant decorative emerald indicator bar indicating final payable
        val sumAccentPaint = Paint().apply {
            color = Color.parseColor("#10B981") // beautiful Emerald
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(leftEdge, summaryY + 12f, leftEdge + 6f, summaryY + sumBoxHeight - 12f),
            3f, 3f, sumAccentPaint
        )

        val sumTitlePaint = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = width * 0.015f
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val sumValPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = width * 0.015f
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }

        // Subtotal calculation (Sum of items)
        val calculatedSubtotal = items.sumOf { it.quantity * it.unitPrice }

        // Row 1: Gross subtotal
        canvas.drawText("جمع ناخالص اقلام:", sumBoxX - width * 0.02f, summaryY + sumBoxHeight * 0.22f, sumTitlePaint)
        canvas.drawText(formatPrice(calculatedSubtotal), leftEdge + width * 0.025f, summaryY + sumBoxHeight * 0.22f, sumValPaint)

        // Row 2: Discount
        canvas.drawText("تخفیف همکاری:", sumBoxX - width * 0.02f, summaryY + sumBoxHeight * 0.44f, Paint(sumTitlePaint).apply { color = Color.parseColor("#EF4444") })
        canvas.drawText("- ${formatPrice(transaction.discount)}", leftEdge + width * 0.025f, summaryY + sumBoxHeight * 0.44f, Paint(sumValPaint).apply { color = Color.parseColor("#EF4444") })

        // Row 3: Tax
        canvas.drawText("مالیات بر ارزش افزوده:", sumBoxX - width * 0.02f, summaryY + sumBoxHeight * 0.66f, sumTitlePaint)
        canvas.drawText("+ ${formatPrice(transaction.tax)}", leftEdge + width * 0.025f, summaryY + sumBoxHeight * 0.66f, sumValPaint)

        // Horizontal line
        canvas.drawLine(leftEdge + width * 0.02f, summaryY + sumBoxHeight * 0.76f, sumBoxX - width * 0.02f, summaryY + sumBoxHeight * 0.76f, gridPaint)

        // Row 4: Grand Total
        canvas.drawText("مبلغ قابل پرداخت:", sumBoxX - width * 0.02f, summaryY + sumBoxHeight * 0.9f, Paint(sumTitlePaint).apply {
            color = Color.parseColor("#0F172A")
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
            textSize = width * 0.016f
        })
        canvas.drawText(formatPrice(transaction.amount), leftEdge + width * 0.025f, summaryY + sumBoxHeight * 0.9f, Paint(sumValPaint).apply {
            color = Color.parseColor("#047857") // Emerald 700
            textSize = width * 0.017f
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        })

        // Draw terms and notes (or user contracts notes)
        if (config.showTerms) {
            val noteX = rightEdge
            val noteY = summaryY + width * 0.025f
            val noteTitlePaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                textSize = width * 0.016f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            val noteBodyPaint = Paint().apply {
                color = Color.parseColor("#64748B")
                textSize = width * 0.013f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }

            canvas.drawText("شرایط و ضوابط پیش‌فاکتور:", noteX, noteY, noteTitlePaint)

            canvas.drawText(config.terms1, noteX, noteY + width * 0.033f, noteBodyPaint)
            canvas.drawText(config.terms2, noteX, noteY + width * 0.061f, noteBodyPaint)
            canvas.drawText(config.terms3, noteX, noteY + width * 0.089f, noteBodyPaint)
            canvas.drawText(config.terms4, noteX, noteY + width * 0.117f, noteBodyPaint)

            // If user input custom notes in transaction description, show it as an additional note row
            if (notes.isNotEmpty() && notes.isNotBlank()) {
                canvas.drawText("توضیحات اختصاصی: $notes", noteX, noteY + width * 0.145f, Paint(noteBodyPaint).apply {
                    color = Color.parseColor("#0F172A")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                })
            }
        } else {
            // Just draw custom transaction description notes on the right side
            if (notes.isNotEmpty() && notes.isNotBlank()) {
                val noteX = rightEdge
                val noteY = summaryY + width * 0.025f
                val noteTitlePaint = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    textSize = width * 0.016f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                val noteBodyPaint = Paint().apply {
                    color = Color.parseColor("#334155")
                    textSize = width * 0.013f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("توضیحات و شرح قرارداد:", noteX, noteY, noteTitlePaint)
                canvas.drawText(notes, noteX, noteY + width * 0.035f, noteBodyPaint)
            }
        }

        // Signatures at bottom
        val sigY = height - width * 0.16f
        val sigLinePaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        }

        val sigWidth = width * 0.22f

        // Draw Custom Seller Signature if enabled
        if (config.useSignature && config.signaturePath != null) {
            try {
                val file = File(config.signaturePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(config.signaturePath)
                    if (bitmap != null) {
                        val sigDestRect = RectF(
                            leftEdge + width * 0.03f + (sigWidth / 2f) - width * 0.06f,
                            sigY - width * 0.08f,
                            leftEdge + width * 0.03f + (sigWidth / 2f) + width * 0.06f,
                            sigY
                        )
                        canvas.drawBitmap(bitmap, null, sigDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        canvas.drawLine(leftEdge + width * 0.03f, sigY, leftEdge + width * 0.03f + sigWidth, sigY, sigLinePaint)
        canvas.drawText("مهر و امضای فروشنده", leftEdge + width * 0.03f + (sigWidth / 2f), sigY + width * 0.03f, Paint(trTextPaint).apply {
            textAlign = Paint.Align.CENTER
            color = Color.parseColor("#475569")
            textSize = width * 0.014f
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        })

        canvas.drawLine(rightEdge - width * 0.03f - sigWidth, sigY, rightEdge - width * 0.03f, sigY, sigLinePaint)
        canvas.drawText("امضا و تایید خریدار", rightEdge - width * 0.03f - (sigWidth / 2f), sigY + width * 0.03f, Paint(trTextPaint).apply {
            textAlign = Paint.Align.CENTER
            color = Color.parseColor("#475569")
            textSize = width * 0.014f
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        })

        // Footer Brand Text
        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = width * 0.013f
            typeface = Typeface.create(baseTypeface, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("طراحی و صادر شده توسط سیستم حسابداری و مدیریت مشتریان پالکس", width / 2f, height - width * 0.04f, footerPaint)

        // Draw Customizable Watermark diagonal text
        if (config.showWatermark && config.watermarkText.isNotEmpty()) {
            canvas.save()
            // Rotate around the center of the page
            canvas.rotate(-35f, width / 2f, height / 2f)
            
            val watermarkPaint = Paint().apply {
                color = Color.RED
                alpha = 22 // very faint translucent red
                textSize = width * 0.075f
                typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(config.watermarkText, width / 2f, height / 2f, watermarkPaint)
            canvas.restore()
        }
    }

    // PDF generation trigger
    fun generatePdf(context: Context, transaction: Transaction, customer: Customer?): File {
        val pdfDocument = PdfDocument()
        val config = loadConfig(context)
        
        // A4 page specifications (595 x 842 points)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        
        // Draw onto the PDF canvas
        drawInvoice(page.canvas, 595f, 842f, transaction, customer, config)
        
        pdfDocument.finishPage(page)
        
        // Save PDF to cache directory
        val file = File(context.cacheDir, "invoice_${transaction.id}.pdf")
        val out = FileOutputStream(file)
        pdfDocument.writeTo(out)
        out.flush()
        out.close()
        pdfDocument.close()
        
        return file
    }

    // High quality Image generation trigger
    fun generateImage(context: Context, transaction: Transaction, customer: Customer?): File {
        // High resolution equivalent to A4 ratio
        val width = 1000f
        val height = 1414f
        val config = loadConfig(context)
        
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw invoice details on Bitmap
        drawInvoice(canvas, width, height, transaction, customer, config)
        
        // Save Bitmap to JPEG in cache directory
        val file = File(context.cacheDir, "invoice_${transaction.id}.jpg")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.flush()
        out.close()
        
        return file
    }

    // Share File via android standard Intent chooser
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
