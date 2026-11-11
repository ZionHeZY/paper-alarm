package tech.hezy.alarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.hezy.alarm.R
import tech.hezy.alarm.data.ContactCandidate
import tech.hezy.alarm.data.alreadyImported
import tech.hezy.alarm.data.resolveNextLunar
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.LunarBirthday
import tech.hezy.alarm.domain.lunarDateText
import tech.hezy.alarm.domain.lunarDayName
import tech.hezy.alarm.domain.lunarMonthName
import tech.hezy.alarm.domain.nextSolarDate
import java.time.LocalDate
import kotlin.math.abs

private val CardInk = Ink
private val CardSlab = InkCard
private val CardWell = InkYellow
private val LunarAccent = Ink
private val SolarAccent = Ink
private val CardLine = Ink
private val CardShape = RoundedCornerShape(22.dp)

@Composable
internal fun LayoutMorphButton(progress: Float, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    val interaction = remember { MutableInteractionSource() }
    val shift = if (interaction.collectIsPressedAsState().value) 3.dp else 0.dp
    Box(modifier.size(IosMetrics.minTouch).semantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize().offset(x = 3.dp, y = 3.dp).clip(CircleShape).background(Ink))
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = 3.dp, bottom = 3.dp)
                .offset(x = shift, y = shift)
                .clip(CircleShape)
                .border(2.dp, Ink, CircleShape)
                .background(InkCard)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
            val stroke = 2.2.dp.toPx()
            val inset = 3.4.dp.toPx()
            val left = inset
            val right = size.width - inset
            val top = inset
            val bottom = size.height - inset
            val span = bottom - top
            val y1 = lerp(top, top + span * 0.18f, p)
            val y3 = lerp(bottom, top + span * 0.82f, p)
            val y2 = (y1 + y3) / 2f
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            val side = (1f - p * 1.25f).coerceIn(0f, 1f)
            if (p < 0.04f) {
                drawRoundRect(
                    Ink,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            } else {
                drawLine(Ink, Offset(left, y1), Offset(right, y1), stroke, cap)
                drawLine(Ink, Offset(left, y3), Offset(right, y3), stroke, cap)
                val mid = (p * 1.35f - 0.15f).coerceIn(0f, 1f)
                if (mid > 0.02f) {
                    drawLine(Ink.copy(alpha = mid), Offset(left, y2), Offset(right, y2), stroke, cap)
                }
                if (side > 0.02f) {
                    drawLine(Ink.copy(alpha = side), Offset(left, y1), Offset(left, y3), stroke, cap)
                    drawLine(Ink.copy(alpha = side), Offset(right, y1), Offset(right, y3), stroke, cap)
                }
            }
        }
        }
    }
}

@Composable
internal fun BirthdayDeck(
    alarms: List<Alarm>,
    is24: Boolean,
    palette: IosPalette,
    onOpen: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    height: Dp? = null,
) {
    if (alarms.isEmpty()) return
    CardStack(alarms.size, height) { index, modifier ->
        val alarm = alarms[index]
        val birthday = alarm.birthday ?: return@CardStack
        val (count, next) = birthdayWhen(birthday)
        BirthdayCard(
            month = if (birthday.solar) "${birthday.month}月" else lunarMonthName(birthday.month),
            day = birthday.day.toString().padStart(2, '0'),
            daySuffix = stringResource(R.string.card_day),
            kicker = stringResource(if (birthday.solar) R.string.card_kicker_solar else R.string.card_kicker_lunar),
            name = alarm.label.ifBlank { stringResource(R.string.name) },
            meta = birthdayDateText(birthday),
            count = count,
            next = next,
            footer = stringResource(R.string.remind_at, clockText(alarm.hour, alarm.minute, is24)),
            accent = if (birthday.solar) SolarAccent else LunarAccent,
            palette = palette,
            enabled = alarm.enabled,
            onToggle = { onToggle(alarm.id) },
            modifier = modifier,
            onClick = { onOpen(alarm.id) },
        )
    }
}

