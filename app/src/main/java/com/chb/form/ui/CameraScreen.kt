@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.chb.form.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CameraScreen(onImageCaptured: (String) -> Unit, onBack: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    TopAppBar(title = { Text("ถ่ายรูป") })
    Spacer(Modifier.height(16.dp))
    Text("โหมดกล้องจะกลับมาในเวอร์ชันถัดไป ตอนนี้ข้ามไปก่อนได้ครับ")
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onImageCaptured(""); onBack() }) { Text("ข้าม") }
  }
}
