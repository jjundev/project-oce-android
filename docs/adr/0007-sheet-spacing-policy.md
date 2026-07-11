# ADR-0007: 공용 시트/스페이싱 폴리시 (Compose 인셋)

## Status
Accepted (2026-07-11, PR D)

## Context
앱은 Compose 단일 Activity + `enableEdgeToEdge()`. View 시스템 `fitsSystemWindows` 는 없다.
시트마다 핸들→제목 갭(24/20/6)·최하단 패딩이 제각각이었고, 3탭 밖 풀스크린 형제는 상태바
밑으로 콘텐츠가 깔렸다.

## Decision
1. 시트 세로 리듬의 단일 소스는 `OceSheetDefaults.contentPadding`
   (top=`sheetHandleGap`=12dp, horizontal=`sheetPadding`=24dp, bottom=`sheetContentBottom`=24dp)
   이며 `OneClickBottomSheet` 프리미티브가 `navigationBarsPadding()` 과 함께 적용한다.
   콜러는 자체 외곽 top/bottom padding 을 두지 않는다. 특수 시트만 `contentPadding` 오버라이드.
2. 3탭 밖 풀스크린 형제 화면 루트는 `Modifier.statusBarsPadding()` 로 상태바 인셋을 통일 적용.
   엣지-고정 하단 CTA(예: Summary 완료 풋터)는 `navigationBarsPadding()` 추가.
3. 3탭 화면은 전역 Scaffold(AppRoot) innerPadding 이 이미 systemBars 를 흡수하므로 제외.

## Exceptions
- `SlimFeedbackSheet`·`DialogueGeneratingScreen.ReadyBottomSheet`: raw/인-컴포지션 커스텀 오버레이
  (Robolectric 별도-윈도 스크린샷 정합 사유). 자체 `navigationBarsPadding()` 유지.
- `DialogueTurnScreen`: 자체 Scaffold + `DialogueHeader(Modifier.statusBarsPadding())` 로 이미 처리.

## Consequences
신규 시트는 프리미티브만 쓰면 자동으로 정합. 신규 풀스크린 형제는 루트 `statusBarsPadding()` 를
잊지 말 것. Robolectric 스크린샷은 프리미티브를 우회 재현하므로 `OceSheetDefaults.contentPadding`
를 재적용해야 한다.
