package com.goforer.phogal.presentation.ui.compose.screen.home.common.photo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.goforer.base.designsystem.component.loadImagePainter
import com.goforer.base.designsystem.component.shimmer
import com.goforer.phogal.R
import com.goforer.phogal.data.model.remote.response.gallery.common.CurrentUserCollection
import com.goforer.phogal.data.model.remote.response.gallery.common.ProfileImage
import com.goforer.phogal.data.model.remote.response.gallery.common.Social
import com.goforer.phogal.data.model.remote.response.gallery.common.Urls
import com.goforer.phogal.data.model.remote.response.gallery.common.photo.Photo
import com.goforer.phogal.data.model.remote.response.gallery.common.photo.PhotoLinks
import com.goforer.phogal.data.model.remote.response.gallery.common.user.User
import com.goforer.phogal.data.model.remote.response.gallery.common.user.UserLinks
import com.goforer.phogal.presentation.stateholder.uistate.home.common.photo.PhotoItemUiState
import com.goforer.phogal.presentation.stateholder.uistate.home.common.photo.rememberPhotoItemUiState
import com.goforer.phogal.presentation.stateholder.uistate.home.common.user.rememberUserContainerUiState
import com.goforer.phogal.presentation.ui.compose.screen.home.common.user.UserContainer
import com.goforer.phogal.presentation.ui.theme.Blue75
import com.goforer.phogal.presentation.ui.theme.ColorSnowWhite
import com.goforer.phogal.presentation.ui.theme.ColorSystemGray1
import com.goforer.phogal.presentation.ui.theme.ColorSystemGray5
import com.goforer.phogal.presentation.ui.theme.DarkGreen60

@Composable
fun PhotoItem(
    modifier: Modifier = Modifier,
    state: PhotoItemUiState = rememberPhotoItemUiState(),
    onItemClicked: (item: Photo, index: Int) -> Unit,
    onViewPhotos: (name: String, firstName: String, lastName: String, username: String) -> Unit,
    onShowSnackBar: (text: String) -> Unit,
    onOpenWebView: (firstName: String, url: String) -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                state.setClicked(true)
                onItemClicked(state.photo, state.index)
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (state.clicked)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val imageUrl = state.photo.urls.raw
            val painter = loadImagePainter(
                data = imageUrl,
                size = Size(state.photo.width, state.photo.height)
            )

            val transition by animateFloatAsState(
                targetValue = if (painter.state is AsyncImagePainter.State.Success) 1f else 0f,
                animationSpec = tween(durationMillis = 400)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        ratio = if (state.photo.width > 0 && state.photo.height > 0)
                            state.photo.width.toFloat() / state.photo.height.toFloat()
                        else 4f / 3f
                    )
            ) {
                if (painter.state is AsyncImagePainter.State.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .shimmer(
                                durationMillis = 1300,
                                shimmerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f
                            )
                        )
                    )
                } else {
                    Image(
                        painter = painter,
                        contentDescription = "Photo by ${state.photo.user.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(0.95f + (0.05f * transition))
                            .alpha(transition),
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply {
                            setToSaturation(transition)
                        })
                    )

                    if (state.bookmarked) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_bookmark_on),
                                contentDescription = "Bookmarked",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            val userState = rememberUserContainerUiState(
                user = rememberSaveable { mutableStateOf(state.photo.user.toString()) },
                profileSize = rememberSaveable { mutableDoubleStateOf(36.0) },
                colors = remember { mutableStateOf(listOf(ColorSystemGray1, ColorSystemGray1, ColorSnowWhite, ColorSystemGray5, Blue75, DarkGreen60)) },
                visibleViewButton = rememberSaveable { mutableStateOf(state.visibleViewButton) },
                fromItem = rememberSaveable { mutableStateOf(true) }
            )

            UserContainer(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                state = userState,
                onViewPhotos = onViewPhotos,
                onShowSnackBar = onShowSnackBar,
                onOpenWebView = onOpenWebView
            )
        }
    }
}

@Preview(showBackground = true, name = "PhotoItem")
@Composable
fun PhotoItemPreview() {
    PhotoItem(
        modifier = Modifier.fillMaxWidth(),
        state = createMockPhotoItemUiState(isBookmarked = false),
        onItemClicked = { photo, index ->  },
        onViewPhotos = { name, firstName, lastName, username -> },
        onShowSnackBar = { text -> },
        onOpenWebView = { firstName, url -> }
    )
}

