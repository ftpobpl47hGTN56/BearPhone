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
import com.bearphone.app.sms.SmsRole
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.data.CallLogRepository
import com.bearphone.app.data.Contact
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.ui.ContactsScreen
import com.bearphone.app.ui.MessagesScreen
import com.bearphone.app.data.SmsRepository
import com.bearphone.app.data.SmsThread
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
    private var isDefaultSms by mutableStateOf(false)
    /** Текст, пришедший вместе с sms:-ссылкой ("sms:+7912...?body=Привет") */
    private var smsDraft by mutableStateOf<String?>(null)
    private var missingPerms by mutableStateOf(false)
    private var contacts by mutableStateOf<List<Contact>>(emptyList())
    private var recents by mutableStateOf<List<CallLogEntry>>(emptyList())
    /** Увеличиваем, чтобы перечитать журнал/контакты (после звонка, выдачи прав и т.п.) */
    private var reloadTick by mutableIntStateOf(0)
    private var smsThreads by mutableStateOf<List<SmsThread>>(emptyList())
    /** Номер открытого SMS-диалога (null — список диалогов) */
    private var openSms by mutableStateOf<String?>(null)
    /** Меняется при любом изменении базы SMS — экраны перечитывают данные */
    private var smsTick by mutableIntStateOf(0)

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { smsTick++ }
    }

    private val permissions: Array<String> = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
        }

    private val smsRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
            smsTick++
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

                LaunchedEffect(reloadTick, smsTick) {
                    val t = SmsRepository.loadThreads(this@MainActivity)
                    if (t != smsThreads) smsThreads = t
                }

                LaunchedEffect(reloadTick) {
                    // Присваиваем только если данные реально изменились — иначе список
                    // перерисовывается целиком при каждом возврате в приложение
                    val c = ContactsRepository.loadAll(this@MainActivity)
                    if (c != contacts) contacts = c
                    val r = CallLogRepository.load(this@MainActivity)
                    if (r != recents) recents = r
                }

                Scaffold(
                    bottomBar = {
                        val st = com.bearphone.app.ui.theme.LocalBearStyle.current
                        NavigationBar(containerColor = st.tabColor, contentColor = st.onTabColor) {
                            NavTab(TAB_KEYPAD, Icons.Filled.Dialpad, "Набор")
                            NavTab(TAB_RECENTS, Icons.Filled.History, "Вызовы")
                            NavTab(TAB_MESSAGES, Icons.AutoMirrored.Filled.Message, "SMS", smsThreads.sumOf { it.unread })
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
                                onMessage = { openSms = it; tab = TAB_MESSAGES },
                                onChanged = { reloadTick++ },
                            )
                            TAB_MESSAGES -> MessagesScreen(
                                threads = smsThreads,
                                contacts = contacts,
                                canRead = SmsRepository.canRead(this@MainActivity),
                                reloadTick = smsTick,
                                openAddress = openSms,
                                onOpen = { openSms = it },
                                onClose = { openSms = null; smsDraft = null },
                                draft = smsDraft,
                                onCall = ::placeCall,
                                onRequestPermissions = { permissionLauncher.launch(permissions) },
                                onChanged = { smsTick++ },
                            )
                            TAB_CONTACTS -> ContactsScreen(
                                contacts = contacts,
                                hasPermission = granted(Manifest.permission.READ_CONTACTS),
                                onCall = ::placeCall,
                            )
                            TAB_SETTINGS -> SettingsScreen(
                                isDefaultDialer = isDefault,
                                isDefaultSms = isDefaultSms,
                                onRequestSms = { smsRoleLauncher.launch(SmsRole.requestIntent(this@MainActivity)) },
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
    private fun androidx.compose.foundation.layout.RowScope.NavTab(
        id: Int, icon: ImageVector, label: String, badge: Int = 0,
    ) {
        NavigationBarItem(
            selected = tab == id,
            onClick = {
                // Повторный тап по "SMS" возвращает к списку диалогов
                if (id == TAB_MESSAGES && tab == TAB_MESSAGES) openSms = null
                tab = id
            },
            icon = {
                if (badge > 0) BadgedBox(badge = { Badge { Text(if (badge > 99) "99+" else badge.toString()) } }) {
                    Icon(icon, null)
                } else Icon(icon, null)
            },
            label = { Text(label, maxLines = 1) },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                unselectedIconColor = com.bearphone.app.ui.theme.LocalBearStyle.current.onTabColor.copy(alpha = 0.7f),
                unselectedTextColor = com.bearphone.app.ui.theme.LocalBearStyle.current.onTabColor.copy(alpha = 0.7f),
            ),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        try {
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        } catch (e: SecurityException) { }
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(smsObserver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Звонков нет, а уведомление "Идёт разговор" осталось (сервис убили) — снимаем
        if (com.bearphone.app.call.CallManager.calls.value.isEmpty()) {
            com.bearphone.app.call.CallNotification.cancelCall(this)
        }
        refreshStatus() // внутри reloadTick++ — после звонка журнал обновится
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

        // sms:/smsto: из других приложений и тап по уведомлению SMS — открываем диалог
        val smsAddress = intent.getStringExtra(EXTRA_SMS_ADDRESS)
            ?: data?.takeIf { it.scheme in setOf("sms", "smsto", "mms", "mmsto") }
                ?.schemeSpecificPart?.substringBefore('?')?.let(Uri::decode)?.substringBefore(',')
        if (!smsAddress.isNullOrBlank()) {
            openSms = smsAddress
            tab = TAB_MESSAGES
            smsDraft = intent.getStringExtra("sms_body")
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: data?.query?.substringAfter("body=", "")?.takeIf { it.isNotEmpty() }?.let(Uri::decode)
            intent.removeExtra(EXTRA_SMS_ADDRESS)
        }
    }

    private fun placeCall(n: String) {
        if (n.isBlank()) return
        Dialer.call(this, n)
        number = ""
    }

    private fun refreshStatus() {
        isDefault = Dialer.isDefault(this)
        isDefaultSms = SmsRole.isHeld(this)
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
        const val EXTRA_SMS_ADDRESS = "bearphone.sms_address"
        const val TAB_KEYPAD = 0
        const val TAB_RECENTS = 1
        const val TAB_MESSAGES = 2
        const val TAB_CONTACTS = 3
        const val TAB_SETTINGS = 4
    }
}
