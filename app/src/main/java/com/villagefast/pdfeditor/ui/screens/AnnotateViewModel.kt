package com.villagefast.pdfeditor.ui.screens

import android.graphics.Color
import android.net.Uri
import com.villagefast.pdfeditor.utils.AnnotationRect
import com.villagefast.pdfeditor.utils.PdfAnnotation
import com.villagefast.pdfeditor.utils.PdfAnnotator
import com.villagefast.pdfeditor.utils.PdfUtils
import com.villagefast.pdfeditor.utils.ShapeType
import com.villagefast.pdfeditor.utils.StampType
import com.villagefast.pdfeditor.utils.TextBlock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.villagefast.pdfeditor.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Annotation Screen.
 */
class AnnotateViewModel : ViewModel() {
    private val _state = MutableStateFlow(AnnotationUiState())
    val state: StateFlow<AnnotationUiState> = _state.asStateFlow()

    fun setSourcePdf(uri: Uri, name: String) {
        _state.value = _state.value.copy(sourceUri = uri, sourceName = name)
    }

    fun setAnnotationType(type: AnnotationTypeOption) {
        _state.value = _state.value.copy(selectedType = type)
    }

    fun setPageIndex(index: Int) {
        _state.value = _state.value.copy(pageIndex = index)
    }

    fun loadTextBlocks(context: android.content.Context) {
        val uri = _state.value.sourceUri ?: return
        val pageIndex = _state.value.pageIndex
        viewModelScope.launch {
            val blocks = PdfUtils.getTextBlocks(context, uri, pageIndex)
            _state.value = _state.value.copy(textBlocks = blocks)
        }
    }

    fun setAnnotationText(text: String) {
        _state.value = _state.value.copy(annotationText = text)
    }

    fun setColor(color: Int) {
        _state.value = _state.value.copy(selectedColor = color)
    }

    fun setStampType(type: StampType) {
        _state.value = _state.value.copy(stampType = type)
    }

    fun setPosition(x: Float, y: Float) {
        _state.value = _state.value.copy(positionX = x, positionY = y)
    }

    fun setSize(width: Float, height: Float) {
        _state.value = _state.value.copy(width = width, height = height)
    }

    fun addAnnotation() {
        val currentState = _state.value
        val annotation = createAnnotation(currentState) ?: return
        
        val newUndoStack = currentState.undoStack + listOf(currentState.annotations)
        val updatedAnnotations = currentState.annotations + annotation
        
        _state.value = currentState.copy(
            annotations = updatedAnnotations,
            undoStack = newUndoStack,
            redoStack = emptyList()
        )
    }

    fun addAnnotationForBlock(block: TextBlock) {
        val currentState = _state.value
        val annotation = when (currentState.selectedType) {
            AnnotationTypeOption.HIGHLIGHT -> PdfAnnotation.Highlight(
                pageIndex = currentState.pageIndex,
                rect = block.bounds,
                color = currentState.selectedColor,
                opacity = 0.4f
            )
            AnnotationTypeOption.UNDERLINE -> PdfAnnotation.Underline(
                pageIndex = currentState.pageIndex,
                startX = block.bounds.x,
                startY = block.bounds.y, // Bottom of block
                endX = block.bounds.x + block.bounds.width,
                endY = block.bounds.y,
                color = currentState.selectedColor,
                strokeWidth = 2f
            )
            AnnotationTypeOption.STRIKETHROUGH -> PdfAnnotation.Strikethrough(
                pageIndex = currentState.pageIndex,
                startX = block.bounds.x,
                startY = block.bounds.y + block.bounds.height / 2f, // Middle of block
                endX = block.bounds.x + block.bounds.width,
                endY = block.bounds.y + block.bounds.height / 2f,
                color = currentState.selectedColor,
                strokeWidth = 2f
            )
            else -> null
        } ?: return

        val newUndoStack = currentState.undoStack + listOf(currentState.annotations)
        val updatedAnnotations = currentState.annotations + annotation
        
        _state.value = currentState.copy(
            annotations = updatedAnnotations,
            undoStack = newUndoStack,
            redoStack = emptyList()
        )
    }

    fun undo() {
        val currentState = _state.value
        if (currentState.undoStack.isEmpty()) return
        
        val lastState = currentState.undoStack.last()
        val newUndoStack = currentState.undoStack.dropLast(1)
        val newRedoStack = currentState.redoStack + listOf(currentState.annotations)
        
        _state.value = currentState.copy(
            annotations = lastState,
            undoStack = newUndoStack,
            redoStack = newRedoStack
        )
    }

    fun redo() {
        val currentState = _state.value
        if (currentState.redoStack.isEmpty()) return
        
        val nextState = currentState.redoStack.last()
        val newRedoStack = currentState.redoStack.dropLast(1)
        val newUndoStack = currentState.undoStack + listOf(currentState.annotations)
        
        _state.value = currentState.copy(
            annotations = nextState,
            undoStack = newUndoStack,
            redoStack = newRedoStack
        )
    }

    fun removeAnnotation(index: Int) {
        val currentState = _state.value
        val updatedAnnotations = currentState.annotations.toMutableList()
        if (index in updatedAnnotations.indices) {
            val newUndoStack = currentState.undoStack + listOf(currentState.annotations)
            updatedAnnotations.removeAt(index)
            _state.value = currentState.copy(
                annotations = updatedAnnotations,
                undoStack = newUndoStack,
                redoStack = emptyList()
            )
        }
    }

