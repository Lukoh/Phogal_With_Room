package com.goforer.phogal.presentation.ui.compose.screen.home.common.photo

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goforer.base.designsystem.component.LoadingIndicator
import com.goforer.base.designsystem.component.shimmer
import com.goforer.phogal.presentation.ui.theme.ColorSystemGray7
import com.goforer.phogal.presentation.ui.theme.DarkGreen10
import com.goforer.phogal.presentation.ui.theme.PhogalTheme

@Composable
fun LoadingPicture(
    modifier: Modifier = Modifier,
    enableLoadIndicator: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(4.dp))
    ) {
        // Respond to very short viewports by trimming vertical padding on the
        // indicator below.
        val isSmallScreen = maxHeight < 100.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ColorSystemGray7)
                    .shimmer(
                        baseColor = ColorSystemGray7,
                        highlightColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {}
        }

        if (enableLoadIndicator) {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = if (isSmallScreen) 10.dp else 22.dp),
                color = DarkGreen10
            )
        }
    }
}

@Preview(name = "Light Mode")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode",
    showSystemUi = true
)
@Composable
fun LoadingPicturePreview(modifier: Modifier = Modifier) {
    PhogalTheme {
        BoxWithConstraints(
            modifier = modifier.clip(RoundedCornerShape(4.dp))
        ) {
            // Height is a function of width so the preview scales nicely across
            // phone/foldable/tablet previews.
            val dynamicHeight = minOf(maxWidth * 0.7f, 256.dp)

            Column {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dynamicHeight)
                        .background(ColorSystemGray7)
                        .shimmer(
                            baseColor = ColorSystemGray7,
                            highlightColor = MaterialTheme.colorScheme.surface,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {}
            }

            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = DarkGreen10
            )
        }
    }
}
