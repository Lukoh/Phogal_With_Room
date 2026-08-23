package com.goforer.phogal.presentation.ui.compose.screen.home.gallery

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
fun LoadingPhotos(
    modifier: Modifier = Modifier,
    count: Int,
    enableLoadIndicator: Boolean = false
) {
    BoxWithConstraints(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        // Scale card size with the available width so this looks correct on
        // foldables and tablets as well.
        val isWideScreen = maxWidth > 600.dp
        val cardHeight = if (isWideScreen) 320.dp else 256.dp
        val horizontalPadding = if (isWideScreen) 16.dp else 0.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        ) {
            repeat(count) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .shimmer(
                            baseColor = ColorSystemGray7,
                            highlightColor = MaterialTheme.colorScheme.surface,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    // Empty card body; the shimmer is the content.
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (enableLoadIndicator) {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(vertical = 22.dp),
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
fun LoadingPhotosPreview(modifier: Modifier = Modifier) {
    PhogalTheme {
        BoxWithConstraints(
            modifier = modifier.clip(RoundedCornerShape(4.dp))
        ) {
            val isWideScreen = maxWidth > 600.dp
            val cardHeight = if (isWideScreen) 320.dp else 256.dp

            Column {
                repeat(3) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight)
                            .background(ColorSystemGray7)
                            .shimmer(
                                baseColor = ColorSystemGray7,
                                highlightColor = MaterialTheme.colorScheme.surface,
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {}
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = DarkGreen10
            )
        }
    }
}
