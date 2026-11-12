package app.woorichadiary.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.woorichadiary.DiaryController
import app.woorichadiary.UiState
import app.woorichadiary.core.CarAction
import app.woorichadiary.core.CarEvent
import app.woorichadiary.core.DiaryEngine
import app.woorichadiary.core.Family
import app.woorichadiary.core.Seat

private val Paper = Color(0xFFFFF8EC)
private val Ink = Color(0xFF2E2A25)
private val CarBody = Color(0xFFF2B84B)
private val Accent = Color(0xFFD9573F)
private val Soft = Color(0xFFF3E7D2)

/** The Pleos shell draws its status strip and climate bar over the app without reporting insets. */
private val ShellPadding = Modifier.padding(top = 96.dp, bottom = 140.dp)

@Composable
fun DrivingScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black))
}

@Composable
fun SetupScreen(onDone: (Family) -> Unit) {
    var carName by rememberSaveable { mutableStateOf("") }
    var childName by rememberSaveable { mutableStateOf("") }
    var seat by rememberSaveable { mutableStateOf(Seat.REAR_LEFT) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val ready = carName.isNotBlank() && childName.isNotBlank() && consent

    Row(
        Modifier.fillMaxSize().background(Paper).then(ShellPadding).padding(horizontal = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarFace(CarAction.Kind.TALK, Modifier.size(520.dp).weight(1f))
        Spacer(Modifier.width(72.dp))
        Column(
            Modifier.weight(1.2f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text("우리 차에게 이름을 지어 주세요", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Ink)
            BigField("차 이름", carName) { carName = it }
            BigField("아이 이름", childName) { childName = it }
            Text("아이가 앉는 자리", fontSize = 28.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Seat.REAR_LEFT, Seat.REAR_RIGHT).forEach { option ->
                    FilterChip(
                        selected = seat == option,
                        onClick = { seat = option },
                        label = { Text(option.label, fontSize = 28.sp, modifier = Modifier.padding(16.dp)) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { consent = !consent },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = consent, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Text(
                    "보호자로서 아이 이름과 차량 기록을 이 차 안에 저장하는 데 동의합니다. 언제든 삭제할 수 있습니다.",
                    fontSize = 24.sp,
                    lineHeight = 34.sp,
                    color = Ink,
                )
            }
            Button(
                enabled = ready,
                onClick = { onDone(Family(carName.trim(), mapOf(seat to childName.trim()), consent)) },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) { Text("시작하기", fontSize = 32.sp) }
        }
    }
}

@Composable
private fun BigField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 24.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 32.sp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: UiState, controller: DiaryController) {
    var demoOpen by remember { mutableStateOf(false) }
    val family = state.family ?: return

    Column(Modifier.fillMaxSize().background(Paper).then(ShellPadding).padding(horizontal = 56.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${family.carName}의 일기", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = controller::openPhotoPage) { Text("사진 고르기", fontSize = 24.sp) }
            TextButton(onClick = { demoOpen = !demoOpen }) { Text("데모", fontSize = 24.sp) }
            TextButton(onClick = controller::eraseAll) { Text("기록 삭제", fontSize = 24.sp) }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            CarFace(state.kind, Modifier.size(480.dp).weight(1f))
            Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Bubble(state.line ?: "${family.carName}가 기다리고 있어요")
                if (state.choices.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        state.choices.forEach { choice ->
                            Button(
                                onClick = { controller.answer(choice) },
                                modifier = Modifier.height(96.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            ) { Text(choice, fontSize = 32.sp) }
                        }
                    }
                }
                state.parentNotice?.let { notice ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink).padding(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("보호자 안내  $notice", color = Color.White, fontSize = 28.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = controller::dismissNotice) { Text("확인", fontSize = 26.sp, color = CarBody) }
                    }
                }
                val today = DiaryEngine.lines(state.today)
                if (state.photos.isNotEmpty()) {
                    PhotoStrip(state.photos)
                } else if (today.isNotEmpty()) {
                    Text("오늘의 일기", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                    today.forEach { Text(it, fontSize = 28.sp, color = Ink) }
                }
                if (state.unavailableSignals.isNotEmpty()) {
                    Text("일부 차량 신호를 읽을 수 없어 수동 입력을 사용해요", fontSize = 22.sp, color = Accent)
                }
            }
        }
        val rider = family.riders.keys.firstOrNull() ?: Seat.REAR_LEFT
        OutlinedButton(onClick = { controller.inject(CarEvent.BeltChanged(rider, true)) }) {
            Text("벨트 맸어요", fontSize = 26.sp)
        }
        if (demoOpen) DemoPanel(rider, controller)
    }
    state.photoPageUrl?.let { url -> PhotoPairing(url, controller::closePhotoPage) }
}

@Composable
private fun PhotoStrip(photos: List<java.io.File>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        photos.forEach { file ->
            val bitmap = remember(file) { decodeThumbnail(file) }
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "가족 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(24.dp)),
                )
            }
        }
    }
}

