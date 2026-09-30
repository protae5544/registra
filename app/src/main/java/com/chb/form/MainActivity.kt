package com.chb.form
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.ui.*
import com.chb.form.ui.theme.ChbTheme
import com.chb.form.vm.FormViewModel
import com.chb.form.vm.Ui
import java.io.File
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
class MainActivity : ComponentActivity() {
private val vm: FormViewModel by viewModels()
override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
enableEdgeToEdge()
setContent { ChbTheme { val ui by vm.ui.collectAsStateWithLifecycle()
val snack = remember { SnackbarHostState() }
var screen by remember { mutableStateOf<Screen>(Screen.Form) }
LaunchedEffect(Unit) { vm.toast.collect { snack.showSnackbar(it) } }
LaunchedEffect(ui) { when (val u = ui) { is Ui.Done -> { screen = Screen.Preview(u.file);
vm.resetUi() }
is Ui.Error -> { snack.showSnackbar(u.msg);
vm.resetUi() }
else -> Unit
} }
Surface(Modifier.fillMaxSize()) { Scaffold(snackbarHost = { SnackbarHost(snack) }) { paddingValues -> when (val sc = screen) { Screen.Form -> FormWizard( vm = vm, onCamera = { screen = Screen.Camera
}, onSign = { screen = Screen.Sign
}, onExport = { vm.export() } )
Screen.Camera -> CameraScreen( onCaptured = { vm.saveCard(it);
screen = Screen.Form
}, onClose = { screen = Screen.Form
} )
Screen.Sign -> SignaturePad( onDone = { vm.saveSignature(it);
screen = Screen.Form
}, onCancel = { screen = Screen.Form
} )
is Screen.Preview -> PreviewScreen(sc.file) { screen = Screen.Form
} } } }
if (ui is Ui.Busy) { AlertDialog( onDismissRequest = {}, confirmButton = {}, title = { Text("กำลังสร้างเอกสาร") }, text = { LinearProgressIndicator(Modifier.fillMaxSize().height(4.dp)) } ) } } } } }
sealed interface Screen { data object Form : Screen
data object Camera : Screen
data object Sign : Screen
data class Preview(val file: File) : Screen
}
