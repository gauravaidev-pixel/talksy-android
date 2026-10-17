package com.talksy.app.ui.screens.polls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.AppGraph
import kotlinx.coroutines.launch

class CreatePollViewModel : ViewModel() {
    fun create(chatId: String, question: String, options: List<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            AppGraph.polls.create(chatId, question, options).onSuccess { onDone() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePollScreen(nav: NavController, chatId: String, vm: CreatePollViewModel = viewModel()) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New poll") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                        onClick = {
                            vm.create(chatId, question.trim(), options.filter { it.isNotBlank() }) {
                                nav.popBackStack()
                            }
                        },
                    ) { Text("Create") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                maxLines = 3,
            )
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(options) { index, value ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[index] = it },
                            label = { Text("Option ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = { if (options.size < 10) options.add("") },
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add option")
            }
        }
    }
}