private fun decodeThumbnail(file: java.io.File): Bitmap? =
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })

@Composable
private fun PhotoPairing(url: String, onClose: () -> Unit) {
    val qr = remember(url) { qrBitmap(url) }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC2E2A25)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(36.dp)).background(Color.White).padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Image(qr.asImageBitmap(), contentDescription = "사진 고르기 QR", modifier = Modifier.size(420.dp))
            Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("보호자 폰으로 찍어 주세요", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("나들이 사진 중 7장을 골라 드려요. 승인한 사진만 차로 옮겨집니다.", fontSize = 28.sp, lineHeight = 40.sp, color = Ink)
                Text(url, fontSize = 20.sp, color = Ink.copy(alpha = 0.6f))
                Button(
                    onClick = onClose,
                    modifier = Modifier.height(88.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("닫기", fontSize = 28.sp) }
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int = 840): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}

/** Generates the same events as the vehicle, for presentations and signals the console lacks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoPanel(rider: Seat, controller: DiaryController) {
    var km by remember { mutableStateOf(100.0) }
    FlowRow(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Soft).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Demo("탑승") { controller.inject(CarEvent.SeatChanged(rider, true)) }
        Demo("하차") { controller.inject(CarEvent.SeatChanged(rider, false)) }
        Demo("벨트 풀기") { controller.inject(CarEvent.BeltChanged(rider, false)) }
        Demo("비") { controller.inject(CarEvent.WiperChanged(true)) }
        Demo("12km 주행") { km += 12; controller.inject(CarEvent.OdometerChanged(km - 12)); controller.inject(CarEvent.DrivingChanged(true)); controller.inject(CarEvent.OdometerChanged(km)); controller.inject(CarEvent.DrivingChanged(false)) }
        Demo("배터리 18%") { controller.inject(CarEvent.BatteryChanged(18)) }
        Demo("충전 80%") { controller.inject(CarEvent.BatteryChanged(80)) }
    }
}

@Composable
private fun Demo(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label, fontSize = 22.sp) }
}

@Composable
private fun Bubble(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Color.White).padding(44.dp)) {
        Text(text, fontSize = 44.sp, lineHeight = 60.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}

private val CarBodyDark = Color(0xFFDDA03A)
private val Glass = Color(0xFFCDE8F2)

/**
 * An original friendly car seen from the front: headlights are its eyes, the grille its mouth.
 * Expression follows what the car is doing. Drawn in a centered square so it never stretches.
 */
