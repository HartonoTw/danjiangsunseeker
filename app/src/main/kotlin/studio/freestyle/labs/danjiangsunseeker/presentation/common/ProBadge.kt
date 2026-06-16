package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 尊榮版「PRO」徽章：香檳金漸層膠囊 + 獎章圖示，付費後顯示於各頁右上角。 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFC9971B)),
                ),
                shape = shape,
            )
            .border(0.6.dp, Color(0x4D000000), shape)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = Color(0xFF4A340A),
            modifier = Modifier.size(9.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "PRO",
            color = Color(0xFF4A340A),
            fontWeight = FontWeight.Black,
            fontSize = 8.sp,
            letterSpacing = 0.5.sp,
        )
    }
}
