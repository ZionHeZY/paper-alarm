package tech.hezy.alarm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.hezy.alarm.R
import tech.hezy.alarm.data.alreadyImported
import tech.hezy.alarm.data.resolveNextLunar
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.AlarmTone
import tech.hezy.alarm.domain.LunarBirthday
import tech.hezy.alarm.domain.lunarDateText
import tech.hezy.alarm.domain.nextSolarDate
import tech.hezy.alarm.domain.lunarDayName
import tech.hezy.alarm.domain.lunarMonthName
import tech.hezy.glassalarmslider.LocalGlassBackdrop
import tech.hezy.glassalarmslider.glassBackdropSource
import tech.hezy.glassalarmslider.rememberGlassBackdrop
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AlarmHome(state: AlarmListState, dispatch: (AlarmIntent) -> Unit) {
    val palette = iosPalette(true)
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    var cards by rememberSaveable { mutableStateOf(true) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val morph = remember { Animatable(if (cards) 0f else 1f) }
    val scope = rememberCoroutineScope()
    fun toggleLayout() {
        cards = !cards
        scope.launch { morph.animateTo(if (cards) 0f else 1f, spring(dampingRatio = 0.78f, stiffness = 280f)) }
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var afterContacts by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAlarmGate by remember { mutableStateOf(false) }
    var holdAlarmGate by rememberSaveable { mutableStateOf(false) }
    var showContactsGate by remember { mutableStateOf(false) }
    var contactsGoSettings by remember { mutableStateOf(false) }
    var accessTick by remember { mutableIntStateOf(0) }
    val gate = remember { AlarmGate(context) }
    val notifyLabel = stringResource(R.string.perm_notify)
    val fullLabel = stringResource(R.string.perm_full_screen)
    val batteryLabel = stringResource(R.string.perm_battery)
    val autostartLabel = stringResource(R.string.perm_autostart)
    val missing = remember(accessTick, notifyLabel, fullLabel, batteryLabel, autostartLabel) {
        gate.missingLabels(notifyLabel, fullLabel, batteryLabel, autostartLabel)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(context)
                accessTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(missing, holdAlarmGate, showContactsGate) {
        showAlarmGate = missing.isNotEmpty() && !holdAlarmGate && !showContactsGate
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        accessTick++
    }
    val contactPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val ok = grants[Manifest.permission.READ_CONTACTS] == true &&
            grants[Manifest.permission.WRITE_CONTACTS] == true
        if (ok) {
            showContactsGate = false
            val next = afterContacts
            afterContacts = null
            next?.invoke()
        } else {
            contactsGoSettings = gate.permanentlyDenied(Manifest.permission.READ_CONTACTS) ||
                gate.permanentlyDenied(Manifest.permission.WRITE_CONTACTS)
            showContactsGate = true
        }
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) dispatch(AlarmIntent.ExportPicked(uri.toString()))
    }
    fun openAlarmSetting() {
        when {
            !gate.notificationsGranted() -> {
                if (gate.permanentlyDenied(Manifest.permission.POST_NOTIFICATIONS)) gate.openNotificationSettings()
                else {
                    gate.markAsked(Manifest.permission.POST_NOTIFICATIONS)
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            !gate.fullScreenGranted() -> gate.openFullScreenSettings()
            !gate.batteryGranted() -> gate.openBatterySettings()
            else -> {
                gate.openAutostart()
                accessTick++
            }
        }
    }
    fun ensureContacts(block: () -> Unit) {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (read && write) block()
        else {
            afterContacts = block
            contactsGoSettings = gate.permanentlyDenied(Manifest.permission.READ_CONTACTS) ||
                gate.permanentlyDenied(Manifest.permission.WRITE_CONTACTS)
            showContactsGate = true
        }
    }
    val backdrop = rememberGlassBackdrop()
    var sheet by remember { mutableFloatStateOf(0f) }
    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        IosMetrics.screenMargin + 64.dp
    Box(Modifier.fillMaxSize().background(InkPaper)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.05f * sheet
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(IosMetrics.groupedCorner * sheet)
                    clip = true
                }
                .background(InkPaper),
        ) {
            val today = LocalDate.now()
            val birthdays = state.alarms.filter { it.birthday != null }.sortedBy { alarm ->
                val birthday = alarm.birthday ?: return@sortedBy LocalDate.MAX
                if (birthday.solar) nextSolarDate(today, birthday.month, birthday.day) ?: LocalDate.MAX
                else resolveNextLunar(today, birthday.month, birthday.day)?.date ?: LocalDate.MAX
            }
            val others = state.alarms.filter { it.birthday == null }
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop, InkPaper)) {
            Column(Modifier.fillMaxSize().padding(top = headerHeight)) {
                if (!canOverlay) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = IosMetrics.screenMargin, vertical = 8.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.grouped)
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.allow_overlay),
                                color = palette.secondary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.allow),
                                color = palette.blue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                }
                if (tab == 0) {
                    BirthdayPanel(
                        Modifier.weight(1f),
                        birthdays,
                        morph.value,
                        palette,
                        is24,
                        state.editing,
                        state.armedDeleteId,
                        onOpen = { dispatch(AlarmIntent.Open(it)) },
                        onToggle = { dispatch(AlarmIntent.ToggleEnabled(it)) },
                        onArm = { dispatch(AlarmIntent.ArmDelete(it)) },
                        onDelete = { dispatch(AlarmIntent.Delete(it)) },
                        onAdd = { dispatch(AlarmIntent.OpenBirthday) },
                        onContacts = { ensureContacts { dispatch(AlarmIntent.ShowImports) } },
                    )
                } else {
                    AlarmPage(
                        Modifier.weight(1f),
                        others,
                        palette,
                        is24,
                        state.editing,
                        state.armedDeleteId,
                        onOpen = { dispatch(AlarmIntent.Open(it)) },
                        onToggle = { dispatch(AlarmIntent.ToggleEnabled(it)) },
                        onArm = { dispatch(AlarmIntent.ArmDelete(it)) },
                        onDelete = { dispatch(AlarmIntent.Delete(it)) },
                    )
                }
                InkTabs(tab) { tab = it }
            }
            }
            Box(Modifier.fillMaxWidth().height(headerHeight).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            })
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = IosMetrics.screenMargin)) {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    AlarmToolbar(state, cards, tab == 0, morph.value, { toggleLayout() }, { dispatch(AlarmIntent.Open(null)) }, dispatch)
                }
            }
        }
        IosEditorSheet(state.editor, onProgress = { sheet = it }, onIntent = dispatch) { draft, editorDispatch ->
            IosEditorStack(draft.page, onBack = { editorDispatch(AlarmIntent.OpenPage(EditorPage.Form)) }) { page ->
                when (page) {
                    EditorPage.Form -> EditorForm(
                        draft, palette, is24, editorDispatch,
                        onWriteContacts = { ensureContacts { editorDispatch(AlarmIntent.OpenPage(EditorPage.Export)) } },
                    )
                    EditorPage.Repeat -> RepeatPage(draft, palette, editorDispatch)
                    EditorPage.Label -> LabelPage(draft, palette, editorDispatch)
                    EditorPage.Sound -> SoundPage(draft, palette, editorDispatch)
                    EditorPage.Snooze -> SnoozePage(draft, palette, editorDispatch)
                    EditorPage.Export -> ExportPage(draft, state, palette, editorDispatch) {
                        ensureContacts { pickContact.launch(null) }
                    }
                }
            }
        }
        if (state.imports != null) ImportSheet(state, palette, cards, morph.value, { toggleLayout() }, dispatch)
        if (showContactsGate) {
            InkDialog(
                title = stringResource(R.string.contacts_perm_title),
                body = stringResource(if (contactsGoSettings) R.string.contacts_perm_settings_body else R.string.contacts_perm_body),
                confirm = stringResource(if (contactsGoSettings) R.string.perm_settings else R.string.allow),
                dismiss = stringResource(R.string.perm_later),
                onConfirm = {
                    if (contactsGoSettings) gate.openAppSettings()
                    else {
                        gate.markAsked(Manifest.permission.READ_CONTACTS)
                        gate.markAsked(Manifest.permission.WRITE_CONTACTS)
                        contactPermission.launch(
                            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                        )
                    }
                },
                onDismiss = {
                    showContactsGate = false
                    afterContacts = null
                },
            )
        } else if (showAlarmGate && missing.isNotEmpty()) {
            InkDialog(
                title = stringResource(R.string.perm_title),
                body = stringResource(R.string.perm_body, missing.joinToString(" \u00b7 ")),
                confirm = stringResource(R.string.perm_open),
                dismiss = stringResource(R.string.perm_later),
                onConfirm = { openAlarmSetting() },
                onDismiss = {
                    holdAlarmGate = true
                    showAlarmGate = false
                },
            )
        }
    }
}
@Composable
private fun InkDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            val shape = RoundedCornerShape(22.dp)
            Box {
                Box(Modifier.matchParentSize().offset(4.dp, 4.dp).clip(shape).background(Ink))
                Column(
                    Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .clip(shape)
                        .border(2.dp, Ink, shape)
                        .background(InkPaper)
                        .padding(20.dp),
                ) {
                    Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(body, color = Color(0xFF5C584F), fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkButton(dismiss, Modifier.weight(1f), onClick = onDismiss)
                        InkButton(confirm, Modifier.weight(1f), filled = true, onClick = onConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmToolbar(
    state: AlarmListState,
    cards: Boolean,
    birthdayPage: Boolean,
    morph: Float,
    onToggleLayout: () -> Unit,
    onAddAlarm: () -> Unit,
    dispatch: (AlarmIntent) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        if (birthdayPage) {
            LayoutMorphButton(
                morph,
                stringResource(if (cards) R.string.view_list else R.string.view_cards),
                onToggleLayout,
                Modifier.align(Alignment.CenterStart).padding(start = IosMetrics.screenMargin),
            )
        }
        Text(
            stringResource(if (birthdayPage) R.string.birthdays else R.string.other_alarms),
            color = Ink,
            fontSize = IosMetrics.body,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = IosMetrics.screenMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IosMetrics.labelGap),
        ) {
            if (!birthdayPage && !state.editing) {
                InkIconButton("plus", stringResource(R.string.add_alarm), onAddAlarm)
            }
            if (state.editing) {
                InkIconButton("check", stringResource(R.string.done), { dispatch(AlarmIntent.ToggleEdit) }, filled = true)
            } else {
                InkButton(stringResource(R.string.edit), Modifier.height(40.dp)) { dispatch(AlarmIntent.ToggleEdit) }
            }
        }
    }
}

@Composable
private fun TrashButton(palette: IosPalette, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(palette.destructive)
            .semantics { contentDescription = label }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val stroke = 1.5.dp.toPx()
            val w = size.width
            val h = size.height
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            drawRoundRect(Color.White, Offset(w * .27f, h * .27f), Size(w * .46f, h * .62f),
                CornerRadius(w * .06f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawLine(Color.White, Offset(w * .18f, h * .25f), Offset(w * .82f, h * .25f), stroke, cap)
            drawRoundRect(Color.White, Offset(w * .39f, h * .1f), Size(w * .22f, h * .14f),
                CornerRadius(w * .04f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawLine(Color.White, Offset(w * .43f, h * .39f), Offset(w * .43f, h * .76f), stroke, cap)
            drawLine(Color.White, Offset(w * .57f, h * .39f), Offset(w * .57f, h * .76f), stroke, cap)
        }
    }
}

@Composable
private fun Chevron(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(8.dp, 14.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height / 2), androidx.compose.ui.geometry.Offset(0f, size.height), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun ShapeMorph(
    progress: Float,
    listHeight: Dp,
    card: @Composable () -> Unit,
    list: @Composable () -> Unit,
) {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.01f) {
        card()
        return
    }
    if (p > 0.99f) {
        list()
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = lerp(252.dp, maxWidth, p)
        val height = lerp(368.dp, listHeight, p)
        val radius = lerp(22.dp, 0.dp, p)
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .width(width)
                    .height(height)
                    .clip(RoundedCornerShape(radius))
                    .background(InkCard.copy(alpha = (1f - p).coerceIn(0f, 1f))),
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - p * 1.45f).coerceIn(0f, 1f) }) { card() }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((p - 0.28f) * 1.5f).coerceIn(0f, 1f) }) { list() }
            }
        }
    }
}

