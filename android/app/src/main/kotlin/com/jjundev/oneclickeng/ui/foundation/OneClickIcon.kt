package com.jjundev.oneclickeng.ui.foundation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.R
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 시맨틱 아이콘 seam. `OceIcon` 시맨틱 이름 → Material Symbols(Rounded) vector 를 순수 매핑한다.
 * name→glyph 매핑은 [OceIcon] 에만 존재하고, 이 컴포저블은 분기하지 않는다(01a-icon-mapping.md #2).
 *
 * 접근성(01a #7 · 06-accessibility-impl A2): 아이콘은 기본적으로 장식이며, 라벨/stateDescription 은
 * **부모 컨트롤**이 보유한다. 이를 강제하기 위해 [contentDescription] 은 **기본값이 없다**(M3 `Icon`
 * 시그니처와 동일). 호출부는 매번 명시적으로 선택해야 한다:
 *  - 장식(부모가 라벨 보유) → `contentDescription = null`
 *  - 아이콘 자체가 라벨을 가질 때 → 문자열 전달
 * 이렇게 하면 null 이 숨은 기본이 아니라 lint 가시 선택이 되어 M0 contentDescription lint gate
 * (06-accessibility-impl:152)를 통과한다.
 *
 * fill 상태(저장 토글·BottomNav 활성 등, A2 비색 신호)는 이 seam 이 분기하지 않고 **부모가 [OceIcon]
 * 상수를 교체**해 표현한다(런타임 FILL 축 없음). 색은 [tint](=`LocalContentColor`)가 상속한다.
 */
@Composable
fun OneClickIcon(
    icon: OceIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = OceIconSize.Default,
) {
    Icon(
        painter = painterResource(icon.res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * 아이콘 크기 토큰. 기본 24dp, 예외 크기는 하드코딩 대신 여기서 소유한다(01a #6).
 * 매핑표 §A 의 예외 사용처(ListRow 22 · Feedback 16 · Input 15)를 명명 상수로 고정한다.
 */
object OceIconSize {
    val Default: Dp = 24.dp

    /** 빈 상태(C5) 중앙 일러스트 아이콘 (02-shared-components.md:65 `96dp`). */
    val EmptyState: Dp = 96.dp

    /** ListRow disclosure chevron (01a §A `chevron_right` size 22). */
    val ListDisclosure: Dp = 22.dp

    /** FeedbackSection 인라인 아이콘 (01a §A `error` sz16). */
    val FeedbackInline: Dp = 16.dp

    /** Input 인라인 아이콘 (01a §A `error` · Input sz15). */
    val InputInline: Dp = 15.dp
}

/**
 * 시맨틱 glyph ↔ Material Symbols(Rounded) vector 매핑. drawable 과 1:1(총 45 상수).
 * nav 3종(forum·history·settings)과 bookmark 는 fill 상태별로 별도 상수를 가진다(01a #4·#5).
 * 상황(Topic) 카탈로그 13종은 이모지→벡터 전환분(M0 정합)이다.
 */
enum class OceIcon(
    @DrawableRes val res: Int,
) {
    ChevronRight(R.drawable.ic_chevron_right),
    ExpandMore(R.drawable.ic_expand_more),
    ArrowBack(R.drawable.ic_arrow_back),
    Search(R.drawable.ic_search),
    Close(R.drawable.ic_close),
    Error(R.drawable.ic_error),
    EditNote(R.drawable.ic_edit_note),
    Spellcheck(R.drawable.ic_spellcheck),
    AutoAwesome(R.drawable.ic_auto_awesome),
    FormatPaint(R.drawable.ic_format_paint),
    Hub(R.drawable.ic_hub),
    Bookmark(R.drawable.ic_bookmark),
    BookmarkBorder(R.drawable.ic_bookmark_border),
    Delete(R.drawable.ic_delete),
    ContentCopy(R.drawable.ic_content_copy),
    LocalFireDepartment(R.drawable.ic_local_fire_department),
    Schedule(R.drawable.ic_schedule),
    Bolt(R.drawable.ic_bolt),
    MatchWord(R.drawable.ic_match_word),
    Notes(R.drawable.ic_notes),
    FormatQuote(R.drawable.ic_format_quote),
    Mic(R.drawable.ic_mic),
    Keyboard(R.drawable.ic_keyboard),
    Autorenew(R.drawable.ic_autorenew),
    Refresh(R.drawable.ic_refresh),
    Send(R.drawable.ic_send),
    Tune(R.drawable.ic_tune),
    Lock(R.drawable.ic_lock),
    Check(R.drawable.ic_check),
    CheckCircle(R.drawable.ic_check_circle),
    VolumeUp(R.drawable.ic_volume_up),
    GraphicEq(R.drawable.ic_graphic_eq),
    Speed(R.drawable.ic_speed),
    Notifications(R.drawable.ic_notifications),
    CloudOff(R.drawable.ic_cloud_off),
    AccountCircle(R.drawable.ic_account_circle),
    HourglassEmpty(R.drawable.ic_hourglass_empty),
    SentimentSatisfied(R.drawable.ic_sentiment_satisfied),
    SentimentNeutral(R.drawable.ic_sentiment_neutral),
    SentimentVeryDissatisfied(R.drawable.ic_sentiment_very_dissatisfied),
    PlayArrow(R.drawable.ic_play_arrow),
    GridView(R.drawable.ic_grid_view),
    ViewAgenda(R.drawable.ic_view_agenda),
    PartlyCloudyDay(R.drawable.ic_partly_cloudy_day),
    WavingHand(R.drawable.ic_waving_hand),
    Event(R.drawable.ic_event),
    Hotel(R.drawable.ic_hotel),
    // 상황(Topic) 카탈로그 아이콘(M0 정합) — 이모지 → Material 심볼 벡터 전환. TopicCatalog.icon 매핑 참조.
    LocalCafe(R.drawable.ic_local_cafe),
    Interests(R.drawable.ic_interests),
    Restaurant(R.drawable.ic_restaurant),
    ShoppingCart(R.drawable.ic_shopping_cart),
    Flight(R.drawable.ic_flight),
    LocalTaxi(R.drawable.ic_local_taxi),
    Directions(R.drawable.ic_directions),
    BusinessCenter(R.drawable.ic_business_center),
    Work(R.drawable.ic_work),
    LocalHospital(R.drawable.ic_local_hospital),
    Medication(R.drawable.ic_medication),
    AccountBalance(R.drawable.ic_account_balance),
    Call(R.drawable.ic_call),
    CleaningServices(R.drawable.ic_cleaning_services),
    RestartAlt(R.drawable.ic_restart_alt),
    CloudSync(R.drawable.ic_cloud_sync),
    SyncProblem(R.drawable.ic_sync_problem),
    DeleteForever(R.drawable.ic_delete_forever),
    OpenInNew(R.drawable.ic_open_in_new),
    Info(R.drawable.ic_info),
    Shield(R.drawable.ic_shield),
    Description(R.drawable.ic_description),
    Logout(R.drawable.ic_logout),
    Person(R.drawable.ic_person),
    NavForum(R.drawable.ic_forum_fill0),
    NavForumFilled(R.drawable.ic_forum_fill1),
    NavHistory(R.drawable.ic_history_fill0),
    NavHistoryFilled(R.drawable.ic_history_fill1),
    NavSettings(R.drawable.ic_settings_fill0),
    NavSettingsFilled(R.drawable.ic_settings_fill1),
}

/**
 * 아이콘 카탈로그 프리뷰(수용 기준: 29 렌더 · seam 이 BLANK 아님 확인). 매핑표 대조 체크리스트 용.
 */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun OceIconCatalogPreview() {
    OceTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OceIcon.entries.chunked(4).forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowIcons.forEach { icon ->
                        Column(
                            modifier = Modifier.width(80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OneClickIcon(icon = icon, contentDescription = null)
                            Text(
                                text = icon.name,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