@Composable
internal fun ContactDeck(
    rows: List<ContactCandidate>,
    alarms: List<Alarm>,
    palette: IosPalette,
    onImport: (ContactCandidate) -> Unit,
) {
    if (rows.isEmpty()) return
    CardStack(rows.size) { index, modifier ->
        val row = rows[index]
        val birthday = row.birthday
        val taken = alreadyImported(alarms, row)
        BirthdayCard(
            month = if (birthday.solar) "${birthday.month}月" else lunarMonthName(birthday.month),
            day = birthday.day.toString().padStart(2, '0'),
            daySuffix = stringResource(R.string.card_day),
            kicker = stringResource(R.string.contacts_title),
            name = row.name,
            meta = birthdayDateText(birthday),
            count = "",
            next = "",
            footer = stringResource(if (taken) R.string.already_imported else R.string.card_join),
            accent = if (birthday.solar) SolarAccent else LunarAccent,
            palette = palette,
            enabled = !taken,
            onToggle = null,
            modifier = modifier,
            onClick = { if (!taken) onImport(row) },
        )
    }
}

@Composable
internal fun BirthdayDateBlock(draft: EditorDraft, palette: IosPalette, dispatch: (AlarmIntent) -> Unit) {
    val birthday = draft.birthday ?: return
    val months = remember(birthday.solar) {
        if (birthday.solar) (1..12).map { "${it}月" } else (1..12).map { lunarMonthName(it) }
    }
    val days = remember(birthday.solar) {
        if (birthday.solar) (1..31).map { "${it}日" } else (1..30).map { lunarDayName(it) }
    }
    val years = remember { (1900..2100).map(Int::toString) }
    val showYear = birthday.year != null
    Column(Modifier.fillMaxWidth()) {
        GroupedCard(palette) {
            Box(Modifier.fillMaxWidth().padding(IosMetrics.screenMargin)) {
                if (draft.label.isEmpty()) {
                    Text(stringResource(R.string.name), color = palette.secondary, fontSize = IosMetrics.body)
                }
                BasicTextField(
                    draft.label,
                    { dispatch(AlarmIntent.ChangeLabel(it)) },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = IosMetrics.body, fontFamily = IosFontFamily),
                    cursorBrush = SolidColor(palette.blue),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(196.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.padding(horizontal = IosMetrics.screenMargin).fillMaxWidth().height(34.dp)
                    .clip(CircleShape).background(palette.highlight),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (showYear) {
                    WheelColumn(
                        years,
                        (birthday.year ?: 2000).coerceIn(1900, 2100) - 1900,
                        { dispatch(AlarmIntent.ChangeBirthYear(1900 + it)) },
                        palette,
                        Modifier.weight(1.15f),
                    )
                }
                WheelColumn(
                    months,
                    abs(birthday.month) - 1,
                    { dispatch(AlarmIntent.ChangeLunarMonth(it + 1)) },
                    palette,
                    Modifier.weight(1f),
                )
                WheelColumn(
                    days,
                    (birthday.day - 1).coerceIn(0, days.lastIndex),
                    { dispatch(AlarmIntent.ChangeLunarDay(it + 1)) },
                    palette,
                    Modifier.weight(1f),
                )
            }
        }
        val (count, next) = birthdayWhen(birthday)
        if (count.isNotEmpty()) {
            Text(
                listOf(count, next).filter { it.isNotEmpty() }.joinToString(" · "),
                color = palette.secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 32.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        GroupedCard(palette) {
            if (!birthday.solar) {
                GroupedRow(
                    stringResource(R.string.leap_month),
                    null,
                    palette,
                    null,
                    trailing = { IosSwitch(birthday.month < 0, { dispatch(AlarmIntent.ToggleLeap(it)) }, palette) },
                    showDivider = true,
                )
            }
            GroupedRow(
                stringResource(R.string.record_year),
                null,
                palette,
                null,
                trailing = { IosSwitch(showYear, { dispatch(AlarmIntent.ToggleBirthYear(it)) }, palette) },
                showDivider = false,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardStack(
    count: Int,
    height: Dp? = null,
    card: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val state = rememberPagerState(pageCount = { count })
    val page = state.currentPage.coerceIn(0, count - 1)
    val dots = if (count > 1) 18.dp else 0.dp
    val pager = if (height == null) 440.dp else (height - dots).coerceAtLeast(140.dp)
    Column(
        if (height == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().height(height),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            beyondBoundsPageCount = 1,
            modifier = Modifier.fillMaxWidth().height(pager),
        ) { index ->
            val offset = (state.currentPage - index) + state.currentPageOffsetFraction
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                card(
                    index,
                    Modifier.graphicsLayer {
                        val distance = abs(offset).coerceIn(0f, 1f)
                        val scale = 1f - 0.04f * distance
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.35f * distance
                    },
                )
            }
        }
        if (count > 1) {
            val shown = count.coerceAtMost(8)
            val selected = if (count <= shown) page else (page * (shown - 1) / (count - 1)).coerceIn(0, shown - 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                repeat(shown) { dot ->
                    Box(
                        Modifier.size(if (dot == selected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (dot == selected) Ink else Color(0xFFC8C4B8)),
                    )
                }
            }
        }
    }
}

@Composable
private fun BirthdayCard(
    month: String,
    day: String,
    daySuffix: String,
    kicker: String,
    name: String,
    meta: String,
    count: String,
    next: String,
    footer: String?,
    accent: Color,
    palette: IosPalette,
    enabled: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ink = if (enabled) CardInk else Color(0xFF8A8680)
    val interaction = remember { MutableInteractionSource() }
    val shift = if (interaction.collectIsPressedAsState().value) 3.dp else 0.dp
    Box(modifier.wrapContentHeight()) {
        val short = false
        val pad = 18.dp
        val well = 112.dp
        Box(Modifier.matchParentSize().offset(x = 3.dp, y = 3.dp).clip(CardShape).background(Ink))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = 3.dp, bottom = 3.dp)
                .offset(x = shift, y = shift)
                .clip(CardShape)
                .background(CardSlab)
                .border(2.dp, CardLine, CardShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            Column(
                Modifier.padding(pad),
            ) {
                Text(kicker, color = accent, fontSize = 12.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(if (short) 6.dp else 12.dp))
                Column(
                    Modifier.fillMaxWidth().height(well).clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Ink, RoundedCornerShape(16.dp)).background(CardWell)
                        .padding(horizontal = 16.dp, vertical = if (short) 8.dp else 12.dp),
                ) {
                    Text(month, color = ink.copy(alpha = 0.62f), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            day,
                            color = ink,
                            fontSize = if (short) 36.sp else 54.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IosFontFamily,
                        )
                        if (daySuffix.isNotEmpty()) {
                            Text(
                                daySuffix,
                                color = ink.copy(alpha = 0.55f),
                                fontSize = if (short) 14.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, bottom = if (short) 6.dp else 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (short) 6.dp else 14.dp))
                Text(
                    name,
                    color = ink,
                    fontSize = if (short) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (short) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(meta, color = ink.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (count.isNotEmpty()) {
                    if (!short) {
                        Text(stringResource(R.string.card_next), color = ink.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    Text(count, color = if (enabled) accent else ink, fontSize = if (short) 20.sp else 26.sp, fontWeight = FontWeight.Black)
                    if (!short && next.isNotEmpty()) Text(next, color = ink.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            if (footer != null || onToggle != null) {
                Column(Modifier.padding(horizontal = pad).padding(bottom = if (short) 8.dp else 14.dp)) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(CardLine))
                    Row(Modifier.padding(top = if (short) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(footer.orEmpty(), color = ink.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (onToggle != null) IosSwitch(enabled, { onToggle() }, palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun birthdayWhen(birthday: LunarBirthday): Pair<String, String> {
    val today = LocalDate.now()
    val date = if (birthday.solar) {
        nextSolarDate(today, birthday.month, birthday.day)
    } else {
        resolveNextLunar(today, birthday.month, birthday.day)?.date
    } ?: return "" to ""
    return daysUntil(today, date) to stringResource(R.string.solar_date, date.year, date.monthValue, date.dayOfMonth)
}

private fun clockText(hour: Int, minute: Int, is24: Boolean): String {
    val h = if (is24) hour else (hour % 12).let { if (it == 0) 12 else it }
    val hours = if (is24) h.toString().padStart(2, '0') else h.toString()
    return "$hours:${minute.toString().padStart(2, '0')}"
}
