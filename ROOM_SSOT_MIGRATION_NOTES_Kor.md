# Room + DAO 기반 SSOT(단일 진실 공급원) 마이그레이션 노트

> 적용일: 2026-08-22 · 아키텍처: **MVVM + UDF(단방향 데이터 흐름) 유지** · DB: Room `phogal.db` v1

## 1. 무엇이 바뀌었나 — 한 문장 요약

기존에는 REST API 응답이 곧바로 Repository → ViewModel → View로 흘렀지만,
이제 **네트워크는 Room(로컬 DB)을 갱신하는 역할만 하고, UI는 오직 Room의 옵저버블 쿼리(Flow / PagingSource)만 구독**합니다.

```
[변경 전]  RestAPI(suspend) ──▶ Repository ──▶ ViewModel(StateFlow) ──▶ View
[변경 후]  RestAPI(suspend) ──▶ Room(SSOT) ──옵저버블 쿼리(Flow)──▶ Repository ──▶ ViewModel(StateFlow) ──▶ View
                                   ▲                                            (UDF 방향 동일, 원천만 DB로 교체)
                    네트워크는 DB의 '작성자'일 뿐 UI에 직접 데이터를 주지 않음
```

이로써 ① 인터넷이 끊겨도 로컬에 저장된 데이터가 즉시 렌더링되고(오프라인 우선),
② 화면·컴포넌트마다 서로 다른 복사본을 들고 있던 문제가 사라지며(SSOT),
③ 좋아요 등 데이터 변경이 DB 한 곳만 갱신하면 구독 중인 모든 화면에 자동 전파됩니다.

## 2. 새로 추가된 파일 (Room 스택)

| 파일 | 역할 |
|---|---|
| `data/datasource/local/room/PhogalDatabase.kt` | RoomDatabase — 엔티티 3종·DAO 3종, SSOT의 물리적 실체 |
| `data/datasource/local/room/entity/PhotoFeedEntity.kt` | 피드(검색/인기/사용자 사진) 1행 = 1장. `feedKey`로 피드 구분, `local_id` 순서 보존, Photo 전체를 JSON 컬럼으로 저장 |
| `data/datasource/local/room/entity/PictureEntity.kt` | 사진 상세(`GET photos/{id}`) 캐시. 사진 id가 PK |
| `data/datasource/local/room/entity/RemoteKeyEntity.kt` | 피드별 페이지네이션 북키핑(next_page, 마지막 새로고침 시각) |
| `data/datasource/local/room/dao/PhotoFeedDao.kt` | **옵저버블 PagingSource** + `Flow<List<...>>` 반환 쿼리 — DB가 바뀌면 Room이 자동 재방출 |
| `data/datasource/local/room/dao/PictureDao.kt` | `observePicture(id): Flow<PictureEntity?>` — 상세 화면의 SSOT 스트림 |
| `data/datasource/local/room/dao/RemoteKeyDao.kt` | RemoteMediator 전용 키 조회/저장 |
| `data/datasource/local/room/mediator/PhotoFeedRemoteMediator.kt` | Paging 3 `RemoteMediator` — REFRESH/APPEND 시 REST 호출 → Room에 트랜잭션으로 기록. 세 피드가 공용으로 사용 |
| `data/datasource/local/room/converter/PhogalTypeConverters.kt` | `@ProvidedTypeConverter` — AppModule의 앱 공용 `Json` 설정 그대로 사용해 Photo/Picture를 JSON 컬럼으로 직렬화 |
| `di/module/DatabaseModule.kt` | Hilt로 DB·DAO 제공. `addTypeConverter(PhogalTypeConverters(json))` |

## 3. 수정된 파일