    fun clearAnnotations() {
        val currentState = _state.value
        if (currentState.annotations.isEmpty()) return
        
        val newUndoStack = currentState.undoStack + listOf(currentState.annotations)
        _state.value = currentState.copy(
            annotations = emptyList(),
            undoStack = newUndoStack,
            redoStack = emptyList()
        )
    }

    private fun createAnnotation(state: AnnotationUiState): PdfAnnotation? {
        return when (state.selectedType) {
            AnnotationTypeOption.HAND -> null
            AnnotationTypeOption.HIGHLIGHT -> {
                PdfAnnotation.Highlight(
                    pageIndex = state.pageIndex,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    color = state.selectedColor,
                    opacity = 0.4f
                )
            }
            AnnotationTypeOption.STICKY_NOTE -> {
                PdfAnnotation.StickyNote(
                    pageIndex = state.pageIndex,
                    x = state.positionX,
                    y = state.positionY,
                    text = state.annotationText,
                    color = state.selectedColor,
                    width = state.width,
                    height = state.height
                )
            }
            AnnotationTypeOption.TEXT_BOX -> {
                PdfAnnotation.TextBox(
                    pageIndex = state.pageIndex,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    text = state.annotationText,
                    fontSize = 12f,
                    textColor = Color.BLACK,
                    backgroundColor = null,
                    borderColor = state.selectedColor
                )
            }
            AnnotationTypeOption.STAMP -> {
                PdfAnnotation.Stamp(
                    pageIndex = state.pageIndex,
                    stampType = state.stampType,
                    x = state.positionX,
                    y = state.positionY,
                    width = state.width,
                    height = state.height
                )
            }
            AnnotationTypeOption.RECTANGLE -> {
                PdfAnnotation.Shape(
                    pageIndex = state.pageIndex,
                    shapeType = ShapeType.RECTANGLE,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    strokeColor = state.selectedColor,
                    fillColor = null,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.CIRCLE -> {
                PdfAnnotation.Shape(
                    pageIndex = state.pageIndex,
                    shapeType = ShapeType.CIRCLE,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    strokeColor = state.selectedColor,
                    fillColor = null,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.ARROW -> {
                PdfAnnotation.Shape(
                    pageIndex = state.pageIndex,
                    shapeType = ShapeType.ARROW,
                    rect = AnnotationRect(state.positionX, state.positionY, state.width, state.height),
                    strokeColor = state.selectedColor,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.UNDERLINE -> {
                PdfAnnotation.Underline(
                    pageIndex = state.pageIndex,
                    startX = state.positionX,
                    startY = state.positionY,
                    endX = state.positionX + state.width,
                    endY = state.positionY,
                    color = state.selectedColor,
                    strokeWidth = 2f
                )
            }
            AnnotationTypeOption.STRIKETHROUGH -> {
                PdfAnnotation.Strikethrough(
                    pageIndex = state.pageIndex,
                    startX = state.positionX,
                    startY = state.positionY,
                    endX = state.positionX + state.width,
                    endY = state.positionY,
                    color = state.selectedColor,
                    strokeWidth = 2f
                )
            }
        }
    }

    fun applyAnnotations(
        context: android.content.Context,
        outputUri: Uri
    ) {
        val sourceUri = _state.value.sourceUri ?: return
        if (_state.value.annotations.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, progress = 0, error = null)
            val annotator = PdfAnnotator()
            val result = annotator.addAnnotations(
                context = context,
                inputUri = sourceUri,
                outputUri = outputUri,
                annotations = _state.value.annotations,
                progressCallback = { progress ->
                    _state.value = _state.value.copy(progress = progress)
                }
            )
            _state.value = _state.value.copy(
                isProcessing = false,
                isComplete = result.success,
                error = result.errorMessage,
                annotationsAdded = result.annotationsAdded,
                resultUri = if (result.success) outputUri else null
            )
        }
    }

    fun reset() {
        _state.value = AnnotationUiState()
    }
}

enum class AnnotationTypeOption(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HAND("Move", Icons.Default.PanTool),
    HIGHLIGHT("Highlight", Icons.Default.Highlight),
    UNDERLINE("Underline", Icons.Default.FormatUnderlined),
    STRIKETHROUGH("Strikethrough", Icons.Default.StrikethroughS),
    STICKY_NOTE("Sticky Note", Icons.Default.Note),
    TEXT_BOX("Text Box", Icons.Default.TextFields),
    RECTANGLE("Rectangle", Icons.Default.Rectangle),
    CIRCLE("Circle", Icons.Default.Circle),
    ARROW("Arrow", Icons.AutoMirrored.Filled.ArrowRightAlt),
    STAMP("Stamp", Icons.Default.Verified)
}

data class AnnotationUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val selectedType: AnnotationTypeOption = AnnotationTypeOption.HAND,
    val pageIndex: Int = 0,
    val annotationText: String = "",
    val selectedColor: Int = Color.YELLOW,
    val stampType: StampType = StampType.APPROVED,
    val positionX: Float = 100f,
    val positionY: Float = 700f,
    val width: Float = 200f,
    val height: Float = 30f,
    val textBlocks: List<TextBlock> = emptyList(),
    val annotations: List<PdfAnnotation> = emptyList(),
    val undoStack: List<List<PdfAnnotation>> = emptyList(),
    val redoStack: List<List<PdfAnnotation>> = emptyList(),
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    val annotationsAdded: Int = 0,
    val resultUri: Uri? = null
)
