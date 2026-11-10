package ge.gela.wamali

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** სრული ეკრანი, რომელიც ჩაკეტილ ეკრანზეც ჩნდება, როცა წამლის დროა */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MedStore.init(this)
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    private fun show(i: Intent) {
        val medId = i.getStringExtra(Alarms.EXTRA_MED)
        val m = medId?.let { MedStore.med(it) }
        if (medId == null || m == null) { finish(); return }
        val at = i.getLongExtra(Alarms.EXTRA_AT, System.currentTimeMillis())
        setContent {
            WamaliTheme {
                RingingScreen(
                    m, at,
                    onTaken = { Alarms.taken(this, medId, at); finish() },
                    onSnooze = { Alarms.snoozeAction(this, medId, at); finish() },
                    onSkip = { Alarms.skip(this, medId, at); finish() }
                )
            }
        }
    }
}

@Composable
fun RingingScreen(m: Medication, at: Long, onTaken: () -> Unit, onSnooze: () -> Unit, onSkip: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale"
    )
    val bmp = remember(m.photo) { MedStore.loadPhoto(m)?.asImageBitmap() }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Teal, Color(0xFF0D3F4D))))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text("წამლის დროა", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(260.dp).scale(pulse).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            Box(
                Modifier.size(210.dp)
                    .clip(RoundedCornerShape(52.dp))
                    .background(TealLight)
                    .border(4.dp, Color.White, RoundedCornerShape(52.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("💊", fontSize = 90.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(m.name, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (m.dose.isNotBlank()) Text(m.dose, color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp)
        if (m.note.isNotBlank()) Text("ℹ ${m.note}", color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp)

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onTaken,
            modifier = Modifier.fillMaxWidth().height(66.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) { Text("✓  მივიღე", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSnooze,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) { Text("⏰ 5 წუთში", fontSize = 17.sp) }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
            ) { Text("გამოტოვება", color = Color.White, fontSize = 17.sp) }
        }
    }
}
