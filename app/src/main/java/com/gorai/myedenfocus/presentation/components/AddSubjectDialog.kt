package com.gorai.myedenfocus.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gorai.myedenfocus.domain.model.Subject

@Composable
fun AddSubjectDialog(
    isOpen: Boolean,
    title: String = "Add/Update Subject",
    selectedColors: List<Color>,
    onColorChange: (List<Color>) -> Unit,
    subjectName: String,
    goalHours: String,
    onSubjectNameChange: (String) -> Unit,
    onGoalHoursChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmButtonClick: () -> Unit
) {
    var subjectNameError by rememberSaveable { mutableStateOf<String?>(null) }
    var goalHoursError by rememberSaveable { mutableStateOf<String?>(null) }

    subjectNameError = when {
        subjectName.isBlank() -> "Subject name cannot be empty."
        subjectName.length < 2 -> "Subject name cannot be less than 2 characters."
        subjectName.length > 20 -> "Subject name cannot be more than 20 characters."
        else -> null
    }

    goalHoursError = when {
        goalHours.isBlank() -> "Please enter goal study hours."
        goalHours.toFloatOrNull() == null -> "Please enter a valid number."
        goalHours.toFloat() < 1f -> "Goal study hours cannot be empty."
        goalHours.toFloat() > 1000f -> "Please set a maximum of 1000 hours"
        else -> null
    }

    if (isOpen) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = title) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Subject.subjectCardColors.forEach { colors ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(brush = Brush.verticalGradient(colors))
                                    .border(
                                        width = 2.dp,
                                        color = if (colors == selectedColors) Color.Black else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onColorChange(colors)
                                    }
                            )
                        }
                    }
                    
                    // Subject Name TextField
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { onSubjectNameChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "Subject Name") },
                        singleLine = true,
                        isError = subjectNameError != null && subjectName.isNotBlank(),
                        supportingText = { Text(text = subjectNameError.orEmpty()) }
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Goal Hours TextField
                    OutlinedTextField(
                        value = goalHours,
                        onValueChange = { onGoalHoursChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "Goal Hours") },
                        isError = goalHoursError != null && goalHours.isNotBlank(),
                        supportingText = { Text(text = goalHoursError.orEmpty()) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = "Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmButtonClick,
                    enabled = subjectNameError == null && goalHoursError == null
                ) {
                    Text(text = "Save")
                }
            }
        )
    }
}