@Composable
private fun Separator(palette: IosPalette, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(IosMetrics.hairline).background(palette.separator))
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    palette: IosPalette,
    is24: Boolean,
    editing: Boolean,
    armed: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onArm: () -> Unit,
    onDelete: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 70.dp.toPx() }
    var expanded by remember(alarm.id) { mutableStateOf(false) }
    var dragging by remember(alarm.id) { mutableStateOf(false) }
    var dragOffset by remember(alarm.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(armed) { expanded = armed }
    val settled by animateFloatAsState(
        if (dragging) dragOffset else if (expanded) -threshold else 0f,
        if (dragging) snap() else spring(dampingRatio = 0.9f, stiffness = 650f),
        label = "delete reveal",
    )
    val offset = if (dragging) dragOffset else settled
    val color = if (alarm.enabled) palette.label else palette.disabledTime
    val deleteLabel = stringResource(if (alarm.birthday != null) R.string.delete_birthday else R.string.delete_alarm)
    Box(Modifier.fillMaxWidth().padding(horizontal = IosMetrics.screenMargin).clipToBounds()) {
        if (offset < -1f) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                TrashButton(palette, deleteLabel, onDelete)
            }
        }
        Column(
            Modifier.offset { IntOffset(offset.roundToInt(), 0) }.background(palette.background)
                .pointerInput(threshold) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffset = settled; dragging = true },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            dragOffset = (dragOffset + drag).coerceIn(-threshold, 0f)
                        },
                        onDragEnd = { expanded = dragOffset < -threshold / 2; dragging = false },
                        onDragCancel = { dragging = false },
                    )
                }
                .clickable { if (expanded) expanded = false else onOpen() },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 101.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    Box(
                        Modifier.width(40.dp).height(44.dp)
                            .semantics { contentDescription = deleteLabel }
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                if (armed) expanded = !expanded else onArm()
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                            drawCircle(palette.destructive)
                            drawLine(Color.White, Offset(size.width * .3f, size.height / 2),
                                Offset(size.width * .7f, size.height / 2), 1.8.dp.toPx(),
                                androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val hour = if (is24) alarm.hour else (alarm.hour % 12).let { if (it == 0) 12 else it }
                        val hours = if (is24) hour.toString().padStart(2, '0') else hour.toString()
                        Text(
                            "$hours:${alarm.minute.toString().padStart(2, '0')}",
                            color = color,
                            fontSize = 54.sp,
                            lineHeight = 60.sp,
                            fontFamily = IosFontFamily,
                            fontWeight = FontWeight(350),
                            letterSpacing = (-1.5).sp,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f),
                            ),
                        )
                        if (!is24) {
                            Text(
                                stringResource(if (alarm.hour < 12) R.string.am else R.string.pm),
                                color = color,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(start = 3.dp, bottom = 6.dp),
                            )
                        }
                    }
                    Text(
                        subtitle(alarm),
                        color = color,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    )
                }
                if (editing) {
                    Chevron(palette.separator)
                } else {
                    val description = stringResource(R.string.alarm_title) + " ${alarm.hour}:${alarm.minute}"
                    Box(Modifier.semantics { contentDescription = description }) {
                        IosSwitch(alarm.enabled, { onToggle() }, palette)
                    }
                }
            }
            Separator(palette)
        }
    }
}

