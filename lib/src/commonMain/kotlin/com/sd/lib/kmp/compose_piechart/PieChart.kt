package com.sd.lib.kmp.compose_piechart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class PieSlice(
  val id: Any,
  val value: Float,
  val color: Color,
  val label: String? = null,
)

@Composable
fun FPieChart(
  modifier: Modifier = Modifier,
  items: List<PieSlice>,
  selectedId: Any? = null,
  selectedScale: Float = 1.1f,
  hollowPercentage: Float = 0f,
  pieBackground: Color = Color.LightGray,
  labelStyle: TextStyle = TextStyle.Default,
  labelLineSpacing: Dp = 2.dp,
  labelLineHorizontalSize: Dp = 16.dp,
  labelLineInsertSize: Dp = 4.dp,
  labelLineWidth: Dp = 1.dp,
  onClickSlice: ((PieSlice) -> Unit)? = null,
) {
  val textMeasurer = rememberTextMeasurer()

  val chartData = getChartData(
    listPieSlice = items,
    textMeasurer = textMeasurer,
    labelStyle = labelStyle,
  ) ?: return

  LaunchedEffect(chartData, selectedId, selectedScale) {
    for (item in chartData.items) {
      val itemSelected = item.slice.id == selectedId
      launch { item.scaleAnim.animateTo(if (itemSelected) selectedScale else 1f) }
    }
  }

  var centerOffset by remember { mutableStateOf(Offset.Zero) }

  Canvas(
    modifier = modifier
      .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
      .handleClickSlice(
        chartData = chartData,
        center = centerOffset,
        onClickSlice = onClickSlice,
      )
  ) {
    centerOffset = this.center

    val hasLabel = chartData.labelMaxWidth > 0 && chartData.labelMaxHeight > 0
    if (hasLabel) {
      drawChartWithLabel(
        chartData = chartData,
        hollowPercentage = hollowPercentage,
        selectedId = selectedId,
        selectedScale = selectedScale,
        pieBackground = pieBackground,
        textMeasurer = textMeasurer,
        labelStyle = labelStyle,
        labelLineSpacing = labelLineSpacing,
        labelLineHorizontalSize = labelLineHorizontalSize,
        labelLineInsertSize = labelLineInsertSize,
        labelLineWidth = labelLineWidth,
      )
    } else {
      drawChartWithoutLabel(
        chartData = chartData,
        hollowPercentage = hollowPercentage,
        selectedScale = selectedScale,
        pieBackground = pieBackground,
      )
    }
  }
}

@Composable
private fun getChartData(
  listPieSlice: List<PieSlice>,
  textMeasurer: TextMeasurer,
  labelStyle: TextStyle,
): PieChartData? {
  return produceState<PieChartData?>(null, listPieSlice, textMeasurer) {
    val total = listPieSlice.fold(0f) { acc, item -> if (item.value > 0f) acc + item.value else acc }

    val listItem = mutableListOf<PieSliceItem>()
    var startAngle = -90f
    var labelMaxWidth = 0
    var labelMaxHeight = 0

    for (slice in listPieSlice) {
      if (slice.value < 0f) continue

      val percent = (if (total > 0f) slice.value / total else 0f).coerceIn(0f, 1f)
      val sweepAngle = percent * 360f

      val label = slice.label ?: percent.formatPercent()
      val labelSize = textMeasurer.measure(
        text = label,
        style = labelStyle,
      ).size.also { size ->
        if (size.width > labelMaxWidth) labelMaxWidth = size.width
        if (size.height > labelMaxHeight) labelMaxHeight = size.height
      }

      val sliceInfo = PieSliceItem(
        slice = slice,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        label = label,
        labelSize = labelSize,
      )

      listItem.add(sliceInfo)
      startAngle += sweepAngle
    }

    value = PieChartData(
      total = total,
      items = listItem,
      labelMaxWidth = labelMaxWidth,
      labelMaxHeight = labelMaxHeight,
    )
  }.value
}

private fun Modifier.handleClickSlice(
  chartData: PieChartData,
  center: Offset,
  onClickSlice: ((PieSlice) -> Unit)?,
): Modifier {
  if (onClickSlice == null) return this
  return composed {
    val callbackUpdated by rememberUpdatedState(onClickSlice)
    pointerInput(chartData, center) {
      detectTapGestures { offset ->
        for (item in chartData.items) {
          val touchSlice = isPointInPieSlice(
            point = offset,
            center = center,
            radius = chartData.radius * item.scale,
            startAngle = item.startAngle,
            sweepAngle = item.sweepAngle,
          )
          if (touchSlice) {
            callbackUpdated(item.slice)
            break
          }
        }
      }
    }
  }
}