@Preview(showBackground = true, name = "BookmarkedPhotoItem")
@Composable
fun PhotoItemBookmarkPreview() {
    PhotoItem(
        state = createMockPhotoItemUiState(isBookmarked = true),
        onItemClicked = { _, _ -> },
        onViewPhotos = { _, _, _, _ -> },
        onShowSnackBar = { _ -> },
        onOpenWebView = { _, _ -> }
    )
}

@Composable
fun createMockPhotoItemUiState(isBookmarked: Boolean): PhotoItemUiState {
    val mockUser = User(
        acceptedTos = true,
        bio = "Android Developer & Photographer based in Seoul.",
        firstName = "John",
        forHire = true,
        id = "2DFFG",
        instagramUsername = "johndoe_insta",
        lastName = "Doe",
        links = UserLinks(
            self = "https://api.unsplash.com/users/lukoh_john",
            html = "https://unsplash.com/@lukoh_john",
            photos = "https://api.unsplash.com/users/lukoh_john/photos"
        ),
        location = "Seoul, South Korea",
        name = "Lukoh",
        portfolioUrl = "https://johndoe.dev",
        profileImage = ProfileImage(
            small = "https://images.unsplash.com/profile-1500000000000-abcdefg?ixlib=rb-4.0.3&crop=faces&fit=crop&w=32&h=32",
            medium = "https://images.unsplash.com/profile-1500000000000-abcdefg?ixlib=rb-4.0.3&crop=faces&fit=crop&w=64&h=64",
            large = "https://images.unsplash.com/profile-1500000000000-abcdefg?ixlib=rb-4.0.3&crop=faces&fit=crop&w=128&h=128"
        ),
        social = Social(
            instagramUsername = "johndoe_insta",
            portfolioUrl = "https://johndoe.dev",
            twitterUsername = "johndoe_twitter",
            paypalEmail = null
        ),
        totalCollections = 5,
        totalLikes = 124,
        totalPhotos = 42,
        twitterUsername = "johndoe_twitter",
        updatedAt = "2026-05-22T10:00:00Z",
        username = "Lukoh Nam"
    )

    val mockPhoto = Photo(
        id = "3DFDS",
        createdAt = "2026-05-22T09:00:00Z",
        updatedAt = "2026-05-22T10:00:00Z",
        width = 360,
        height = 640,
        color = "#607d8b",
        blurHash = "L6PZfXYe00IX.g9Fws%M%~9F_2M{",
        description = "description",
        user = mockUser,
        currentUserCollections = listOf(
            CurrentUserCollection(
                id = 124,
                title = "Water",
                publishedAt = "2026-01-15T08:30:00Z",
                lastCollectedAt = "2026-05-20T14:22:11Z",
                updatedAt = "2026-05-22T10:00:00Z",
                coverPhoto = null,
                user = mockUser
            )
        ),
        urls = Urls(
            raw = "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
            full = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=85",
            regular = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=1080",
            small = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=400",
            thumb = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=200",
        ),
        links = PhotoLinks(
            self = "https://api.unsplash.com/photos/3DFDS",
            html = "https://unsplash.com/photos/3DFDS",
            download = "https://unsplash.com/photos/3DFDS/download",
            downloadLocation = "https://api.unsplash.com/photos/3DFDS/download"
        )
    )

    return rememberMockPhotoItemUiState(
        index = 0,
        photo = mockPhoto,
        visibleViewButton = true,
        clicked = false,
        bookmarked = false
    )
}

@Composable
fun rememberMockPhotoItemUiState(
    index: Int = 0,
    photo: Photo,
    visibleViewButton: Boolean = true,
    clicked: Boolean = false,
    bookmarked: Boolean = false
): PhotoItemUiState {
    return rememberPhotoItemUiState(
        index = remember { mutableIntStateOf(index) },
        photo = remember { mutableStateOf(photo) },
        visibleViewButton = remember { mutableStateOf(visibleViewButton) },
        clicked = remember { mutableStateOf(clicked) },
        bookmarked = remember { mutableStateOf(bookmarked) }
    )
}