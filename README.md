# Phogal_With_Room: SSOT Architecture Migration Guide

> **Project Transition**: `Phogal_Migrate` ➔ `Phogal_With_Room`
> **Applied Principles**: SSOT (Single Source of Truth) via Room + DAO
> **Architecture**: MVVM + UDF (Unidirectional Data Flow)

This document details the architectural evolution from the previous `Phogal_Migrate` project to `Phogal_With_Room`. The primary focus of this upgrade is establishing the **Room Database as the strict Single Source of Truth (SSOT)** for all network-fetched data.

---

## 1. The SSOT Principle (Single Source of Truth)

In the previous version, the data fetched from the REST API was passed down to the `Repository`, cached in-memory within the `ViewModel`, and directly rendered on the `View`. This approach led to data fragmentation, where different screens (e.g., Feed vs. Detail) could hold conflicting versions of the same photo (such as the "Like" status).

**The new SSOT architecture fundamentally changes this:**
* **Network as a Writer Only**: The network no longer feeds data directly to the UI. Instead, API responses are strictly written into the Room database.
* **Database as the Sole Emitter**: The UI components only subscribe to observable streams (`Flow` / `PagingSource`) emitted directly by Room. 
* **Reactive Updates**: Any mutation (e.g., a user toggling a "Like") simply patches the database row. Room automatically detects this change and re-emits the updated data to all active observers, instantly synchronizing the entire app.

---

## 2. Architecture Diagram & Visual Flow

The following architecture diagram illustrates the structural shift and reactive data flow in `Phogal_With_Room`, highlighting the Room Database acting as the central Single Source of Truth (SSOT).

![Phogal Room Architecture](./phogal_room_architecture.png)

### Comparison: Legacy vs. New SSOT Flow
flowchart TD
    subgraph "Legacy (Phogal_Migrate)"
        direction LR
        OldAPI[REST API] --> OldRepo[Repository]
        OldRepo --> OldVM[ViewModel]
        OldVM --> OldView[View]
    end

    subgraph "New SSOT Architecture (Phogal_With_Room)"
        direction LR
        NewAPI[REST API] -- "1. Fetch Data" --> Mediator[RemoteMediator / Repo]
        Mediator -- "2. Write to DB" --> Room[(Room DB
SSOT)]
        
        Room -- "3. Observable Stream
(Flow / PagingSource)" --> NewRepo[Repository]
        NewRepo -- "4. Domain Mapping" --> NewVM[ViewModel]
        NewVM -- "5. StateFlow" --> NewView[Compose UI]
        
        NewView -- "6. User Action
(e.g., Like)" -.-> NewRepo
        NewRepo -. "7. Read-Modify-Write" .-> Room
    end
    
    style Room fill:#4CAF50,stroke:#388E3C,stroke-width:2px,color:white
```

---

## 3. Core Implementation Details

### 3.1 Paged Feeds (Search, Popular, User Photos)
We adopted the **RemoteMediator** pattern from the Paging 3 library to integrate network and local database operations.

1. **Read Path**: The UI collects `LazyPagingItems`, which triggers Paging 3 to read pages exclusively from **Room's `PagingSource`**.
2. **Fetch Path**: When data runs out, `PhotoFeedRemoteMediator.load()` is invoked. It fetches data from the REST API and saves the payload in a single Room transaction.
3. **Reactive Path**: Room automatically invalidates the `PagingSource`, notifying Paging 3 to fetch the new snapshot. The UI updates seamlessly.
4. **Offline Resilience**: The app instantly displays cached records if the network is unavailable. Network errors are safely caught and exposed as `LoadState.Error` without clearing the screen.

### 3.2 Detail Screen Streams
The Detail screen uses multi-emission observable queries through a Kotlin `flow` builder:

```kotlin
override fun getPictureStream(id: String): Flow<NetworkResult<Picture>> = flow {
    // 1. Instantly emit cache if available (Offline-first / Fast-load)
    val cached = pictureDao.getPicture(id)
    cached?.let { emit(NetworkResult.Success(it.picture)) }

    // 2. Refresh from network, but ONLY write the result to the database
    when (val result = safeApiCall { api.getPhoto(id) }) {
        is NetworkResult.Success -> pictureDao.upsert(PictureEntity.of(result.data))
    }

    // 3. Delegate all subsequent emissions to Room's reactive stream
    emitAll(pictureDao.observePicture(id).filterNotNull()
        .map { NetworkResult.Success(it.picture) }.distinctUntilChanged())
}.flowOn(ioDispatcher)
```

---

## 4. Newly Added Components

To support the SSOT transition, the following data layer components were introduced:

| Component Category | Files / Classes | Role |
|---|---|---|
| **Database Setup** | `PhogalDatabase.kt`, `DatabaseModule.kt` | The main Room Database instance (3 Entities, 3 DAOs) and Hilt DI provisions. |
| **Entities (Tables)** | `PhotoFeedEntity.kt`, `PictureEntity.kt`, `RemoteKeyEntity.kt` | Physical table representations. `PhotoFeedEntity` caches feeds, `PictureEntity` caches detail screens, and `RemoteKeyEntity` tracks pagination keys. |
| **Data Access Objects** | `PhotoFeedDao.kt`, `PictureDao.kt`, `RemoteKeyDao.kt` | Provides database operations, observable `Flow`s, and `PagingSource`s. |
| **Pagination** | `PhotoFeedRemoteMediator.kt` | Bridges network paging with local storage, ensuring transactional updates. |
| **Converters** | `PhogalTypeConverters.kt` | `@ProvidedTypeConverter` that utilizes the app's shared JSON configuration to serialize complex objects into Room columns. |

---

## 5. Modifications to Existing Files

Crucially, **no modifications were required in the UI (Compose) layer**. The changes were safely isolated to the Domain and Data layers:

* **Repositories**: 
  * Replaced direct `PagingSource` network calls with DB pagination (`Pager(remoteMediator = ..., pagingSourceFactory = ...)`).
  * Changed return types from one-shot results to `Flow` streams for real-time observation.
* **ViewModels (`PictureViewModel`)**: 
  * Removed manual state patching methods. Refactored to use `flatMapLatest` and `stateIn` for derived state, ensuring UI states are inherently tied to DB state.
* **Dependencies**: 
  * Introduced Room `2.9.0` along with `room-runtime`, `room-ktx`, `room-paging`, and `ksp` compiler in `libs.versions.toml` and `build.gradle`.

---

## 6. Pre-build Checklist

To successfully compile and test `Phogal_With_Room`:

- [ ] **Sync and Build**: Run a full Gradle Sync, then execute `Build > Rebuild Project` to allow KSP to auto-generate the DAOs and `PhogalDatabase_Impl`.
- [ ] **Validate Compatibility**: Ensure `room = "2.9.0"` functions correctly with the current Kotlin and KSP versions defined in the project.
- [ ] **Test Offline State**: Enable Airplane Mode, navigate to the Popular tab (should render cache), enter a Detail screen (should render cache), and attempt a 'Like' (should trigger an error snackbar while preserving cached data).
- [ ] **Clear Data on Logout**: Integrate `PhogalDatabase.clearAllTables()` alongside the existing `DataStore` cleanup to ensure secure account switching.
