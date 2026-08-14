package space.ourmosaic.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.system.AppSettings
import space.ourmosaic.app.system.SerializedOffset
import space.ourmosaic.app.system.SerializedPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    i18n: I18nState,
    appSettings: AppSettings,
    onBack: () -> Unit
) {
    var paths by remember { mutableStateOf(appSettings.drawingData.map { it.points }) }
    var currentPath by remember { mutableStateOf<List<SerializedOffset>?>(null) }

    val composePaths = remember(paths, currentPath) {
        val list = mutableListOf<Path>()
        paths.forEach { points ->
            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                list.add(path)
            }
        }
        currentPath?.let { points ->
            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                list.add(path)
            }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.DrawTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                    }
                },
                actions = {
                    IconButton(onClick = { paths = emptyList() }) {
                        Icon(Icons.Default.Clear, contentDescription = i18n.text(MessageKey.PowEffacer))
                    }
                    IconButton(onClick = {
                        appSettings.drawingData = paths.map { SerializedPath(it) }
                        onBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = i18n.text(MessageKey.DrawSave))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = listOf(SerializedOffset(offset.x, offset.y))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath = currentPath?.plus(SerializedOffset(change.position.x, change.position.y))
                            },
                            onDragEnd = {
                                currentPath?.let { paths = paths + listOf(it) }
                                currentPath = null
                            }
                        )
                    }
            ) {
                composePaths.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.Gray,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
