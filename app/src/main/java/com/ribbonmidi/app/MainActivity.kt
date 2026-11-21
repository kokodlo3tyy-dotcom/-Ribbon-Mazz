package com.ribbonmidi.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

// ألوان التصميم الداكن المعتمد
private val BgDark = Color(0xFF0D1015)
private val PanelDark = Color(0xFF161B22)
private val SurfaceDark = Color(0xFF1E242E)
private val BorderDark = Color(0xFF2D3542)
private val CyanNeon = Color(0xFF00E5FF)
private val AmberGold = Color(0xFFFFB300)
private val GreenOk = Color(0xFF00E676)
private val TextMain = Color(0xFFE6EDF3)
private val TextDim = Color(0xFF8B949E)

class MainActivity : ComponentActivity() {
    private lateinit var midiEngine: MidiEngine
    private lateinit var scaleManager: ScaleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        scaleManager = ScaleManager(this)
        val connectedState = mutableStateOf(false)
        val deviceNameState = mutableStateOf("No USB MIDI")

        midiEngine = MidiEngine(this) { isConnected, name ->
            connectedState.value = isConnected
            deviceNameState.value = name
        }
        midiEngine.start()

        setContent {
            ArabicRibbonApp(
                midiEngine = midiEngine,
                scaleManager = scaleManager,
                isConnected = connectedState.value,
                deviceName = deviceNameState.value
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        midiEngine.stop()
    }
}

@Composable
fun ArabicRibbonApp(
    midiEngine: MidiEngine,
    scaleManager: ScaleManager,
    isConnected: Boolean,
    deviceName: String
) {
    // التنقل بين الشاشة الرئيسية (الريبون) وشاشة السكيل الكاملة
    var currentScreen by remember { mutableStateOf("RIBBON") } // "RIBBON" or "SCALE"
    var showSettings by remember { mutableStateOf(false) }

    // حالات الأزرار العلوية
    var isMono by remember { mutableStateOf(true) }
    var isLegato by remember { mutableStateOf(false) }
    var isGlide by remember { mutableStateOf(false) }
    var glideValue by remember { mutableStateOf(35f) }

    // إعدادات الريبون
    var pitchRange by remember { mutableStateOf(2) }
    var ribbonTravel by remember { mutableStateOf(0.8f) }     // 0.3 إلى 1.0
    var springSpeed by remember { mutableStateOf(0.18f) }     // سرعة الرجوع للصفر
    var isRelativeMode by remember { mutableStateOf(true) }

    // حالات السكيل والترانسبوز والبنوك
    var quarterNotes by remember { mutableStateOf(BooleanArray(12) { false }) }
    var selectedRootKey by remember { mutableStateOf(2) }     // D افتراضياً
    var selectedMaqam by remember { mutableStateOf<MaqamPreset?>(null) }
    var transpose by remember { mutableStateOf(0) }
    var activeBank by remember { mutableStateOf(-1) }
    var bankVersion by remember { mutableStateOf(0) }

    // تحديث المحرك عند أي تغيير
    LaunchedEffect(quarterNotes, transpose, pitchRange) {
        midiEngine.quarterToneMask = quarterNotes
        midiEngine.transposeSemitones = transpose
        midiEngine.pitchBendRangeSemitones = pitchRange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (currentScreen == "RIBBON") {
            RibbonMainScreen(
                isMono = isMono,
                onToggleMono = {
                    isMono = !isMono
                    midiEngine.sendMonoPoly(isMono)
                },
                isLegato = isLegato,
                onToggleLegato = {
                    isLegato = !isLegato
                    midiEngine.sendLegato(isLegato)
                },
                isGlide = isGlide,
                onToggleGlide = {
                    isGlide = !isGlide
                    midiEngine.sendGlideEnable(isGlide)
                },
                glideValue = glideValue,
                onGlideValueChange = {
                    glideValue = it
                    midiEngine.sendGlideTime(it.toInt())
                },
                isConnected = isConnected,
                deviceName = deviceName,
                pitchRange = pitchRange,
                ribbonTravel = ribbonTravel,
                springSpeed = springSpeed,
                isRelativeMode = isRelativeMode,
                transpose = transpose,
                hasActiveScale = quarterNotes.any { it },
                onPitchChange = { midiEngine.updateRibbonPitch(it) },
                onOpenScale = { currentScreen = "SCALE" },
                onOpenSettings = { showSettings = true }
            )
        } else {
            ArabicScaleFullScreen(
                scaleManager = scaleManager,
                quarterNotes = quarterNotes,
                selectedRootKey = selectedRootKey,
                selectedMaqam = selectedMaqam,
                transpose = transpose,
                activeBank = activeBank,
                bankVersion = bankVersion,
                onToggleNote = { idx ->
                    val copy = quarterNotes.copyOf()
                    copy[idx] = !copy[idx]
                    quarterNotes = copy
                    selectedMaqam = null
                },
                onSelectRoot = { root ->
                    selectedRootKey = root
                    selectedMaqam?.let { maqam ->
                        quarterNotes = scaleManager.getMaqamNotes(maqam, root)
                    }
                },
                onSelectMaqam = { maqam ->
                    selectedMaqam = maqam
                    quarterNotes = scaleManager.getMaqamNotes(maqam, selectedRootKey)
                },
                onResetScale = {
                    quarterNotes = BooleanArray(12) { false }
                    selectedMaqam = null
                    activeBank = -1
                },
                onTransposeChange = { transpose = it.coerceIn(-12, 12) },
                onSaveBank = { bankIdx ->
                    scaleManager.saveBank(bankIdx, quarterNotes, transpose)
                    activeBank = bankIdx
                    bankVersion++
                },
                onLoadBank = { bankIdx ->
                    scaleManager.loadBank(bankIdx)?.let { (loadedNotes, loadedTrans) ->
                        quarterNotes = loadedNotes
                        transpose = loadedTrans
                        activeBank = bankIdx
                        selectedMaqam = null
                    }
                },
                onBack = { currentScreen = "RIBBON" }
            )
        }

        // نافذة الإعدادات المنبثقة
        if (showSettings) {
            SettingsDialog(
                pitchRange = pitchRange,
                ribbonTravel = ribbonTravel,
                springSpeed = springSpeed,
                isRelativeMode = isRelativeMode,
                onPitchRangeChange = {
                    pitchRange = it
                    midiEngine.sendPitchBendRangeRpn(it)
                },
                onTravelChange = { ribbonTravel = it },
                onSpringSpeedChange = { springSpeed = it },
                onModeChange = { isRelativeMode = it },
                onReconnectMidi = { midiEngine.autoConnect() },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
fun RibbonMainScreen(
    isMono: Boolean,
    onToggleMono: () -> Unit,
    isLegato: Boolean,
    onToggleLegato: () -> Unit,
    isGlide: Boolean,
    onToggleGlide: () -> Unit,
    glideValue: Float,
    onGlideValueChange: (Float) -> Unit,
    isConnected: Boolean,
    deviceName: String,
    pitchRange: Int,
    ribbonTravel: Float,
    springSpeed: Float,
    isRelativeMode: Boolean,
    transpose: Int,
    hasActiveScale: Boolean,
    onPitchChange: (Float) -> Unit,
    onOpenScale: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var pitchValue by remember { mutableStateOf(0f) } // -1.0 to +1.0
    var isTouching by remember { mutableStateOf(false) }
    var fingerXRatio by remember { mutableStateOf(0.5f) }
    var trailXRatio by remember { mutableStateOf(0.5f) }

    // فيزياء الرجوع التلقائي الناعم (Spring-back) وتتبع ظل النيون
    LaunchedEffect(isTouching, springSpeed) {
        while (true) {
            if (!isTouching && abs(pitchValue) > 0.001f) {
                pitchValue *= (1f - springSpeed)
                if (abs(pitchValue) < 0.004f) pitchValue = 0f
                onPitchChange(pitchValue)
                fingerXRatio = 0.5f + (pitchValue * 0.45f)
            }
            // حركة الظل النيون الناعمة وراء الصباع
            trailXRatio += (fingerXRatio - trailXRatio) * 0.22f
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // الشريط العلوي (Top Hardware Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PanelDark)
                .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HardwareToggleButton(
                    label = if (isMono) "MONO" else "POLY",
                    active = isMono,
                    activeColor = CyanNeon,
                    onClick = onToggleMono
                )
                HardwareToggleButton(
                    label = "LEGATO",
                    active = isLegato,
                    activeColor = CyanNeon,
                    onClick = onToggleLegato
                )
                HardwareToggleButton(
                    label = "GLIDE",
                    active = isGlide,
                    activeColor = CyanNeon,
                    onClick = onToggleGlide
                )
                // سلايدر الجلايد الصغير
                Slider(
                    value = glideValue,
                    onValueChange = onGlideValueChange,
                    valueRange = 0f..127f,
                    modifier = Modifier.width(100.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanNeon,
                        activeTrackColor = CyanNeon,
                        inactiveTrackColor = BorderDark
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // مؤشر USB MIDI
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) GreenOk else Color.Red)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "USB MIDI" else "OFFLINE",
                        color = if (isConnected) GreenOk else TextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // زرار شاشة السكيل العربي الكاملة
                HardwareToggleButton(
                    label = if (transpose != 0) "SCALE (T:${if (transpose > 0) "+$transpose" else transpose})" else "ARABIC SCALE",
                    active = hasActiveScale || transpose != 0,
                    activeColor = AmberGold,
                    onClick = onOpenScale
                )

                // زرار الإعدادات
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = TextMain, fontSize = 16.sp)
                }
            }
        }

        // منطقة شريط الريبون (80% من الشاشة)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(PanelDark)
                .border(1.5.dp, BorderDark, RoundedCornerShape(16.dp))
                .pointerInput(ribbonTravel, isRelativeMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isTouching = true
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val startX = down.position.x
                        val effectiveHalfWidth = (width / 2f) * ribbonTravel

                        fun updateFromX(currentX: Float) {
                            val normalized = if (isRelativeMode) {
                                ((currentX - startX) / effectiveHalfWidth).coerceIn(-1f, 1f)
                            } else {
                                ((currentX - width / 2f) / effectiveHalfWidth).coerceIn(-1f, 1f)
                            }
                            pitchValue = normalized
                            fingerXRatio = (currentX / width).coerceIn(0.03f, 0.97f)
                            onPitchChange(normalized)
                        }

                        if (!isRelativeMode) {
                            updateFromX(down.position.x)
                        } else {
                            fingerXRatio = (down.position.x / width).coerceIn(0.03f, 0.97f)
                        }

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                updateFromX(change.position.x)
                                change.consume()
                            } else {
                                break
                            }
                        } while (event.changes.any { it.pressed })

                        isTouching = false
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerX = w / 2f
                val centerY = h / 2f

                // تدريج المسطرة العلوية
                val ticks = 40
                for (i in 0..ticks) {
                    val x = (w / ticks) * i
                    val isMajor = i % 5 == 0
                    val tickH = if (isMajor) 18f else 9f
                    drawLine(
                        color = if (i == ticks / 2) CyanNeon else BorderDark,
                        start = Offset(x, 12f),
                        end = Offset(x, 12f + tickH),
                        strokeWidth = if (isMajor) 2.5f else 1.5f
                    )
                }

                // الخط الأفقي الأوسط
                drawLine(
                    color = BorderDark,
                    start = Offset(24f, centerY),
                    end = Offset(w - 24f, centerY),
                    strokeWidth = 2f
                )

                // خط المنتصف (Zero Point)
                drawLine(
                    color = CyanNeon.copy(alpha = 0.45f),
                    start = Offset(centerX, 36f),
                    end = Offset(centerX, h - 24f),
                    strokeWidth = 2f
                )

                // شريط التوهج من المنتصف لمكان البيتش الحالي
                val pitchX = centerX + (pitchValue * (w * 0.45f))
                drawLine(
                    color = CyanNeon,
                    start = Offset(centerX, centerY),
                    end = Offset(pitchX, centerY),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )

                // تأثير ظل النيون التفاعلي (Neon Glow Shadow Trail)
                val trailX = trailXRatio * w
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CyanNeon.copy(alpha = if (isTouching) 0.40f else 0.16f),
                            CyanNeon.copy(alpha = 0.0f)
                        ),
                        center = Offset(trailX, centerY),
                        radius = h * 0.48f
                    ),
                    radius = h * 0.48f,
                    center = Offset(trailX, centerY)
                )

                // نقطة النيون الأساسية المضيئة
                val activeX = if (isTouching) fingerXRatio * w else pitchX
                drawCircle(
                    color = CyanNeon.copy(alpha = 0.35f),
                    radius = 28f,
                    center = Offset(activeX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 10f,
                    center = Offset(activeX, centerY)
                )
            }

            // نصوص توضيحية بسيطة على أطراف الريبون
            Text(
                text = "±$pitchRange ST",
                color = TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
            Text(
                text = if (isRelativeMode) "RELATIVE" else "ABSOLUTE",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
            )
        }
    }
}

