# 위젯 갱신 및 체크리스트 보호 (2026-09-26)

이전 작업에서는 사용자 요청으로 위젯 현대화를 보류했다.
이번에는 사용자가 위젯 개선을 다시 요청하여 아래 내용을 반영했다.

## 변경

- Android 12/API 31 이상에서는 작은 컬렉션을 `RemoteCollectionItems`로 전달하고
  `updateAppWidget`으로 갱신한다. 이 경로에서는 서비스 어댑터와
  `notifyAppWidgetViewDataChanged`를 사용하지 않는다.
- Android 8–11 및 큰 데이터는 기존 `RemoteViewsService` 경로를 유지한다.
  직접 전달할 컬렉션이 200행을 초과하거나 직렬화 크기가 256KiB를 초과하면
  기존 경로를 선택한다. 항목이나 본문을 잘라내지 않는다.
- 두 경로가 같은 행 렌더러를 사용한다. Android 12 이상의 체크박스 직접 조작,
  이전 Android에서 항목을 눌러 앱을 여는 동작을 유지한다.
- DB 조회와 위젯 갱신은 IO 코루틴에서 수행한다. 수신기는 `goAsync()`로 처리 시간을
  확보하고, 갱신 실패 시에도 `finally`에서 결과를 종료한다. 갱신 요청을 직렬화해
  늦게 완성된 이전 스냅샷이 최신 화면을 덮는 것을 방지한다.
- 체크 상태 변경을 Room 트랜잭션으로 묶어 서로 다른 항목의 동시 체크가 유실되지
  않게 한다. 삭제된 노트, 잘못된 위치, 해당 위치의 본문이 바뀐 오래된 클릭은 무시한다.
  본문 검사는 고유 항목 ID를 대체하지 않으므로 동일한 본문을 가진 중복 항목의
  이동까지 식별하는 것은 아니다. 업데이트 전 위젯의 본문 정보 없는 클릭도 호환한다.
- 체크 응답은 API 31 이상에서 필수 extra가 있는 경우만 받는다.
- API 31 전용 위젯 메타데이터는 `xml-v31/widget.xml`로 분리했다.
- 위젯 설정 화면은 갱신 완료 후 성공을 반환하며 중복 선택과 잘못된 위치를 막는다.

공식 근거: [Android 컬렉션 위젯 안내](https://developer.android.com/develop/ui/views/appwidgets/collections),
[RemoteViews API](https://developer.android.com/reference/android/widget/RemoteViews).

## 검증

JDK 25, 수정된 임시 복사본에서 아래 작업을 수행했다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --offline --console=plain
```

- 전체 테스트 65개 통과, 실패/건너뜀 0개. 위젯 테스트 8개 포함.
- Android 9/17: 삭제된 항목 클릭, 동시 체크 저장, 기존 팩토리의 조회·삭제 후 갱신.
- Android 17: 새 컬렉션의 체크박스 표시, 빈 컬렉션, 큰 데이터의 기존 경로 선택.
- debug 및 최적화된 unsigned release 빌드 통과.
- lint: 제외되지 않은 오류 0개, 경고 56개. 기존 번역 오류 78건만 baseline으로 제외.
  위젯 관련 API/속성 경고 4건은 코드 및 리소스 분기로 해결했다.

기존 경로의 deprecated API 두 곳은 호환 목적이 명확한 작은 함수에만 경고 억제를
붙였다. baseline에 위젯 경고를 추가하거나 앱의 최소 Android 버전을 올리지 않았다.

## 최소 실기 확인 (아직 미실시)

새 빌드로 체크리스트 위젯 하나를 홈 화면에 추가한 뒤 아래만 확인한다.

1. 위젯에서 두 항목을 체크하고 제목을 눌러 앱을 연다. 체크 상태가 저장됐는지 확인한다.
2. 앱에서 항목 하나를 수정·삭제하고 홈 화면으로 돌아와 위젯이 갱신되는지 확인한다.

자동 테스트는 런처의 실제 위젯 호스팅, PendingIntent 전달 및 백그라운드 앱 열기까지
재현하지 않는다. 위 확인은 기존 화면/재생 기능의 실기 검증을 다시 하라는 의미가 아니다.