@Composable
private fun EditorBar(title: String, dispatch: (AlarmIntent) -> Unit, form: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = IosMetrics.screenMargin)) {
        InkIconButton(
            if (form) "close" else "back",
            stringResource(if (form) R.string.cancel else R.string.back),
            { dispatch(if (form) AlarmIntent.CloseEditor else AlarmIntent.OpenPage(EditorPage.Form)) },
            Modifier.align(Alignment.CenterStart),
        )
        Text(
            title,
            color = Ink,
            fontSize = IosMetrics.body,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
        if (form) {
            InkIconButton(
                "check",
                stringResource(R.string.save),
                { dispatch(AlarmIntent.Save) },
                Modifier.align(Alignment.CenterEnd),
                filled = true,
            )
        }
    }
}

@Composable
private fun BirthdayPanel(
    modifier: Modifier = Modifier,
    birthdays: List<Alarm>,
    morph: Float,
    palette: IosPalette,
    is24: Boolean,
    editing: Boolean,
    armedId: Long?,
    onOpen: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onArm: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
    onContacts: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (birthdays.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                InkEmpty(R.drawable.empty_birthday, stringResource(R.string.no_birthdays))
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val deckHeight = maxHeight
            ShapeMorph(morph, deckHeight, card = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BirthdayDeck(birthdays, is24, palette, onOpen, onToggle)
                }
            }) {
                Column(Modifier.fillMaxWidth().height(deckHeight).verticalScroll(rememberScrollState())) {
                    birthdays.forEach { alarm ->
                        AlarmRow(
                            alarm, palette, is24, editing, armedId == alarm.id,
                            onOpen = { onOpen(alarm.id) },
                            onToggle = { onToggle(alarm.id) },
                            onArm = { onArm(alarm.id) },
                            onDelete = { onDelete(alarm.id) },
                        )
                    }
                }
            }
        }
        BirthdayActions(onAdd, onContacts)
    }
}

