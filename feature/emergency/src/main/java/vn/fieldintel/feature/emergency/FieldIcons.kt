package vn.fieldintel.feature.emergency

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

/** Consistent vector symbols; no font glyphs or device dependent emoji. */
@Composable
internal fun FieldIcon(section: AppSection?, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * .085f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1*w,y1*h), Offset(x2*w,y2*h), sw, cap = StrokeCap.Round)
        fun path(vararg p: Pair<Float,Float>, close: Boolean = false) {
            val shape = Path().apply {
                moveTo(p[0].first*w,p[0].second*h)
                p.drop(1).forEach { lineTo(it.first*w,it.second*h) }
                if(close) close()
            }
            drawPath(shape,color,style=Stroke(width=sw,cap=StrokeCap.Round))
        }
        when(section) {
            null -> {
                path(.14f to .47f,.5f to .17f,.86f to .47f)
                path(.22f to .44f,.22f to .84f,.78f to .84f,.78f to .44f)
                line(.43f,.84f,.43f,.63f); line(.57f,.63f,.57f,.84f)
            }
            AppSection.FIELD -> {
                path(.1f to .2f,.34f to .12f,.65f to .22f,.9f to .12f,.9f to .79f,.65f to .88f,.34f to .78f,.1f to .88f,close=true)
                line(.34f,.12f,.34f,.78f); line(.65f,.22f,.65f,.88f)
            }
            AppSection.RECOGNITION -> {
                path(.12f to .34f,.24f to .34f,.31f to .22f,.69f to .22f,.76f to .34f,.88f to .34f,.88f to .79f,.12f to .79f,close=true)
                drawCircle(color,w*.16f,Offset(w*.5f,h*.55f),style=Stroke(width=sw))
            }
            AppSection.LIBRARY -> {
                path(.18f to .16f,.5f to .25f,.5f to .87f,.18f to .78f,close=true)
                path(.5f to .25f,.82f to .16f,.82f to .78f,.5f to .87f)
            }
            AppSection.SETTINGS -> {
                drawCircle(color,w*.27f,Offset(w*.5f,h*.5f),style=Stroke(width=sw))
                drawCircle(color,w*.09f,Offset(w*.5f,h*.5f))
                for (i in 0 until 8) {
                    val angle=i*Math.PI/4
                    val dx=kotlin.math.cos(angle).toFloat()
                    val dy=kotlin.math.sin(angle).toFloat()
                    line(.5f+dx*.33f,.5f+dy*.33f,.5f+dx*.43f,.5f+dy*.43f)
                }
            }
            AppSection.SURVIVAL -> {
                path(.12f to .8f,.5f to .18f,.88f to .8f,close=true)
                line(.5f,.18f,.5f,.8f); line(.36f,.8f,.5f,.59f); line(.5f,.59f,.64f,.8f)
            }
            AppSection.EMERGENCY -> {
                path(.5f to .1f,.82f to .23f,.78f to .68f,.5f to .91f,.22f to .68f,.18f to .23f,close=true)
                line(.5f,.33f,.5f,.55f); drawCircle(color,w*.035f,Offset(w*.5f,h*.7f))
            }
            AppSection.PREP -> {
                path(.25f to .27f,.75f to .27f,.83f to .43f,.79f to .85f,.21f to .85f,.17f to .43f,close=true)
                path(.36f to .27f,.36f to .17f,.64f to .17f,.64f to .27f)
                line(.36f,.55f,.64f,.55f)
            }
            AppSection.TRAINING -> {
                path(.13f to .29f,.5f to .12f,.87f to .29f,.5f to .46f,close=true)
                line(.25f,.38f,.25f,.69f); line(.75f,.38f,.75f,.69f)
                path(.25f to .69f,.5f to .84f,.75f to .69f)
            }
        }
    }
}
