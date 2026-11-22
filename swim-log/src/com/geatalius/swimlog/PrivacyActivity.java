package com.geatalius.swimlog;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** 앱 정보 및 Health Connect 데이터 사용 안내. */
public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui ui = new Ui(this);
        ui.styleWindow(this);
        LinearLayout root = ui.v();
        root.setBackgroundColor(ui.bg);
        root.addView(ui.topBar(this, "앱 정보 · 개인정보"));
        ScrollView sv = new ScrollView(this);
        LinearLayout b = ui.v();
        b.setPadding(0, 0, 0, ui.dp(32));

        LinearLayout c1 = ui.card();
        c1.addView(ui.cardTitle(getString(R.string.app_name) + " 1.0"));
        c1.addView(ui.text("수영 랩 타임, 심박수, 거리, 페이스를 기록하고 히스토리와 통계로 확인하는 앱이에요.",
                14, ui.fg, false));
        b.addView(c1);

        LinearLayout c2 = ui.card();
        c2.addView(ui.cardTitle("Health Connect 데이터 사용"));
        c2.addView(ui.text(
                "• 읽는 데이터: 운동 기록(수영 세션·랩·영법), 심박수, 거리, 소모 칼로리\n"
                        + "• 사용 목적: 수영 기록을 화면에 정리해서 보여주는 용도로만 사용해요.\n"
                        + "• 저장 위치: 가져온 데이터는 이 기기 안의 앱 저장소에만 저장돼요.\n"
                        + "• 외부 전송: 데이터를 서버나 제3자에게 보내지 않아요. 인터넷 권한도 사용하지 않아요.\n"
                        + "• 권한 해제: 설정 > Health Connect > 앱 권한에서 언제든 해제할 수 있어요.",
                14, ui.fg, false));
        b.addView(c2);

        LinearLayout c3 = ui.card();
        c3.addView(ui.cardTitle("데이터 보관 팁"));
        c3.addView(ui.text("앱을 삭제하면 기록도 함께 삭제돼요. 메뉴의 '백업 내보내기'로 JSON 파일을 저장해두면 "
                + "다시 설치한 뒤 '백업 불러오기'로 복원할 수 있어요.", 14, ui.fg, false));
        b.addView(c3);

        sv.addView(b);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        setContentView(root);
    }
}
