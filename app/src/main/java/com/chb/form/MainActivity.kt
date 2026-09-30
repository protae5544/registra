package com.chb.form

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.ui.CameraScreen
import com.chb.form.ui.FormWizard
import com.chb.form.ui.PreviewScreen
import com.chb.form.ui.SignaturePad
import com.chb.form.ui.theme.ChbTheme
import com.chb.form.vm.FormViewModel
import com.chb.form.vm.Ui
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: FormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChbTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val snack = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.Form) }

                BackHandler(enabled = screen != Screen.Form) { screen = Screen.Form }

                LaunchedEffect(Unit) { vm.toast.collect { snack.showSnackbar(it) } }

                LaunchedEffect(ui) {
                    when (val u = ui) {
                        is Ui.Done -> {
                            screen = Screen.Preview(u.file)
                            vm.resetUi()
                        }
                        is Ui.Error -> {
                            scope.launch { snack.showSnackbar(u.msg) }
                            vm.resetUi()
                        }
                        else -> Unit
                    }
                }

                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        when (val sc = screen) {
                            Screen.Form -> FormWizard(
                                vm = vm,
                                onCamera = { screen = Screen.Camera },
                                onSign = { screen = Screen.Sign },
                                onExport = { vm.export() }
                            )
                            Screen.Camera -> CameraScreen(
                                onCaptured = {
                                    vm.saveCard(it)
                                    screen = Screen.Form
                                },
                                onClose = { screen = Screen.Form }
                            )
                            Screen.Sign -> SignaturePad(
                                onDone = {
                                    vm.saveSignature(it)
                                    screen = Screen.Form
                                },
                                onCancel = { screen = Screen.Form }
                            )
                            is Screen.Preview -> PreviewScreen(sc.file) { screen = Screen.Form }
                        }
                        SnackbarHost(
                            snack,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        )
                    }
                }

                if (ui is Ui.Busy) {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text("กำลังสร้างเอกสาร") },
                        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    )
                }
            }
        }
    }
}

sealed interface Screen {
    data object Form : Screen
    data object Camera : Screen
    data object Sign : Screen
    data class Preview(val file: File) : Screen
}