@Composable
private fun BirthdayActions(onAdd: () -> Unit, onContacts: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InkButton(stringResource(R.string.add_birthday), Modifier.weight(1f).height(48.dp), onClick = onAdd)
        InkButton(stringResource(R.string.contacts_button), Modifier.weight(1f).height(48.dp), onClick = onContacts)
    }
}

@Composable
private fun InkTabs(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(InkPaper).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InkTab(stringResource(R.string.birthdays), selected == 0, Modifier.weight(1f)) { onSelect(0) }
            InkTab(stringResource(R.string.other_alarms), selected == 1, Modifier.weight(1f)) { onSelect(1) }
        }
    }
}

@Composable
private fun InkTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    InkButton(label, modifier.height(48.dp), filled = selected, onClick = onClick)
}

@Composable
private fun InkButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    val shift = if (interaction.collectIsPressedAsState().value) 3.dp else 0.dp
    Box(modifier.heightIn(min = 52.dp)) {
        Box(Modifier.matchParentSize().offset(x = 3.dp, y = 3.dp).clip(shape).background(Ink))
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 3.dp, bottom = 3.dp)
                .offset(x = shift, y = shift)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .border(2.dp, Ink, shape)
                    .background(if (filled) InkYellow else InkCard),
            )
            Text(
                label,
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                    lineHeight = 22.sp,
                ),
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

