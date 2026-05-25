package space.ourmosaic.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ImageCropperDialog(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
    onConfirm: (x: Float, y: Float, size: Float) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    val density = LocalDensity.current
    val holeSizeDp = 300.dp
    val holeSizePx = with(density) { holeSizeDp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Transformable background image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerSize = it.size }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val containerW = containerSize.width.toFloat()
                                val containerH = containerSize.height.toFloat()
                                
                                if (containerW > 0 && containerH > 0) {
                                    val imgW = bitmap.width.toFloat()
                                    val imgH = bitmap.height.toFloat()
                                    val aspectImg = imgW / imgH
                                    val aspectContainer = containerW / containerH
                                    
                                    val displayedW: Float
                                    val displayedH: Float
                                    if (aspectImg > aspectContainer) {
                                        displayedW = containerW
                                        displayedH = containerW / aspectImg
                                    } else {
                                        displayedH = containerH
                                        displayedW = containerH * aspectImg
                                    }

                                    // Ensure scale is enough to cover the hole
                                    val minScaleW = holeSizePx / displayedW
                                    val minScaleH = holeSizePx / displayedH
                                    val minScale = maxOf(minScaleW, minScaleH)
                                    
                                    scale = (scale * zoom).coerceIn(minScale, maxOf(10f, minScale))

                                    val currentWidth = displayedW * scale
                                    val currentHeight = displayedH * scale
                                    
                                    val maxOffsetX = ((currentWidth - holeSizePx) / 2f).coerceAtLeast(0f)
                                    val maxOffsetY = ((currentHeight - holeSizePx) / 2f).coerceAtLeast(0f)
                                    
                                    val newOffset = offset + pan
                                    offset = Offset(
                                        newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                        newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    scale = (scale * zoom).coerceIn(1f, 10f)
                                    offset += pan
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                // 2. Dimmed overlay with square hole
                Box(Modifier.fillMaxSize()) {
                    // Top
                    Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).height(
                        with(density) { ((containerSize.height.toFloat() - holeSizePx) / 2).coerceAtLeast(0f).toDp() }
                    ).background(Color.Black.copy(alpha = 0.7f)))
                    
                    // Bottom
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(
                        with(density) { ((containerSize.height.toFloat() - holeSizePx) / 2).coerceAtLeast(0f).toDp() }
                    ).background(Color.Black.copy(alpha = 0.7f)))
                    
                    // Center row containing Left Dim - Hole - Right Dim
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(holeSizeDp)
                            .align(Alignment.Center)
                    ) {
                        Box(Modifier.fillMaxHeight().weight(1f).background(Color.Black.copy(alpha = 0.7f)))
                        Box(Modifier.size(holeSizeDp).border(2.dp, Color.White.copy(alpha = 0.5f)))
                        Box(Modifier.fillMaxHeight().weight(1f).background(Color.Black.copy(alpha = 0.7f)))
                    }
                }

                // 3. Title bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Crop Photo", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }

                // 4. Action bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = {
                                val containerW = containerSize.width.toFloat()
                                val containerH = containerSize.height.toFloat()

                                if (containerW == 0f || containerH == 0f) return@Button
                                
                                val imgW = bitmap.width.toFloat()
                                val imgH = bitmap.height.toFloat()
                                
                                val aspectImg = imgW / imgH
                                val aspectContainer = containerW / containerH
                                
                                val displayedW: Float
                                val displayedH: Float
                                if (aspectImg > aspectContainer) {
                                    displayedW = containerW
                                    displayedH = containerW / aspectImg
                                } else {
                                    displayedH = containerH
                                    displayedW = containerH * aspectImg
                                }

                                val currentWidth = displayedW * scale
                                val currentHeight = displayedH * scale
                                
                                // Image top-left relative to container center
                                val imgLeft = (containerW - currentWidth) / 2f + offset.x
                                val imgTop = (containerH - currentHeight) / 2f + offset.y
                                
                                // Hole top-left relative to container center
                                val holeLeft = (containerW - holeSizePx) / 2f
                                val holeTop = (containerH - holeSizePx) / 2f
                                
                                // Position of hole relative to image in image-ratio
                                val xOffsetPct = (holeLeft - imgLeft) / currentWidth
                                val yOffsetPct = (holeTop - imgTop) / currentHeight
                                
                                // Size of hole relative to image dimensions
                                // In our cropImage, sizePct is relative to min(width, height)
                                val sizePct = holeSizePx / (minOf(displayedW, displayedH) * scale)

                                onConfirm(
                                    xOffsetPct,
                                    yOffsetPct,
                                    sizePct
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
