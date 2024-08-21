package com.gorai.myedenfocus.presentation.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gorai.myedenfocus.domain.model.Session
import com.gorai.myedenfocus.domain.model.Subject
import com.gorai.myedenfocus.domain.model.Task
import com.gorai.myedenfocus.presentation.components.AddSubjectDialog
import com.gorai.myedenfocus.presentation.components.CountCard
import com.gorai.myedenfocus.presentation.components.DeleteDialog
import com.gorai.myedenfocus.presentation.components.SubjectCard
import com.gorai.myedenfocus.presentation.components.studySessionsList
import com.gorai.myedenfocus.presentation.components.tasksList
import com.gorai.myedenfocus.presentation.destinations.SessionScreenRouteDestination
import com.gorai.myedenfocus.presentation.destinations.SubjectScreenRouteDestination
import com.gorai.myedenfocus.presentation.destinations.TaskScreenRouteDestination
import com.gorai.myedenfocus.presentation.subject.SubjectScreenNavArgs
import com.gorai.myedenfocus.presentation.task.TaskScreenNavArgs
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination(start = true)
@Composable
fun DashBoardScreenRoute(
    navigator: DestinationsNavigator
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tasks by viewModel.recentSessions.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        tasks = tasks,
        recentSessions = recentSessions,
        onEvent = viewModel::onEvent,
        onSubjectCardClick = {
            subjectId -> subjectId?.let {
                val navArg = SubjectScreenNavArgs(subjectId = subjectId)
            navigator.navigate(SubjectScreenRouteDestination(navArgs = navArg))
            }
        },
        onTaskCardClick = {
            taskId ->
            val navArg = TaskScreenNavArgs(taskId = taskId, subjectId = null)
            navigator.navigate(TaskScreenRouteDestination(navArgs = navArg))
        },
        onStartSessionButtonClick = {
            navigator.navigate(SessionScreenRouteDestination())
        }
    )
}