private fun DrawScope.drawChartWithLabel(
  chartData: PieChartData,
  hollowPercentage: Float,
  selectedId: Any?,
  selectedScale: Float,
  pieBackground: Color,
  textMeasurer: TextMeasurer,
  labelStyle: TextStyle,
  labelLineWidth: Dp,
  labelLineSpacing: Dp,
  labelLineHorizontalSize: Dp,
  labelLineInsertSize: Dp,
) {
  val labelLineSpacingPx = labelLineSpacing.toPx()
  val labelLineHorizontalSizePx = labelLineHorizontalSize.toPx()
  val labelLineInsertSizePx = labelLineInsertSize.toPx()

  val safeWidth = size.width - (chartData.labelMaxWidth + labelLineSpacingPx + labelLineHorizontalSizePx) * 2f
  if (safeWidth <= 0f) return

  val safeHeight = size.height - chartData.labelMaxHeight * 2f
  if (safeHeight <= 0f) return

  val radius = min(safeWidth, safeHeight) / 2f / selectedScale
  chartData.radius = radius

  if (chartData.total <= 0f) {
    drawCircle(
      color = pieBackground,
      radius = radius,
    )
  }

  for (item in chartData.items) {
    drawChartArc(
      item = item,
      radius = radius,
      centerX = center.x,
      centerY = center.y,
    )

    val selected = selectedId == item.slice.id
    if (selected) {
      drawChartLabel(
        item = item,
        textMeasurer = textMeasurer,
        radius = radius,
        center = center,
        labelLineWidthPx = labelLineWidth.toPx(),
        labelLineSpacingPx = labelLineSpacingPx,
        labelLineHorizontalSizePx = labelLineHorizontalSizePx,
        labelLineInsertSizePx = labelLineInsertSizePx,
        labelStyle = labelStyle,
      )
    }
  }

  drawChartHollow(
    radius = radius,
    hollowPercentage = hollowPercentage,
  )
}

private fun DrawScope.drawChartWithoutLabel(
  chartData: PieChartData,
  hollowPercentage: Float,
  selectedScale: Float,
  pieBackground: Color,
) {
  val safeWidth = size.width
  if (safeWidth <= 0f) return

  val safeHeight = size.height
  if (safeHeight <= 0f) return

  val radius = min(safeWidth, safeHeight) / 2f / selectedScale
  chartData.radius = radius

  if (chartData.total <= 0f) {
    drawCircle(
      color = pieBackground,
      radius = radius,
    )
  }

  for (item in chartData.items) {
    drawChartArc(
      item = item,
      radius = radius,
      centerX = center.x,
      centerY = center.y,
    )
  }

  drawChartHollow(
    radius = radius,
    hollowPercentage = hollowPercentage,
  )
}

private fun DrawScope.drawChartArc(
  item: PieSliceItem,
  radius: Float,
  centerX: Float,
  centerY: Float,
) {
  val radiusWithScale = radius * item.scale
  val sizeWithScale = Size(radius * 2, radius * 2) * item.scale
  drawArc(
    color = item.slice.color,
    startAngle = item.startAngle,
    sweepAngle = item.sweepAngle,
    topLeft = Offset(centerX - radiusWithScale, centerY - radiusWithScale),
    useCenter = true,
    size = sizeWithScale,
  )
}

private fun DrawScope.drawChartHollow(
  radius: Float,
  hollowPercentage: Float,
) {
  drawCircle(
    color = Color.Transparent,
    radius = radius * hollowPercentage,
    center = center,
    blendMode = BlendMode.Clear,
  )
}