| 파일 | 변경 내용 |
|---|---|
| `PhotosRepositoryImpl` (검색) | `Pager(remoteMediator = ..., pagingSourceFactory = { dao.pagingSource("search/<query>") })` — 네트워크 PagingSource 제거, DB 페이징으로 전환 |
| `PopularPhotosRepositoryImpl` | 동일 패턴, feedKey = `popular/<orderBy>` — 첫 화면이 비행기 모드에서도 캐시로 즉시 렌더링 |
| `UserPhotosRepositoryImpl` | 동일 패턴, feedKey = `user/<username>` |
| `PictureRepository(+Impl)` | `suspend fun getPicture(): NetworkResult` → **`fun getPictureStream(id): Flow<NetworkResult<Picture>>`**. `flow { }` 빌더로 캐시 즉시 방출 → 네트워크 새로고침은 DB에만 기록 → `emitAll(dao.observePicture(id))`로 스트림을 Room에 인계 |
| `PictureLikeRepositoryImpl` | 좋아요/취소 성공 시 Room의 상세 행을 read-modify-write로 패치 → DAO Flow가 재방출되어 구독 화면 자동 갱신 |
| `PictureViewModel` | 일회성 요청 + 수동 상태 패치 제거. `loadRequest.flatMapLatest { repo.getPictureStream(...) }.stateIn(...)` 파생 상태로 전환. 공개 API(`picture`, `loadPicture`, `toggleLike`, `likeActionState`, `events`)는 그대로 유지 → **UI(Compose) 코드는 무변경** |
| `gradle/libs.versions.toml` / `app/build.gradle` | `room = "2.9.0"` + room-runtime / room-ktx / room-paging / ksp room-compiler 추가 |

삭제(대체)된 파일: `PhotosPagingSource.kt`, `PopularPhotosPagingSource.kt`, `UserPhotosPagingSource.kt`
— 네트워크 직접 페이징이 RemoteMediator + Room PagingSource로 대체되었습니다. 원본은 프로젝트 루트 `_removed_paging_sources/`에 보관했습니다.

## 4. 동작 방식 상세

### 4.1 페이징 피드 (검색 · 인기 · 사용자 사진) — RemoteMediator 패턴

1. UI가 `LazyPagingItems`를 수집하면 Paging 3가 **Room의 PagingSource**에서 페이지를 읽습니다.
2. 데이터가 부족하거나 새로고침이 필요하면 Paging 3가 `PhotoFeedRemoteMediator.load()`를 호출합니다.
3. Mediator가 해당 `RestAPI` suspend 함수를 호출하고, 결과를 **하나의 Room 트랜잭션**으로 기록합니다
   (REFRESH = 피드 클리어 + 교체, APPEND = 뒤에 추가 + next_page 갱신).
4. Room이 PagingSource를 무효화 → Paging 3가 DB에서 새 스냅샷을 읽어 UI가 갱신됩니다.
5. **오프라인**: `initialize()`가 캐시 신선도(30분)를 판정해 신선하면 네트워크를 아예 건너뛰고,
   네트워크 실패 시에는 캐시 행이 그대로 화면에 남고 실패는 `LoadState.Error`로 표시됩니다(기존 에러 UI 재사용).

### 4.2 상세 화면 (Picture) — flow 빌더 + DAO 옵저버블 쿼리

```kotlin
override fun getPictureStream(id: String): Flow<NetworkResult<Picture>> = flow {
    val cached = pictureDao.getPicture(id)
    cached?.let { emit(NetworkResult.Success(it.picture)) }   // ① 캐시 즉시 표시
    when (val result = safeApiCall { api.getPhoto(id) }) {     // ② 새로고침은 DB에만 기록
        is NetworkResult.Success -> pictureDao.upsert(PictureEntity.of(result.data))
        /* 실패는 캐시가 없을 때만 방출 — 오프라인이면 캐시가 이김 */
    }
    emitAll(pictureDao.observePicture(id).filterNotNull()      // ③ 이후엔 Room이 유일한 방출원
        .map { NetworkResult.Success(it.picture) }.distinctUntilChanged())
}.flowOn(ioDispatcher)
```

좋아요를 누르면 `PictureLikeRepositoryImpl`이 REST 성공 후 **DB 행만 패치**합니다.
③의 옵저버블 쿼리가 변경을 감지해 재방출하므로, ViewModel이 메모리 상태를 손으로 고칠 필요가 없어졌습니다.