@Composable
private fun DashboardScreen(
    state: DashboardState,
    tasks: List<Task>,
    recentSessions: List<Session>,
    onEvent: (DashboardEvent) -> Unit,
    onSubjectCardClick: (Int?) -> Unit,
    onTaskCardClick: (Int?) -> Unit,
    onStartSessionButtonClick: () -> Unit
) {

    val tasks = listOf(
        Task(
            title = "Prepare Notes",
            description = "Prepare notes for class",
            dueDate = 0L,
            priority = 0,
            relatedToSubject = "Biology",
            isComplete = false,
            taskId = 1,
            taskSubjectId = 1
        ),
        Task(
            title = "Do Homework",
            description = "Prepare notes for class",
            dueDate = 0L,
            priority = 1,
            relatedToSubject = "",
            isComplete = true,
            taskId = 2,
            taskSubjectId = 1
        ),
        Task(
            title = "Go Coaching",
            description = "Prepare notes for class",
            dueDate = 0L,
            priority = 2,
            relatedToSubject = "",
            isComplete = false,
            taskId = 3,
            taskSubjectId = 1
        ),
        Task(
            title = "Assignment",
            description = "Prepare notes for class",
            dueDate = 0L,
            priority = 3,
            relatedToSubject = "",
            isComplete = false,
            taskId = 4,
            taskSubjectId = 1
        ),
        Task(
            title = "Write Poem",
            description = "Prepare notes for class",
            dueDate = 0L,
            priority = 4,
            relatedToSubject = "",
            isComplete = false,
            taskId = 5,
            taskSubjectId = 1
        )
    )

    val sessions = listOf(
        Session(
            relatedToSubject = "DBMS",
            date = 0L,
            duration = 2,
            sessionSubjectId = 0,
            sessionId = 0
        ),
        Session(
            relatedToSubject = "OOPS",
            date = 0L,
            duration = 2,
            sessionSubjectId = 1,
            sessionId = 0
        ),
        Session(
            relatedToSubject = "OS",
            date = 0L,
            duration = 2,
            sessionSubjectId = 2,
            sessionId = 0
        ),
        Session(
            relatedToSubject = "SE",
            date = 0L,
            duration = 2,
            sessionSubjectId = 3,
            sessionId = 0
        )
    )

    var isAddSubjectDialogOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var isDeleteSubjectDialogOpen by rememberSaveable {
        mutableStateOf(false)
    }

    AddSubjectDialog(
        isOpen = isAddSubjectDialogOpen,
        onDismissRequest = { isAddSubjectDialogOpen = false },
        onConfirmButtonClick = {
            isAddSubjectDialogOpen = false
            onEvent(DashboardEvent.SaveSubject)
        },
        subjectName = state.subjectName,
        goalHours = state.goalStudyHours,
        onSubjectNameChange = { onEvent(DashboardEvent.OnSubjectNameChange(it)) },
        onGoalHoursChange = { onEvent(DashboardEvent.OnGoalStudyHoursChange(it)) },
        selectedColors = state.subjectCardColors,
        onColorChange = { onEvent(DashboardEvent.OnSubjectCardColorChange(it)) }
    )

    DeleteDialog(
        isOpen = isDeleteSubjectDialogOpen,
        title = "Delete Session?",
        bodyText = "Are you sure you want to delete this session?",
        onDismissRequest = { isDeleteSubjectDialogOpen = false },
        onConfirmButtonClick = {
            isDeleteSubjectDialogOpen = false
            onEvent(DashboardEvent.DeleteSubject)
        }
    )

    Scaffold(
        topBar = {
            DashboardScreenTopBar()
        }
        ) {
        paddingValues -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item { 
                CountCardsSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    subjectCount = state.totalSubjectCount,
                    studiedHours = state.totalStudiedHours.toString(),
                    goalHours = state.totalStudiedHours.toString()
                )
            }
            item {
                SubjectCardSection(
                    modifier = Modifier.fillMaxWidth(),
                    subjectList = state.subjects,
                    onAddIconClicked = {
                        isAddSubjectDialogOpen = true
                    },
                    onSubjectCardClick = onSubjectCardClick
                )
            }
            item {
                Button(
                    onClick = onStartSessionButtonClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 20.dp)
                ) {
                    Text(text = "Start Study Session")
                }
            }
            tasksList(
                sectionTitle = "Upcoming Tasks",
                emptyListText = "No upcoming tasks\n" + "Press + button to add new tasks",
                tasks = tasks,
                onTaskCardClick = onTaskCardClick,
                onCheckBoxClick = { onEvent(DashboardEvent.OnTaskIsCompleteChange(it)) }
            )
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
            studySessionsList(
                sectionTitle = "Recent Study Sessions",
                emptyListText = "No study sessions\n" + "Start a study session to begin recording your progress",
                sessions = recentSessions,
                onDeleteIconClick = { isDeleteSubjectDialogOpen = true
                    onEvent(DashboardEvent.OnDeleteSessionButtonClick(it))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreenTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "MyedenFocus",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    )
}

@Composable
private fun CountCardsSection(
    modifier: Modifier,
    subjectCount: Int,
    studiedHours: String,
    goalHours: String
) {
    Row(modifier = modifier) {
        CountCard(modifier = modifier.weight(1f), headingText =  "Subject Count", count = "$subjectCount")
        Spacer(modifier = Modifier.width(2.dp))
        CountCard(modifier = modifier.weight(1f), headingText =  "Studied Hours", count = studiedHours)
        Spacer(modifier = Modifier.width(2.dp))
        CountCard(modifier = modifier.weight(1f), headingText =  "Goal Hours", count = goalHours)
    }
}

@Composable
private fun SubjectCardSection(
    modifier: Modifier,
    subjectList: List<Subject>,
    emptyListText: String = "No subjects yet.\n Press + button to add new subjects\n",
    onAddIconClicked: () -> Unit,
    onSubjectCardClick: (Int) -> Unit
) {

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Subjects",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp)
            )
            IconButton(onClick = onAddIconClicked) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Subject",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (subjectList.isEmpty()) {
            Image(
                painter = painterResource(com.gorai.myedenfocus.R.drawable.img_books),
                contentDescription = emptyListText,
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = "No Subjects",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp)
        ) {
            items(subjectList) { subject ->
                SubjectCard(
                    subjectName = subject.name,
                    gradientColors = subject.colors.map { Color(it) },
                    onClick = { subject.subjectId?.let { onSubjectCardClick(it) } }
                )
            }
        }
    }
}