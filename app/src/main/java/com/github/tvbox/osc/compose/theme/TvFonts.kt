package com.github.tvbox.osc.compose.theme

import android.content.Context
import android.graphics.fonts.Font
import android.graphics.fonts.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import com.github.tvbox.osc.R
import android.graphics.Typeface as AndroidTypeface
import android.graphics.fonts.FontFamily as AndroidFontFamily

/**
 * 内嵌字体：拉丁字符用 Inter，其余回退到思源黑体（Noto Sans SC），再回退系统字体。
 *
 * Compose 的 FontFamily 不支持在两套应用内字体之间按字形回退，
 * 因此用 Android 的 Typeface.CustomFallbackBuilder（API 29+）为每个字重构建回退链。
 */
object TvFonts {

    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

    @Volatile
    private var families: Map<FontWeight, FontFamily>? = null

    fun family(context: Context, weight: FontWeight): FontFamily {
        val all = families ?: synchronized(this) {
            families ?: build(context.applicationContext).also { families = it }
        }
        return all[weight] ?: all.getValue(FontWeight.Normal)
    }

    private fun build(context: Context): Map<FontWeight, FontFamily> {
        val res = context.resources
        fun fontFamily(vararg fonts: Pair<Int, Int>): AndroidFontFamily {
            val built = fonts.map { (id, weight) -> Font.Builder(res, id).setWeight(weight).build() }
            return AndroidFontFamily.Builder(built.first()).apply { built.drop(1).forEach(::addFont) }.build()
        }
        val latin = fontFamily(
            R.font.inter_regular to 400,
            R.font.inter_medium to 500,
            R.font.inter_semibold to 600,
        )
        val cjk = fontFamily(
            R.font.noto_sans_sc_regular to 400,
            R.font.noto_sans_sc_medium to 500,
            R.font.noto_sans_sc_semibold to 600,
        )
        return weights.associateWith { weight ->
            val typeface = AndroidTypeface.CustomFallbackBuilder(latin)
                .addCustomFallback(cjk)
                .setSystemFallback("sans-serif")
                .setStyle(FontStyle(weight.weight, FontStyle.FONT_SLANT_UPRIGHT))
                .build()
            FontFamily(Typeface(typeface))
        }
    }
}