@Composable
private fun CarFace(kind: CarAction.Kind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + s * x, oy + s * y)
        fun sz(x: Float, y: Float) = Size(s * x, s * y)
        fun r(v: Float) = CornerRadius(s * v)
        val line = Stroke(width = s * 0.018f, cap = StrokeCap.Round)

        // Ground shadow and tires peeking out under the body.
        drawOval(Ink, topLeft = p(0.12f, 0.86f), size = sz(0.76f, 0.06f), alpha = 0.12f)
        listOf(0.15f, 0.71f).forEach { x ->
            drawRoundRect(Ink, topLeft = p(x, 0.72f), size = sz(0.14f, 0.17f), cornerRadius = r(0.04f))
        }

        // Antenna with a little heart-coloured bobble.
        drawLine(Ink, p(0.64f, 0.17f), p(0.68f, 0.06f), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
        drawCircle(Accent, radius = s * 0.028f, center = p(0.68f, 0.06f))

        // Side mirrors.
        listOf(0.03f, 0.87f).forEach { x ->
            drawRoundRect(CarBodyDark, topLeft = p(x, 0.38f), size = sz(0.10f, 0.07f), cornerRadius = r(0.03f))
        }

        // Cabin (a soft trapezoid) and windshield.
        val cabin = Path().apply {
            moveTo(ox + s * 0.30f, oy + s * 0.15f)
            lineTo(ox + s * 0.70f, oy + s * 0.15f)
            quadraticBezierTo(ox + s * 0.78f, oy + s * 0.15f, ox + s * 0.80f, oy + s * 0.23f)
            lineTo(ox + s * 0.86f, oy + s * 0.46f)
            lineTo(ox + s * 0.14f, oy + s * 0.46f)
            lineTo(ox + s * 0.20f, oy + s * 0.23f)
            quadraticBezierTo(ox + s * 0.22f, oy + s * 0.15f, ox + s * 0.30f, oy + s * 0.15f)
            close()
        }
        drawPath(cabin, CarBody)
        val glass = Path().apply {
            moveTo(ox + s * 0.31f, oy + s * 0.21f)
            lineTo(ox + s * 0.69f, oy + s * 0.21f)
            lineTo(ox + s * 0.76f, oy + s * 0.42f)
            lineTo(ox + s * 0.24f, oy + s * 0.42f)
            close()
        }
        drawPath(glass, Glass)
        drawLine(Color.White, p(0.36f, 0.38f), p(0.44f, 0.24f), strokeWidth = s * 0.02f, cap = StrokeCap.Round, alpha = 0.8f)
        drawLine(Color.White, p(0.43f, 0.38f), p(0.47f, 0.31f), strokeWidth = s * 0.012f, cap = StrokeCap.Round, alpha = 0.6f)

        // Body and bumper with a small number plate.
        drawRoundRect(CarBody, topLeft = p(0.07f, 0.40f), size = sz(0.86f, 0.40f), cornerRadius = r(0.11f))
        drawRoundRect(CarBodyDark, topLeft = p(0.12f, 0.71f), size = sz(0.76f, 0.09f), cornerRadius = r(0.045f))
        drawRoundRect(Color.White, topLeft = p(0.39f, 0.725f), size = sz(0.22f, 0.06f), cornerRadius = r(0.015f))
        drawCircle(Accent, radius = s * 0.011f, center = p(0.49f, 0.748f))
        drawCircle(Accent, radius = s * 0.011f, center = p(0.51f, 0.748f))
        drawPath(
            Path().apply {
                moveTo(ox + s * 0.479f, oy + s * 0.752f)
                lineTo(ox + s * 0.521f, oy + s * 0.752f)
                lineTo(ox + s * 0.50f, oy + s * 0.772f)
                close()
            },
            Accent,
        )

        // Turn signals.
        listOf(0.11f, 0.84f).forEach { x ->
            drawRoundRect(Color(0xFFFF9E57), topLeft = p(x, 0.49f), size = sz(0.05f, 0.03f), cornerRadius = r(0.015f))
        }

        // Cheeks: always a little rosy, brighter during a hug.
        val blush = if (kind == CarAction.Kind.HUG) 0.65f else 0.3f
        listOf(0.17f, 0.83f).forEach { x ->
            drawOval(Accent, topLeft = p(x - 0.045f, 0.62f), size = sz(0.09f, 0.05f), alpha = blush)
        }

        // Headlight eyes.
        listOf(0.30f, 0.70f).forEach { x ->
            val c = p(x, 0.55f)
            when (kind) {
                CarAction.Kind.HUG -> drawArc(
                    Ink, 200f, 140f, false,
                    topLeft = p(x - 0.07f, 0.52f), size = sz(0.14f, 0.11f), style = line,
                )
                else -> {
                    drawCircle(Color.White, radius = s * 0.085f, center = c)
                    drawCircle(Ink, radius = s * 0.085f, center = c, style = Stroke(width = s * 0.008f), alpha = 0.25f)
                    drawCircle(Ink, radius = s * 0.045f, center = p(x, 0.565f))
                    drawCircle(Color.White, radius = s * 0.016f, center = p(x - 0.016f, 0.548f))
                    if (kind == CarAction.Kind.CARE) {
                        // Heavy eyelids: tired and hungry.
                        drawArc(CarBody, 180f, 180f, true, topLeft = p(x - 0.09f, 0.46f), size = sz(0.18f, 0.18f))
                        drawLine(Ink, p(x - 0.085f, 0.55f), p(x + 0.085f, 0.55f), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
                    }
                }
            }
        }

        // Grille mouth.
        when (kind) {
            CarAction.Kind.HUG -> {
                drawArc(Ink, 0f, 180f, true, topLeft = p(0.42f, 0.58f), size = sz(0.16f, 0.12f))
                drawArc(Accent, 0f, 180f, true, topLeft = p(0.46f, 0.63f), size = sz(0.08f, 0.06f))
            }
            CarAction.Kind.CARE -> {
                drawArc(Ink, 200f, 140f, false, topLeft = p(0.44f, 0.64f), size = sz(0.12f, 0.06f), style = line)
                // A worried sweat drop.
                drawCircle(Glass, radius = s * 0.022f, center = p(0.86f, 0.33f))
                drawPath(
                    Path().apply {
                        moveTo(ox + s * 0.839f, oy + s * 0.325f)
                        lineTo(ox + s * 0.86f, oy + s * 0.28f)
                        lineTo(ox + s * 0.881f, oy + s * 0.325f)
                        close()
                    },
                    Glass,
                )
            }
            else -> drawArc(Ink, 20f, 140f, false, topLeft = p(0.40f, 0.56f), size = sz(0.20f, 0.11f), style = line)
        }
    }
}
