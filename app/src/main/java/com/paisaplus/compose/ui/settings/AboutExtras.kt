package com.paisaplus.compose.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paisaplus.compose.R
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.components.LinkRow
import com.paisaplus.compose.ui.components.ListGroup
import com.paisaplus.compose.ui.components.TintCard
import kotlinx.coroutines.delay

private const val RICKROLL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
private const val TELEGRAM = "https://t.me/maxwantstohangout"

/** Plain-words privacy policy: nothing leaves the phone. */
@Composable
fun PrivacyPolicySection() {
    val points = listOf(R.string.pp_1, R.string.pp_2, R.string.pp_3, R.string.pp_4, R.string.pp_5)
    TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(str(R.string.pp_title), style = MaterialTheme.typography.titleLarge)
            points.forEach { Text(str(it), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** "Donate" shows a few memes, then opens the rickroll once. */
@Composable
fun CoffeeSection() {
    val uri = LocalUriHandler.current
    val memes = listOf(R.string.meme_1, R.string.meme_2, R.string.meme_3, R.string.meme_4)
    var step by rememberSaveable { mutableIntStateOf(-1) } // -1 = idle, else the meme showing; past the last = rolled

    LaunchedEffect(step) {
        if (step < 0) return@LaunchedEffect
        delay(8500)
        if (step < memes.size) step++ else { uri.openUri(RICKROLL); step = -1 }
    }

    Column {
    TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedContent(step, label = "meme") { s ->
                Text(
                    when {
                        s < 0 -> str(R.string.coffee_intro)
                        s < memes.size -> str(memes[s])
                        else -> str(R.string.coffee_last)
                    },
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                )
            }
            Button({ if (step < 0) step = 0 }, enabled = step < 0) {
                Icon(Icons.Rounded.Coffee, null, Modifier.padding(end = 8.dp))
                Text(str(R.string.coffee_button))
            }
        }
    }
    ListGroup(Modifier.padding(top = 12.dp)) {
        item { LinkRow(Icons.AutoMirrored.Rounded.Send, 4, str(R.string.telegram), "@maxwantstohangout") { uri.openUri(TELEGRAM) } }
    }
    }
}
