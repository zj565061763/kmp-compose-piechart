package com.sd.demo.kmp.compose_piechart

import kotlinx.serialization.Serializable

sealed interface AppRoute {
  @Serializable
  data object Home : AppRoute

  @Serializable
  data object Sample : AppRoute
}