package com.knockturn.personalexecutive
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()
    private val x: ExecVm by viewModels()
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", it).apply() }
        setContent { PETheme { Root(vm, x) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Root(vm: AppVm, x: ExecVm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var onb by remember { mutableStateOf(!ctx.getSharedPreferences("ui", 0).getBoolean("onb", false)) }
    val snack = remember { SnackbarHostState() }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { perm.launch(Manifest.permission.POST_NOTIFICATIONS) }
    LaunchedEffect(vm.msg) { vm.msg?.let { snack.showSnackbar(it); vm.msg = null } }
    val p = vm.profile
    Scaffold(snackbarHost = { SnackbarHost(snack) },
        topBar = { if (p != null) TopAppBar(
            title = { Text(if (p.role == "admin") "Admin · ${p.name}" else "Hello, ${p.name}") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple, titleContentColor = Color.White, actionIconContentColor = Color.White),
            actions = { IconButton(onClick = { vm.logout() }) { Icon(Icons.Default.Logout, "Sign out") } }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when { onb -> Onboarding { onb = false }; p == null -> AuthScreen(vm); p.role == "admin" -> AdminMain(vm, x); else -> EmployeeMain(vm, x) }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Purple)
        }
    }
}

@Composable fun AuthScreen(vm: AppVm) {
    var isSignUp by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Surface(color = Purple, shape = MaterialTheme.shapes.large, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("PE", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Personal Executive", style = MaterialTheme.typography.headlineSmall, color = Purple)
        Text(if (isSignUp) "Create your account" else "Sign in to continue", color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        if (!isSignUp) {
            LoginForm(vm)
        } else {
            SignUpForm(vm)
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up", color = Purple)
        }
    }
}

@Composable fun LoginForm(vm: AppVm) {
    var e by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = e, onValueChange = { e = it }, label = { Text("Email") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.login(e, pw) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !vm.busy && e.isNotBlank() && pw.isNotBlank()
        ) {
            Text("Sign In")
        }
    }
}

@Composable fun SignUpForm(vm: AppVm) {
    var name by remember { mutableStateOf("") }
    var e by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("employee") }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = e, onValueChange = { e = it }, label = { Text("Email") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(14.dp))
        Text("Account Role:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("employee" to "Employee", "admin" to "Admin").forEach { (rVal, rLabel) ->
                FilterChip(
                    selected = role == rVal,
                    onClick = { role = rVal },
                    label = { Text(rLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Purple,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val valid = !vm.busy && name.isNotBlank() && e.isNotBlank() && pw.length >= 6
        Button(
            onClick = { vm.signUp(name, e, pw, role) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = valid
        ) {
            Text("Create Account")
        }
    }
}

@Composable fun Tabs(titles: List<String>, content: @Composable (Int) -> Unit) {
    var t by remember { mutableIntStateOf(0) }
    Column {
        TabRow(t, containerColor = Color.White, contentColor = Purple) { titles.forEachIndexed { i, s -> Tab(t == i, { t = i }, text = { Text(s) }) } }
        content(t)
    }
}

@Composable fun TextEntry(label: String, button: String, onSend: (String) -> Unit) {
    var s by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(s, { s = it }, label = { Text(label) }, minLines = 5, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button({ onSend(s); s = "" }, enabled = s.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(button) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AdminQuestion(vm: AppVm) {
            var text by remember { mutableStateOf("") }; var urgent by remember { mutableStateOf(false) }
            var target by remember { mutableStateOf<Profile?>(null) }; var open by remember { mutableStateOf(false) }
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Question") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(open, { open = it }) {
                    OutlinedTextField(target?.name ?: "Everyone", {}, readOnly = true, label = { Text("Send to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text("Everyone") }, onClick = { target = null; open = false })
                        vm.employees.forEach { e -> DropdownMenuItem(text = { Text(e.name) }, onClick = { target = e; open = false }) }
                    }
                }
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(urgent, { urgent = it }, colors = SwitchDefaults.colors(checkedTrackColor = Purple)); Spacer(Modifier.width(8.dp))
                    Text(if (urgent) "Urgent (alarm, rings on silent)" else "Normal priority")
                }
                Button({ vm.create(text, urgent, target?.id); text = "" }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Send question") }
            }
        }