### 4.3 유지된 것들

- **MVVM + UDF**: View → intent → Repository → (Room) → Flow → StateFlow → View 의 단방향 흐름 그대로.
- **ViewModel 공개 API·Compose UI**: 시그니처 불변 → 화면 코드 수정 0건.
- **DataStore(LocalDataSource)**: 북마크·팔로잉·검색어·알림 설정은 이미 로컬이 원천(SSOT)이므로 유지.
  REST가 원천이던 데이터만 Room으로 이관했습니다.
- **safeApiCall / NetworkResult / UiState 체계**: 그대로 재사용.

## 5. 확인 필요 사항 (빌드 전 체크리스트)

- [ ] `room = "2.9.0"` 버전이 프로젝트의 Kotlin/KSP 조합과 맞는지 확인 (문제 시 Room 안정 최신으로 조정).
- [ ] 최초 빌드 시 KSP가 DAO 구현을 생성하는지 확인 (`app/build/generated/ksp`).
- [ ] 비행기 모드 테스트: 인기 탭 진입(캐시 렌더링) → 상세 진입(캐시 렌더링) → 좋아요(실패 스낵바, 캐시 유지).
- [ ] 참고: `LocalDataSource.logOut()`은 DataStore만 초기화합니다. 로그아웃 시 Room도 비우려면
  `PhogalDatabase.clearAllTables()` 호출을 추가하세요(현재 logOut은 호출처 없음).
- [ ] 캐시 증가 관리: 피드 캐시는 feedKey 단위로 쌓입니다. 필요 시 주기적 프루닝(오래된 feedKey 삭제)을
  `cached_at` 기준으로 추가할 수 있습니다.

## 6. 정적 검증 결과 (2026-08-23)

클라우드 작업 환경에서는 보안 정책상 maven.google.com / gradle.org 접근이 차단되어 실제 Gradle 빌드는
Android Studio에서 수행해야 합니다. 대신 아래 정적 검증을 전체 소스(200개 .kt)에 대해 수행했습니다.

| 검증 항목 | 결과 |
|---|---|
| Kotlin 구문 파싱 (전 200개 파일, kopyt 파서) | **오류 0건** |
| 프로젝트 내부 import 참조 해석 (com.goforer.*) | **미해결 0건** — R / BuildConfig 45건은 빌드 시 자동 생성 클래스로 정상 |
| 동일 패키지 내 클래스명 중복(컴파일 충돌) | **0건** |
| build.gradle에서 참조하는 version catalog alias 존재 여부 | **전건 일치** (room 4종 포함) |
| PhogalDatabase 선언 일관성 (엔티티 3·DAO 3·TypeConverters) | **일치** |
| 삭제된 PagingSource 3종에 대한 잔여 참조 | **0건** |
| Room/Paging/Hilt API 시그니처 수동 검토 (RemoteMediator, withTransaction, addTypeConverter, Pager(remoteMediator=...)) | **표준 시그니처와 일치** |

> 소스 트리는 완전합니다 — 이전에 전달드린 tar.gz는 "변경분만" 담은 패치 묶음이었고,
> 프로젝트 폴더에는 기존 데이터 모델(Photo, Picture, User, Urls 등 40여 개 클래스)을 포함한
> 전체 소스가 그대로 있습니다. 함께 제공되는 `Phogal_Room_SSOT_FullSource.zip`이
> Room 적용이 완료된 **전체 소스 1벌**입니다.

### Android Studio에서 첫 빌드 절차
1. 프로젝트 열기 → Gradle Sync (room 2.9.0 아티팩트 4종이 내려받아집니다)
2. Build > Rebuild Project — KSP가 `PhogalDatabase_Impl`, DAO 구현, Hilt 컴포넌트를 생성
3. 에러 발생 시 가장 먼저 확인: ①room 버전-Kotlin/KSP 호환(필요 시 room 버전만 조정) ②`google-services.json` 유효성
