# Fix Stale Data and Paging Update issues

The user reported that Pull-to-Refresh (swipe) is not correctly updating the paging data in the Popular Photos section. My diagnosis confirms a critical bug: UI state holders for list items are using `rememberSaveable` to store data (like the `Photo` object) but are not updating that state when the underlying data changes from a paging refresh. Because the item keys (based on ID) remain the same after a refresh, the old cached state was being displayed, making it appear as though the update failed.

## Proposed Changes

### UI State Synchronization

Restore the manual state update logic in all paged list sections. This ensures that even if a `rememberSaveable` block is reused for the same index/key, the fresh data from the `LazyPagingItems` is pushed into the state holder.

#### [MODIFY] [PopularPhotosSection.kt](file:///Users/namlukoh/StudioProjects/Phogal_With_Room/app/src/main/java/com/goforer/phogal/presentation/ui/compose/screen/home/popularphotos/PopularPhotosSection.kt)
- Re-add `state.setPhoto(photo)`, `state.setIndex(index)`, and `state.setBookmark(...)` inside the `items` block.

#### [MODIFY] [SearchPhotosSection.kt](file:///Users/namlukoh/StudioProjects/Phogal_With_Room/app/src/main/java/com/goforer/phogal/presentation/ui/compose/screen/home/gallery/SearchPhotosSection.kt)
- Add explicit state updates for the `PhotoItem`.

#### [MODIFY] [UserPhotosSection.kt](file:///Users/namlukoh/StudioProjects/Phogal_With_Room/app/src/main/java/com/goforer/phogal/presentation/ui/compose/screen/home/common/user/userphotos/UserPhotosSection.kt)
- Add explicit state updates for the `PhotoItem`.

#### [MODIFY] [BookmarkedPhotosSection.kt](file:///Users/namlukoh/StudioProjects/Phogal_With_Room/app/src/main/java/com/goforer/phogal/presentation/ui/compose/screen/home/setting/bookmark/BookmarkedPhotosSection.kt)
- Ensure the `PictureItem` state is updated with the latest `Picture` object.

#### [MODIFY] [FollowingUsersSection.kt](file:///Users/namlukoh/StudioProjects/Phogal_With_Room/app/src/main/java/com/goforer/phogal/presentation/ui/compose/screen/home/setting/following/FollowingUsersSection.kt)
- Ensure the `FollowingUsersItem` state is updated with the latest `User` object.

### Robust Refresh Indicator

Refine the `isRefreshing` logic to be more inclusive of mediator load states, ensuring the indicator stays visible throughout the entire network-to-database-to-UI pipeline.

## Verification Plan

### Manual Verification
1. Run the app and navigate to Popular Photos.
2. Perform a pull-to-refresh.
3. Verify that the indicator appears and, more importantly, that the data (e.g., like counts or update timestamps) actually reflects fresh values if they changed on the server.
4. Verify that Search and other paged sections also correctly update their content on refresh.
