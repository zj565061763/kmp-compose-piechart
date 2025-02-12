package com.sd.demo.kmp.compose_piechart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sd.lib.kmp.compose_piechart.FPieChart
import com.sd.lib.kmp.compose_piechart.PieSlice

@Composable
fun Sample(
  onClickBack: () -> Unit,
) {
  val items = remember {
    listOf(
      PieSlice(id = 1, value = 10f, color = Color.Red),
      PieSlice(id = 2, value = 10f, color = Color.Green),
      PieSlice(id = 3, value = 10f, color = Color.Blue),
      PieSlice(id = 4, value = 10f, color = Color.Cyan),
      PieSlice(id = 5, value = 0f, color = Color.Cyan),
      PieSlice(id = 6, value = 0f, color = Color.Cyan),
    )
  }

  var selectedId by remember(items) { mutableStateOf<Any?>(5) }

  RouteScaffold(
    title = "Sample",
    onClickBack = onClickBack,
  ) {
    FPieChart(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .background(Color.Yellow)
        .padding(5.dp),
      items = items,
      selectedId = selectedId,
      labelStyle = TextStyle(
        fontSize = 12.sp,
        color = Color.Black,
      ),
      hollowPercentage = 0.5f,
      onClickSlice = {
        selectedId = if (selectedId != it.id) it.id else null
      },
    )
  }
}