@Composable
fun ArabicScaleFullScreen(
    scaleManager: ScaleManager,
    quarterNotes: BooleanArray,
    selectedRootKey: Int,
    selectedMaqam: MaqamPreset?,
    transpose: Int,
    activeBank: Int,
    bankVersion: Int,
    onToggleNote: (Int) -> Unit,
    onSelectRoot: (Int) -> Unit,
    onSelectMaqam: (MaqamPreset) -> Unit,
    onResetScale: () -> Unit,
    onTransposeChange: (Int) -> Unit,
    onSaveBank: (Int) -> Unit,
    onLoadBank: (Int) -> Unit,
    onBack: () -> Unit
) {
    var saveMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // الهيدر العلوي للرجوع للريبون
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HardwareToggleButton(
                label = "← BACK TO RIBBON",
                active = true,
                activeColor = CyanNeon,
                onClick = onBack
            )
            Text(
                text = "ARABIC SCALE & USER BANKS (-50 CENTS)",
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            HardwareToggleButton(
                label = "RESET SCALE",
                active = false,
                activeColor = Color.Red,
                onClick = onResetScale
            )
        }

        // صف المقامات والـ Root Key
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PanelDark)
                .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MaqamPreset.entries.forEach { maqam ->
                    HardwareToggleButton(
                        label = maqam.displayName,
                        active = selectedMaqam == maqam,
                        activeColor = AmberGold,
                        onClick = { onSelectMaqam(maqam) }
                    )
                }
            }

            // اختيار الـ Root Key
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("ROOT:", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                scaleManager.noteNames.forEachIndexed { index, name ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selectedRootKey == index) CyanNeon else SurfaceDark)
                            .clickable { onSelectRoot(index) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = name,
                            color = if (selectedRootKey == index) BgDark else TextMain,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // مفاتيح الأوكتاف الـ 12 لتشريق أي نوتة يدوياً
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(PanelDark)
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            scaleManager.noteNames.forEachIndexed { index, name ->
                val isSharp = name.contains("#")
                val isQuarter = quarterNotes[index]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isQuarter -> AmberGold
                                isSharp -> Color(0xFF101318)
                                else -> SurfaceDark
                            }
                        )
                        .border(
                            1.dp,
                            if (isQuarter) Color.White else BorderDark,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleNote(index) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        if (isQuarter) {
                            Text("-50", color = BgDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = name,
                            color = if (isQuarter) BgDark else TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // الصف السفلي: بنوك الحفظ (1-6) + شريط الترانسبوز المدمج
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PanelDark)
                .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // البنوك المحفوظة
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HardwareToggleButton(
                    label = if (saveMode) "TAP BANK TO SAVE" else "SAVE BANK",
                    active = saveMode,
                    activeColor = Color(0xFFFF5252),
                    onClick = { saveMode = !saveMode }
                )
                // bankVersion يضمن تحديث ألوان البنوك بعد الحفظ
                key(bankVersion) {
                    for (i in 1..6) {
                        val saved = scaleManager.isBankSaved(i)
                        HardwareToggleButton(
                            label = "B$i",
                            active = activeBank == i,
                            activeColor = if (saved) AmberGold else TextDim,
                            onClick = {
                                if (saveMode) {
                                    onSaveBank(i)
                                    saveMode = false
                                } else {
                                    onLoadBank(i)
                                }
                            }
                        )
                    }
                }
            }

            // الترانسبوز المدمج
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("TRANSPOSE", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                HardwareToggleButton(label = " - ", active = false, activeColor = CyanNeon) {
                    onTransposeChange(transpose - 1)
                }
                Text(
                    text = if (transpose > 0) "+$transpose" else "$transpose",
                    color = if (transpose != 0) CyanNeon else TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                HardwareToggleButton(label = " + ", active = false, activeColor = CyanNeon) {
                    onTransposeChange(transpose + 1)
                }
                HardwareToggleButton(label = "0", active = false, activeColor = TextDim) {
                    onTransposeChange(0)
                }
            }
        }
    }
}

