package com.chb.form.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.chb.form.ui.theme.*
import java.io.File

@Composable
fun DormPdfResultScreen(
    file: File,
    onBackToForm: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    fun shareOrOpen(action: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(action).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (action == Intent.ACTION_SEND) {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    type = "application/pdf"
                }
            }
            context.startActivity(Intent.createChooser(intent, "เลือกแอป"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        IconButton(onClick = onBackToForm) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "กลับ", tint = NeoBlack)
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = PistachioDark,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "สร้าง PDF สำเร็จ",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = NeoBlack
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = file.name,
                fontSize = 14.sp,
                color = NeoMutedText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { shareOrOpen(Intent.ACTION_VIEW) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PistachioDark)
            ) {
                Icon(Icons.Rounded.Visibility, null, tint = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.width(10.dp))
                Text("เปิดดู PDF", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { shareOrOpen(Intent.ACTION_SEND) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoBorder)
            ) {
                Icon(Icons.Rounded.Share, null, tint = NeoBlack)
                Spacer(Modifier.width(10.dp))
                Text("แชร์ไฟล์", color = NeoBlack, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onBackToForm) {
                Text("กลับไปแก้ไขฟอร์ม", color = PistachioDark)
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