@Composable
private fun InkIconButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    val shift = if (interaction.collectIsPressedAsState().value) 3.dp else 0.dp
    Box(modifier.size(44.dp).semantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize().offset(x = 3.dp, y = 3.dp).clip(shape).background(Ink))
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = 3.dp, bottom = 3.dp)
                .offset(x = shift, y = shift)
                .clip(shape)
                .border(2.dp, Ink, shape)
                .background(if (filled) InkYellow else InkCard)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val stroke = 2.2.dp.toPx()
                val cap = androidx.compose.ui.graphics.StrokeCap.Round
                fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                    drawLine(Ink, Offset(w * x1, w * y1), Offset(w * x2, w * y2), stroke, cap)
                when (icon) {
                    "plus" -> {
                        line(0.12f, 0.5f, 0.88f, 0.5f)
                        line(0.5f, 0.12f, 0.5f, 0.88f)
                    }
                    "close" -> {
                        line(0.2f, 0.2f, 0.8f, 0.8f)
                        line(0.8f, 0.2f, 0.2f, 0.8f)
                    }
                    "check" -> {
                        line(0.12f, 0.54f, 0.4f, 0.8f)
                        line(0.4f, 0.8f, 0.88f, 0.16f)
                    }
                    "back" -> {
                        line(0.68f, 0.14f, 0.28f, 0.5f)
                        line(0.28f, 0.5f, 0.68f, 0.86f)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmPage(
    modifier: Modifier = Modifier,
    alarms: List<Alarm>,
    palette: IosPalette,
    is24: Boolean,
    editing: Boolean,
    armedId: Long?,
    onOpen: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onArm: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (alarms.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InkEmpty(R.drawable.empty_alarm, stringResource(R.string.no_alarms))
        }
    } else {
        LazyColumn(modifier) {
            items(alarms, key = { "alarm-${it.id}" }) { alarm ->
                AlarmRow(
                    alarm, palette, is24, editing, armedId == alarm.id,
                    onOpen = { onOpen(alarm.id) },
                    onToggle = { onToggle(alarm.id) },
                    onArm = { onArm(alarm.id) },
                    onDelete = { onDelete(alarm.id) },
                )
            }
        }
    }
}

@Composable
private fun BirthdayEditor(draft: EditorDraft, palette: IosPalette, is24: Boolean, dispatch: (AlarmIntent) -> Unit, onWriteContacts: () -> Unit) {
    val birthday = draft.birthday ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(if (draft.isNew) R.string.add_birthday else R.string.edit_birthday), dispatch, form = true)
        Spacer(Modifier.height(8.dp))
        BirthdayDateBlock(draft, palette, dispatch)
        Text(
            stringResource(if (birthday.solar) R.string.solar_birthday_help else R.string.lunar_birthday_help),
            color = palette.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.reminder),
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, bottom = 4.dp),
        )
        TimeWheels(draft, palette, is24, dispatch)
        Spacer(Modifier.height(18.dp))
        GroupedCard(palette) {
            GroupedRow(
                stringResource(R.string.sound),
                toneName(draft.tone),
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Sound)) },
            )
            GroupedRow(
                stringResource(R.string.snooze_row),
                null,
                palette,
                null,
                trailing = { IosSwitch(draft.snooze, { dispatch(AlarmIntent.ToggleSnooze(it)) }, palette) },
                showDivider = draft.snooze,
            )
            if (draft.snooze) {
                GroupedRow(
                    stringResource(R.string.snooze_duration),
                    stringResource(R.string.minutes_count, draft.snoozeMinutes),
                    palette,
                    { dispatch(AlarmIntent.OpenPage(EditorPage.Snooze)) },
                    showDivider = false,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        GroupedCard(palette) {
            GroupedRow(
                stringResource(R.string.write_contacts),
                null,
                palette,
                onWriteContacts,
                showDivider = false,
            )
        }
        if (!draft.isNew) {
            Spacer(Modifier.height(36.dp))
            GroupedCard(palette) {
                Box(
                    Modifier.fillMaxWidth().height(IosMetrics.groupedRowMin).clickable { dispatch(AlarmIntent.Delete(draft.id)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.delete_birthday), color = palette.destructive, fontSize = IosMetrics.body)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ExportPage(
    draft: EditorDraft,
    state: AlarmListState,
    palette: IosPalette,
    dispatch: (AlarmIntent) -> Unit,
    onPickOther: () -> Unit,
) {
    val birthday = draft.birthday
    val busy = state.writingContact
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(R.string.export_title), dispatch)
        if (birthday != null) {
            Text(
                birthdayDateText(birthday),
                color = Ink,
                fontSize = 22.sp,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
        Text(
            stringResource(R.string.export_replaces),
            color = palette.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
        state.contactNotice?.takeIf { it != ContactNotice.Denied }?.let { notice ->
            Text(
                contactNoticeText(notice),
                color = if (notice == ContactNotice.Wrote) palette.green else palette.destructive,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.loadingExport && state.exportMatches == null -> Text(
                stringResource(R.string.finding_contacts),
                color = palette.secondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            state.exportMatches.isNullOrEmpty() -> Text(
                stringResource(R.string.no_name_match),
                color = palette.secondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
        GroupedCard(palette) {
            birthday?.contactId?.let { id ->
                GroupedRow(
                    stringResource(R.string.export_linked),
                    null,
                    palette,
                    { if (!busy) dispatch(AlarmIntent.ExportTo(id, false)) },
                )
            }
            state.exportMatches.orEmpty().forEach { match ->
                GroupedRow(
                    match.name,
                    match.birthdayLabel ?: stringResource(R.string.no_birthday_yet),
                    palette,
                    { if (!busy) dispatch(AlarmIntent.ExportTo(match.contactId, false)) },
                    showChevron = false,
                )
            }
            GroupedRow(
                stringResource(R.string.export_other),
                null,
                palette,
                { if (!busy) onPickOther() },
            )
            GroupedRow(
                stringResource(R.string.export_new, draft.label.ifBlank { stringResource(R.string.name) }),
                null,
                palette,
                { if (!busy) dispatch(AlarmIntent.ExportTo(null, true)) },
                showDivider = false,
            )
        }
    }
}

@Composable
private fun ImportSheet(
    state: AlarmListState,
    palette: IosPalette,
    cards: Boolean,
    morph: Float,
    onToggleCards: () -> Unit,
    dispatch: (AlarmIntent) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(InkPaper).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = IosMetrics.screenMargin)) {
            InkIconButton(
                "close",
                stringResource(R.string.cancel),
                { dispatch(AlarmIntent.CloseImports) },
                Modifier.align(Alignment.CenterStart),
            )
            Text(
                stringResource(R.string.contacts_title),
                color = Ink,
                fontSize = IosMetrics.body,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            LayoutMorphButton(
                morph,
                stringResource(if (cards) R.string.view_list else R.string.view_cards),
                onToggleCards,
                Modifier.align(Alignment.CenterEnd),
            )
        }
        val rows = state.imports.orEmpty()
        when {
            state.loadingImports && rows.isEmpty() -> Text(
                stringResource(R.string.finding_contacts),
                color = palette.secondary,
                modifier = Modifier.padding(IosMetrics.screenMargin),
            )
            rows.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                InkEmpty(R.drawable.empty_contacts, stringResource(R.string.contacts_empty))
            }
            else -> ShapeMorph(morph, (rows.size.coerceAtLeast(1) * 64).dp, card = {
                ContactDeck(rows, state.alarms, palette) { dispatch(AlarmIntent.ImportOne(it)) }
            }) {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    itemsIndexed(
                        rows,
                        key = { index, row -> "$index-${row.contactId}-${row.birthday.solar}-${row.birthday.month}-${row.birthday.day}" },
                    ) { _, row ->
                        val taken = alreadyImported(state.alarms, row)
                        Column(
                            Modifier.padding(horizontal = IosMetrics.screenMargin)
                                .clickable(enabled = !taken) { dispatch(AlarmIntent.ImportOne(row)) },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, color = Ink, fontSize = 17.sp)
                                    Text(birthdayDateText(row.birthday), color = palette.secondary, fontSize = 13.sp)
                                }
                                if (taken) {
                                    Text(stringResource(R.string.already_imported), color = palette.secondary, fontSize = 13.sp)
                                }
                            }
                            Separator(palette)
                        }
                    }
                }
            }
        }
    }
}

internal fun birthdayDateText(birthday: LunarBirthday) =
    if (birthday.solar) "${birthday.month}月${birthday.day}日" else lunarDateText(birthday.month, birthday.day)

@Composable
private fun contactNoticeText(notice: ContactNotice): String = when (notice) {
    ContactNotice.Wrote -> stringResource(R.string.export_wrote)
    ContactNotice.Failed -> stringResource(R.string.export_failed)
    ContactNotice.NeedName -> stringResource(R.string.export_need_name)
    ContactNotice.Denied -> stringResource(R.string.contacts_denied)
}

@Composable
private fun EditorForm(
    draft: EditorDraft,
    palette: IosPalette,
    is24: Boolean,
    dispatch: (AlarmIntent) -> Unit,
    onWriteContacts: () -> Unit = { dispatch(AlarmIntent.OpenPage(EditorPage.Export)) },
) {
    if (draft.birthday != null) {
        BirthdayEditor(draft, palette, is24, dispatch, onWriteContacts)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(if (draft.isNew) R.string.add_alarm else R.string.edit_alarm), dispatch, form = true)
        Spacer(Modifier.height(8.dp))
        TimeWheels(draft, palette, is24, dispatch)
        Spacer(Modifier.height(32.dp))
        GroupedCard(palette) {
            GroupedRow(
                stringResource(R.string.repeat),
                repeatSummary(draft.days),
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Repeat)) },
                showDivider = false,
                showChevron = false,
                valueColor = palette.label,
                trailing = { RepeatIndicator() },
            )
        }
        Text(
            repeatHelp(draft.days),
            color = palette.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
        )
        Spacer(Modifier.height(26.dp))
        GroupedCard(palette) {
            GroupedRow(
                stringResource(R.string.label),
                draft.label.ifBlank { stringResource(R.string.alarm_default_label) },
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Label)) },
                showChevron = false,
            )
            GroupedRow(
                stringResource(R.string.sound),
                toneName(draft.tone),
                palette,
                { dispatch(AlarmIntent.OpenPage(EditorPage.Sound)) },
            )
            GroupedRow(
                stringResource(R.string.snooze_row),
                null,
                palette,
                null,
                trailing = { IosSwitch(draft.snooze, { dispatch(AlarmIntent.ToggleSnooze(it)) }, palette) },
                showDivider = draft.snooze,
            )
            if (draft.snooze) {
                GroupedRow(
                    stringResource(R.string.snooze_duration),
                    stringResource(R.string.minutes_count, draft.snoozeMinutes),
                    palette,
                    { dispatch(AlarmIntent.OpenPage(EditorPage.Snooze)) },
                    showDivider = false,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
        if (!draft.isNew) {
            Spacer(Modifier.height(36.dp))
            GroupedCard(palette) {
                Box(
                    Modifier.fillMaxWidth().height(IosMetrics.groupedRowMin).clickable { dispatch(AlarmIntent.Delete(draft.id)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.delete_alarm), color = palette.destructive, fontSize = IosMetrics.body)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RepeatIndicator() {
    androidx.compose.foundation.Canvas(Modifier.padding(start = 10.dp).size(10.dp, 16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawLine(
            Ink,
            androidx.compose.ui.geometry.Offset(0f, h * 0.3f),
            androidx.compose.ui.geometry.Offset(w / 2, 0f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Ink,
            androidx.compose.ui.geometry.Offset(w / 2, 0f),
            androidx.compose.ui.geometry.Offset(w, h * 0.3f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Ink,
            androidx.compose.ui.geometry.Offset(0f, h * 0.7f),
            androidx.compose.ui.geometry.Offset(w / 2, h),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Ink,
            androidx.compose.ui.geometry.Offset(w / 2, h),
            androidx.compose.ui.geometry.Offset(w, h * 0.7f),
            stroke,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun TimeWheels(draft: EditorDraft, palette: IosPalette, is24: Boolean, dispatch: (AlarmIntent) -> Unit) {
    val hourItems = remember(is24) {
        if (is24) (0..23).map { it.toString().padStart(2, '0') } else (1..12).map(Int::toString)
    }
    val minuteItems = remember { (0..59).map { it.toString().padStart(2, '0') } }
    Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(horizontal = IosMetrics.screenMargin).fillMaxWidth().height(34.dp)
                .clip(CircleShape).background(palette.highlight),
        )
        Row(Modifier.width(if (is24) 154.dp else 234.dp), horizontalArrangement = Arrangement.Center) {
            WheelColumn(
                hourItems,
                if (is24) draft.hour else (draft.hour + 11) % 12,
                { index -> dispatch(AlarmIntent.ChangeTime(if (is24) index else to24(index, draft.hour >= 12), draft.minute)) },
                palette,
                Modifier.weight(1f),
            )
            WheelColumn(
                minuteItems,
                draft.minute,
                { dispatch(AlarmIntent.ChangeTime(draft.hour, it)) },
                palette,
                Modifier.weight(1f),
            )
            if (!is24) {
                WheelColumn(
                    listOf(stringResource(R.string.am), stringResource(R.string.pm)),
                    if (draft.hour >= 12) 1 else 0,
                    { dispatch(AlarmIntent.ChangeTime(to24((draft.hour + 11) % 12, it == 1), draft.minute)) },
                    palette,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RepeatPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EditorBar(stringResource(R.string.repeat), dispatch)
        val names = longNames()
        val days = weekDays()
        GroupedCard(palette) {
            days.forEachIndexed { index, day ->
                GroupedRow(
                    names.getValue(day),
                    if (day in draft.days) "✓" else "",
                    palette,
                    { dispatch(AlarmIntent.ToggleDay(day)) },
                    showDivider = index != days.lastIndex,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
    }
}

@Composable
private fun LabelPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    val title = stringResource(if (draft.birthday != null) R.string.name else R.string.label)
    val defaultLabel = stringResource(R.string.alarm_default_label)
    val shown = if (draft.birthday != null && draft.label == defaultLabel) "" else draft.label
    Column(Modifier.fillMaxSize()) {
        EditorBar(title, dispatch)
        GroupedCard(palette) {
            BasicTextField(
                shown,
                { dispatch(AlarmIntent.ChangeLabel(it)) },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = IosMetrics.body, fontFamily = IosFontFamily),
                cursorBrush = SolidColor(palette.blue),
                modifier = Modifier.fillMaxWidth().padding(IosMetrics.screenMargin),
            )
        }
    }
}

@Composable
private fun SoundPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EditorBar(stringResource(R.string.sound), dispatch)
        GroupedCard(palette) {
            AlarmTone.entries.forEachIndexed { index, tone ->
                GroupedRow(
                    toneName(tone),
                    if (tone == draft.tone) "✓" else "",
                    palette,
                    { dispatch(AlarmIntent.SelectTone(tone)) },
                    showDivider = index != AlarmTone.entries.lastIndex,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
    }
}

@Composable
private fun SnoozePage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(R.string.snooze_duration), dispatch)
        GroupedCard(palette) {
            (1..15).forEach { minutes ->
                GroupedRow(
                    stringResource(R.string.minutes_count, minutes),
                    if (draft.snoozeMinutes == minutes) "✓" else "",
                    palette,
                    { dispatch(AlarmIntent.ChangeSnoozeMinutes(minutes)) },
                    showDivider = minutes != 15,
                    showChevron = false,
                    valueColor = palette.blue,
                )
            }
        }
    }
}

@Composable
private fun BirthdayPage(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    val birthday = draft.birthday ?: return
    val months = remember(birthday.solar) {
        if (birthday.solar) (1..12).map { "${it}月" } else (1..12).map { lunarMonthName(it) }
    }
    val days = remember(birthday.solar) {
        if (birthday.solar) (1..31).map { "${it}日" } else (1..30).map { lunarDayName(it) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        EditorBar(stringResource(R.string.lunar_birthday), dispatch)
        Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.padding(horizontal = IosMetrics.screenMargin).fillMaxWidth().height(34.dp)
                    .clip(CircleShape).background(palette.highlight),
            )
            Row(Modifier.width(220.dp), horizontalArrangement = Arrangement.Center) {
                WheelColumn(
                    months,
                    abs(birthday.month) - 1,
                    { dispatch(AlarmIntent.ChangeLunarMonth(it + 1)) },
                    palette,
                    Modifier.weight(1f),
                )
                WheelColumn(
                    days,
                    birthday.day - 1,
                    { dispatch(AlarmIntent.ChangeLunarDay(it + 1)) },
                    palette,
                    Modifier.weight(1f),
                )
            }
        }
        BirthdayPreview(birthday, palette)
        Spacer(Modifier.height(16.dp))
        GroupedCard(palette) {
            if (!birthday.solar) {
                GroupedRow(
                    stringResource(R.string.leap_month),
                    null,
                    palette,
                    null,
                    trailing = { IosSwitch(birthday.month < 0, { dispatch(AlarmIntent.ToggleLeap(it)) }, palette) },
                )
            }
            GroupedRow(
                stringResource(R.string.record_year),
                null,
                palette,
                null,
                trailing = { IosSwitch(birthday.year != null, { dispatch(AlarmIntent.ToggleBirthYear(it)) }, palette) },
                showDivider = false,
            )
        }
        birthday.year?.let { stored ->
            val year = stored.coerceIn(1900, 2100)
            val years = remember { (1900..2100).map(Int::toString) }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.padding(horizontal = IosMetrics.screenMargin).fillMaxWidth().height(34.dp)
                        .clip(CircleShape).background(palette.highlight),
                )
                WheelColumn(
                    years,
                    year - 1900,
                    { dispatch(AlarmIntent.ChangeBirthYear(1900 + it)) },
                    palette,
                    Modifier.width(120.dp),
                )
            }
            Text(
                stringResource(if (birthday.solar) R.string.birth_year else R.string.lunar_year),
                color = palette.secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 32.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BirthdayPreview(birthday: LunarBirthday, palette: IosPalette) {
    val today = LocalDate.now()
    if (birthday.solar) {
        val date = nextSolarDate(today, birthday.month, birthday.day)
        val lines = buildList {
            add(birthdayDateText(birthday))
            if (date != null) {
                val solar = stringResource(R.string.solar_date, date.year, date.monthValue, date.dayOfMonth)
                val age = birthday.year?.let { date.year - it }?.takeIf { it > 0 }?.let { stringResource(R.string.age_years, it) }
                add(listOfNotNull(solar, daysUntil(today, date), age).joinToString(" · "))
                if (date.dayOfMonth != birthday.day) add(stringResource(R.string.missing_day))
            }
        }
        BirthdayLines(lines, palette)
        return
    }
    val resolved = resolveNextLunar(today, birthday.month, birthday.day)
    val month = if (resolved == null || resolved.leapKept) birthday.month else abs(birthday.month)
    val day = resolved?.day ?: birthday.day
    val lines = buildList {
        add(lunarDateText(month, day))
        if (resolved != null) {
            val whenText = daysUntil(today, resolved.date)
            val solar = stringResource(R.string.solar_date, resolved.date.year, resolved.date.monthValue, resolved.date.dayOfMonth)
            val age = birthday.year?.let { resolved.lunarYear - it }?.takeIf { it > 0 }?.let { stringResource(R.string.age_years, it) }
            add(listOfNotNull(solar, whenText, age).joinToString(" · "))
            if (birthday.month < 0 && !resolved.leapKept) add(stringResource(R.string.no_leap_this_year))
            if (birthday.day != resolved.day) add(stringResource(R.string.short_month))
        }
    }
    BirthdayLines(lines, palette)
}

@Composable
private fun BirthdayLines(lines: List<String>, palette: IosPalette) {
    Column(Modifier.padding(horizontal = 32.dp)) {
        lines.forEachIndexed { index, line ->
            Text(line, color = if (index == 0) Ink else palette.secondary, fontSize = if (index == 0) 16.sp else 13.sp)
        }
    }
}

@Composable
internal fun daysUntil(today: LocalDate, date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return if (days == 0) stringResource(R.string.today) else stringResource(R.string.days_until, days)
}

@Composable
private fun subtitle(alarm: Alarm): String {
    val default = stringResource(R.string.alarm_default_label)
    val label = alarm.label.takeIf { it.isNotBlank() && it != default }
    val birthday = alarm.birthday
    if (birthday != null) {
        val today = LocalDate.now()
        if (birthday.solar) {
            val date = nextSolarDate(today, birthday.month, birthday.day)
            return listOfNotNull(label, birthdayDateText(birthday), date?.let { daysUntil(today, it) }).joinToString(" · ")
        }
        val resolved = resolveNextLunar(today, birthday.month, birthday.day)
        val month = if (resolved == null || resolved.leapKept) birthday.month else abs(birthday.month)
        val day = resolved?.day ?: birthday.day
        val whenText = resolved?.let { daysUntil(today, it.date) }
        return listOfNotNull(label, lunarDateText(month, day), whenText).joinToString(" · ")
    }
    val repeat = if (alarm.days.isEmpty()) null else repeatSummary(alarm.days)
    return listOfNotNull(label, repeat).joinToString(" · ").ifBlank { default }
}

@Composable
private fun repeatHelp(days: Set<DayOfWeek>): String = stringResource(
    if (days.isEmpty()) R.string.repeat_help_once else R.string.repeat_help_days,
)

@Composable
private fun repeatSummary(days: Set<DayOfWeek>): String {
    val shorts = shortNames()
    val longs = longNames()
    return when {
        days.isEmpty() -> stringResource(R.string.never)
        days.size == 7 -> stringResource(R.string.every_day)
        days == WEEKDAYS -> stringResource(R.string.weekdays)
        days == WEEKENDS -> stringResource(R.string.weekends)
        days.size == 1 -> longs.getValue(days.first())
        else -> weekDays().filter { it in days }.joinToString(" ") { shorts.getValue(it) }
    }
}

@Composable
private fun longNames() = mapOf(
    DayOfWeek.SUNDAY to stringResource(R.string.day_sun),
    DayOfWeek.MONDAY to stringResource(R.string.day_mon),
    DayOfWeek.TUESDAY to stringResource(R.string.day_tue),
    DayOfWeek.WEDNESDAY to stringResource(R.string.day_wed),
    DayOfWeek.THURSDAY to stringResource(R.string.day_thu),
    DayOfWeek.FRIDAY to stringResource(R.string.day_fri),
    DayOfWeek.SATURDAY to stringResource(R.string.day_sat),
)

@Composable
private fun shortNames() = mapOf(
    DayOfWeek.SUNDAY to stringResource(R.string.short_sun),
    DayOfWeek.MONDAY to stringResource(R.string.short_mon),
    DayOfWeek.TUESDAY to stringResource(R.string.short_tue),
    DayOfWeek.WEDNESDAY to stringResource(R.string.short_wed),
    DayOfWeek.THURSDAY to stringResource(R.string.short_thu),
    DayOfWeek.FRIDAY to stringResource(R.string.short_fri),
    DayOfWeek.SATURDAY to stringResource(R.string.short_sat),
)

@Composable
private fun toneName(tone: AlarmTone) = stringResource(
    when (tone) {
        AlarmTone.Rise -> R.string.tone_rise
        AlarmTone.Pulse -> R.string.tone_pulse
        AlarmTone.Glass -> R.string.tone_glass
        AlarmTone.Harbor -> R.string.tone_harbor
        AlarmTone.Clarity -> R.string.tone_clarity
    },
)

private fun weekDays(): List<DayOfWeek> {
    val first = Calendar.getInstance().firstDayOfWeek
    val start = if (first == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(first - 1)
    val days = DayOfWeek.entries
    return days.drop(start.ordinal) + days.take(start.ordinal)
}

private fun to24(index: Int, pm: Boolean) = (index + 1) % 12 + if (pm) 12 else 0
private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