@Composable
fun HardwareToggleButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) activeColor.copy(alpha = 0.2f) else SurfaceDark)
            .border(
                width = 1.dp,
                color = if (active) activeColor else BorderDark,
                shape = RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeColor else TextMain,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsDialog(
    pitchRange: Int,
    ribbonTravel: Float,
    springSpeed: Float,
    isRelativeMode: Boolean,
    onPitchRangeChange: (Int) -> Unit,
    onTravelChange: (Float) -> Unit,
    onSpringSpeedChange: (Float) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onReconnectMidi: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelDark,
        titleContentColor = TextMain,
        textContentColor = TextMain,
        title = { Text("Ribbon & MIDI Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pitch Bend Range: ±$pitchRange Semitones", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 5, 7, 12, 24).forEach { range ->
                        HardwareToggleButton(
                            label = "±$range",
                            active = pitchRange == range,
                            activeColor = CyanNeon,
                            onClick = { onPitchRangeChange(range) }
                        )
                    }
                }

                Text("Ribbon Travel Width: ${(ribbonTravel * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = ribbonTravel,
                    onValueChange = onTravelChange,
                    valueRange = 0.3f..1.0f
                )

                Text("Spring-Back Speed: ${(springSpeed * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = springSpeed,
                    onValueChange = onSpringSpeedChange,
                    valueRange = 0.05f..0.45f
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HardwareToggleButton(
                        label = "Relative Mode",
                        active = isRelativeMode,
                        activeColor = CyanNeon,
                        onClick = { onModeChange(true) }
                    )
                    HardwareToggleButton(
                        label = "Absolute Mode",
                        active = !isRelativeMode,
                        activeColor = CyanNeon,
                        onClick = { onModeChange(false) }
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReconnectMidi) {
                    Text("Reconnect USB", color = AmberGold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = CyanNeon)
                }
            }
        }
    )
}
