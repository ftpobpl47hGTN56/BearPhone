package com.bearphone.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.bearphone.app.call.Dialer
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.data.CallLogRepository
import com.bearphone.app.data.Contact
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.ui.ContactsScreen
import com.bearphone.app.ui.KeypadScreen
import com.bearphone.app.ui.RecentsScreen
import com.bearphone.app.ui.SettingsScreen
import com.bearphone.app.ui.theme.BearPhoneTheme
import kotlinx.coroutines.launch

/**
 * Единственная "обычная" Activity. Все вкладки — composable-функции, не отдельные Activity.
 */
class MainActivity : ComponentActivity() {

    // ---- состояние, которое переживает смену вкладок ----
    private var tab by mutableIntStateOf(TAB_KEYPAD)
    private var number by mutableStateOf("")
    private var isDefault by mutableStateOf(false)
    private var missingPerms by mutableStateOf(false)
    private var contacts by mutableStateOf<List<Contact>>(emptyList())
    private var recents by mutableStateOf<List<CallLogEntry>>(emptyList())
    /** Увеличиваем, чтобы перечитать журнал/контакты (после звонка, выдачи прав и т.п.) */
    private var reloadTick by mutableIntStateOf(0)

    private val permissions: Array<String> = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
            // Стали звонилкой — сразу попросим недостающие разрешения
            if (isDefault && missingPerms) permissionLauncher.launch(permissions)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        refreshStatus()

        if (savedInstanceState == null) {
            if (!isDefault) roleLauncher.launch(Dialer.requestDefaultIntent(this))
            else if (missingPerms) permissionLauncher.launch(permissions)
        }

        setContent {
            BearPhoneTheme {
                val scope = rememberCoroutineScope()

                LaunchedEffect(reloadTick) {
                    contacts = ContactsRepository.loadAll(this@MainActivity)
                    recents = CallLogRepository.load(this@MainActivity)
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavTab(TAB_KEYPAD, Icons.Filled.Dialpad, "Набор")
                            NavTab(TAB_RECENTS, Icons.Filled.History, "Недавние")
                            NavTab(TAB_CONTACTS, Icons.Filled.Person, "Контакты")
                            NavTab(TAB_SETTINGS, Icons.Filled.Settings, "Настройки")
                        }
                    }
                ) { padding ->
                    val m = Modifier.padding(padding)
                    androidx.compose.foundation.layout.Box(m) {
                        when (tab) {
                            TAB_KEYPAD -> KeypadScreen(
                                number = number,
                                onNumberChange = { number = it },
                                contacts = contacts,
                                onCall = ::placeCall,
                            )
                            TAB_RECENTS -> RecentsScreen(
                                entries = recents,
                                hasPermission = granted(Manifest.permission.READ_CALL_LOG),
                                onCall = ::placeCall,
                                onEditBeforeCall = { number = it; tab = TAB_KEYPAD },
                                onDelete = { e ->
                                    scope.launch {
                                        CallLogRepository.delete(this@MainActivity, e.id)
                                        reloadTick++
                                    }
                                },
                            )
                            TAB_CONTACTS -> ContactsScreen(
                                contacts = contacts,
                                hasPermission = granted(Manifest.permission.READ_CONTACTS),
                                onCall = ::placeCall,
                            )
                            TAB_SETTINGS -> SettingsScreen(
                                isDefaultDialer = isDefault,
                                missingPermissions = missingPerms,
                                onRequestDefault = { roleLauncher.launch(Dialer.requestDefaultIntent(this@MainActivity)) },
                                onRequestPermissions = { permissionLauncher.launch(permissions) },
                                onOpenAppSettings = ::openAppSettings,
                            )
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun androidx.compose.foundation.layout.RowScope.NavTab(id: Int, icon: ImageVector, label: String) {
        NavigationBarItem(
            selected = tab == id,
            onClick = { tab = id },
            icon = { Icon(icon, null) },
            label = { Text(label) },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        reloadTick++ // после звонка журнал обновится
    }

    /** tel:-ссылки и ACTION_DIAL открывают набор с номером; уведомление пропущенного — журнал. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data: Uri? = intent.data
        if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) &&
            data?.scheme == "tel"
        ) {
            number = data.schemeSpecificPart.orEmpty()
            tab = TAB_KEYPAD
        } else if (intent.action == Intent.ACTION_DIAL) {
            tab = TAB_KEYPAD
        }
        intent.getIntExtra(EXTRA_TAB, -1).takeIf { it >= 0 }?.let { tab = it }
    }

    private fun placeCall(n: String) {
        if (n.isBlank()) return
        Dialer.call(this, n)
        number = ""
    }

    private fun refreshStatus() {
        isDefault = Dialer.isDefault(this)
        missingPerms = permissions.any { !granted(it) }
        reloadTick++
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    companion object {
        const val EXTRA_TAB = "bearphone.tab"
        const val TAB_KEYPAD = 0
        const val TAB_RECENTS = 1
        const val TAB_CONTACTS = 2
        const val TAB_SETTINGS = 3
    }
}
