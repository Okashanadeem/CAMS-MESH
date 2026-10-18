package com.cams.mesh.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cams.mesh.data.local.entity.MaterialEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MaterialCard(
    material: MaterialEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (material.contentType == "TEXT") {
                Icons.Default.Description
            } else when (material.mimeType) {
                "application/pdf" -> Icons.Default.PictureAsPdf
                "image/png", "image/jpeg", "image/webp" -> Icons.Default.Image
                else -> Icons.Default.InsertDriveFile
            }

            Icon(
                imageVector = icon,
                contentDescription = material.contentType,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = material.uploaderDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dateFormatted = remember(material.createdAt) {
                        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(material.createdAt))
                    }
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Availability badge
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = if (material.isUploadedByLocalUser) "Uploaded" else "Received",
                        fontSize = 11.sp
                    )
                }
            )
        }
    }
}

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Int = 512
) {
    val bitmap = remember(content) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val w = bitMatrix.width
            val h = bitMatrix.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Room QR Code",
            modifier = modifier
        )
    }
}