private fun DrawScope.drawChartLabel(
  item: PieSliceItem,
  textMeasurer: TextMeasurer,
  radius: Float,
  center: Offset,
  labelLineWidthPx: Float,
  labelLineSpacingPx: Float,
  labelLineHorizontalSizePx: Float,
  labelLineInsertSizePx: Float,
  labelStyle: TextStyle,
) {
  val startAngle = item.startAngle
  val endAngle = item.startAngle + item.sweepAngle
  val radiusWithScale = if (item.slice.value > 0f) radius * item.scale else radius

  // 边缘中心点
  val edgeCenter = calculateArcEdgeCenter(
    startAngle = startAngle,
    endAngle = endAngle,
    radius = radiusWithScale,
    center = center,
  )

  // label定位参考点
  val labelRelativeOffset = edgeCenter

  val labelLeftOrRight = labelRelativeOffset.x < center.x
  val labelLineStartOffset = Offset(
    x = if (labelLeftOrRight) labelRelativeOffset.x - labelLineHorizontalSizePx
    else labelRelativeOffset.x + labelLineHorizontalSizePx,
    y = labelRelativeOffset.y,
  )

  drawText(
    textMeasurer = textMeasurer,
    text = item.label,
    topLeft = Offset(
      x = if (labelLeftOrRight) labelRelativeOffset.x - item.labelSize.width - labelLineSpacingPx - labelLineHorizontalSizePx
      else labelRelativeOffset.x + labelLineSpacingPx + labelLineHorizontalSizePx,
      y = labelRelativeOffset.y - item.labelSize.height / 2,
    ),
    style = labelStyle,
  )

  // 边缘插入中心点
  val edgeInsertCenter = calculateArcEdgeCenter(
    startAngle = startAngle,
    endAngle = endAngle,
    radius = radiusWithScale - labelLineInsertSizePx,
    center = center,
  )

  val linePath = Path().apply {
    moveTo(x = labelLineStartOffset.x, y = labelLineStartOffset.y)
    lineTo(x = edgeCenter.x, y = edgeCenter.y)
    lineTo(x = edgeInsertCenter.x, y = edgeInsertCenter.y)
  }

  drawPath(
    path = linePath,
    color = labelStyle.color,
    style = Stroke(
      width = labelLineWidthPx,
      cap = StrokeCap.Round,
      join = StrokeJoin.Round,
    )
  )
}

private data class PieChartData(
  val total: Float,
  val items: List<PieSliceItem>,
  val labelMaxWidth: Int,
  val labelMaxHeight: Int,
) {
  var radius: Float = 0f
}

private data class PieSliceItem(
  val slice: PieSlice,
  val startAngle: Float,
  val sweepAngle: Float,
  val label: String,
  val labelSize: IntSize,
) {
  val scale: Float get() = scaleAnim.value
  val scaleAnim = Animatable(1f)
}

private fun calculateArcEdgeCenter(
  startAngle: Float,
  endAngle: Float,
  radius: Float,
  center: Offset,
): Offset {
  val normalizedCenter = normalizeDegrees((startAngle + endAngle) / 2f)
  val centerAngleRad = toRadians(normalizedCenter.toDouble())
  val x = radius * cos(centerAngleRad).toFloat()
  val y = radius * sin(centerAngleRad).toFloat()
  return Offset(x, y) + center
}

private fun isPointInPieSlice(
  point: Offset,
  center: Offset,
  radius: Float,
  startAngle: Float,
  sweepAngle: Float,
): Boolean {
  val dx = point.x - center.x
  val dy = point.y - center.y

  val distance = sqrt(dx * dx + dy * dy)
  if (distance > radius) return false

  if (sweepAngle >= 360f) return true

  val pointRadians = atan2(dy, dx)
  val pointDegrees = toDegrees(pointRadians.toDouble()).toFloat()

  val normalizedAngle = normalizeDegrees(pointDegrees)
  val normalizedStart = normalizeDegrees(startAngle)
  val normalizedEnd = normalizeDegrees(startAngle + sweepAngle)

  return if (normalizedStart <= normalizedEnd) {
    normalizedAngle in normalizedStart..normalizedEnd
  } else {
    normalizedAngle in normalizedStart..360f || normalizedAngle in 0f..normalizedEnd
  }
}

private fun normalizeDegrees(value: Float): Float = (value % 360f).let { if (it < 0f) it + 360f else it }
private fun toRadians(value: Double): Double = value * (PI / 180)
private fun toDegrees(value: Double): Double = value * (180 / PI)

private fun Float.formatPercent(decimal: Int = 1): String {
  val percent = this * 100f
  val factor = 10f.pow(decimal)
  val round = round(percent * factor) / factor
  return "${round}%"
}