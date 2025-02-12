package com.sd.demo.kmp.compose_piechart

import android.util.Log

actual fun logMsg(tag: String, block: () -> String) {
  Log.d(tag, block())
}