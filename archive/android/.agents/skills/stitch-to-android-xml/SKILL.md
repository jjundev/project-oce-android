---
name: stitch-to-android-xml
description: >
  Google Stitch에서 디자인된 화면을 Android Studio XML 레이아웃 파일로 변환하는 스킬.
  Stitch MCP의 get_screen / list_screens 툴로 화면 데이터를 읽어온 뒤,
  Python 스크립트로 ConstraintLayout 또는 LinearLayout 기반 XML을 자동 생성한다.
  사용자가 "Stitch 디자인을 Android에서 쓰고 싶어", "Stitch 화면을 XML로 변환해줘",
  "Stitch 화면을 앱에 적용하고 싶어", "Stitch에서 만든 화면을 Android Studio에 넣고 싶어"
  같은 요청을 할 때 반드시 이 스킬을 사용할 것. Stitch MCP와 Codex가 연동된 환경에서도 적극 활용한다.
---

# Stitch → Android XML 변환 스킬

## 개요

Google Stitch MCP로 화면 데이터를 가져온 뒤, Android Studio에서 사용 가능한 XML 레이아웃 파일로 변환하는 Python 스크립트를 생성한다.

---

## 사용할 Stitch MCP 툴

| 툴 이름 | 역할 |
|---|---|
| `list_projects` | 프로젝트 목록 조회 → 프로젝트명 확인 |
| `list_screens` | 프로젝트 내 화면 목록 → screen_id 확인 |
| `get_screen` | **특정 화면 상세 데이터 조회** (핵심) |

---

## 전체 워크플로우

```
list_projects → 프로젝트명 선택
    ↓
list_screens → 변환할 screen_id 선택
    ↓
get_screen → 화면 구조 데이터 수신
    ↓
Python 스크립트로 Android XML 생성
    ↓
res/layout/*.xml 파일 저장
```

---

## Step 1: MCP 툴로 화면 데이터 조회

### 순서
```
1. list_projects 호출 → project_name 목록 확인
2. list_screens(project_name) 호출 → screen_id 목록 확인
3. get_screen(project_name, screen_id) 호출 → 화면 구조 JSON 수신
```

> `get_screen` 실제 응답 JSON을 먼저 출력하여 필드 구조(`components`, `type`, `x`, `y` 등의 실제 키 이름)를 파악한다. 구조가 아래 예시와 다를 경우, 스크립트의 키 이름을 조정한다.

### get_screen 예상 응답 구조
```json
{
  "screen": {
    "id": "screen_login",
    "name": "로그인 화면",
    "components": [
      {
        "id": "txt_title",
        "type": "Text",
        "x": 20, "y": 40,
        "width": 300, "height": 40,
        "properties": {
          "text": "환영합니다",
          "fontSize": 24,
          "fontWeight": "bold",
          "textColor": "#000000"
        }
      },
      {
        "id": "btn_login",
        "type": "Button",
        "x": 20, "y": 100,
        "width": 350, "height": 56,
        "properties": {
          "text": "로그인",
          "backgroundColor": "#6200EE",
          "textColor": "#FFFFFF",
          "cornerRadius": 8
        }
      }
    ]
  }
}
```

---

## Step 2: 컴포넌트 타입 매핑 테이블

| Stitch type | Android View | 비고 |
|---|---|---|
| `Text` | `TextView` | |
| `Button` | `com.google.android.material.button.MaterialButton` | |
| `Input` / `TextField` | `com.google.android.material.textfield.TextInputLayout` | 내부에 `TextInputEditText` 추가 |
| `Image` / `Icon` | `ImageView` | src는 placeholder 처리 |
| `Container` / `Frame` | `androidx.constraintlayout.widget.ConstraintLayout` | |
| `Row` | `LinearLayout` (horizontal) | |
| `Column` | `LinearLayout` (vertical) | |
| `Divider` | `View` (1dp height) | |
| `Switch` | `com.google.android.material.switchmaterial.SwitchMaterial` | |
| `Checkbox` | `CheckBox` | |
| `List` | `androidx.recyclerview.widget.RecyclerView` | 아이템 레이아웃 별도 생성 |
| `Card` | `com.google.android.material.card.MaterialCardView` | |
| `FAB` | `com.google.android.material.floatingactionbutton.FloatingActionButton` | |
| `BottomNav` | `com.google.android.material.bottomnavigation.BottomNavigationView` | |
| `Toolbar` / `TopBar` | `androidx.appcompat.widget.Toolbar` | |

---

## Step 3: Python 변환 스크립트

```python
#!/usr/bin/env python3
"""
stitch_to_xml.py
Google Stitch get_screen 응답 → Android Layout XML 변환기

사용법:
  python stitch_to_xml.py screen_data.json res/layout/activity_main.xml [constraint|linear]
"""

import json, sys
from xml.etree.ElementTree import Element, SubElement, indent, tostring

DESIGN_DPI = 160  # Stitch 기준 DPI

COMPONENT_MAP = {
    "Text":      "TextView",
    "Button":    "com.google.android.material.button.MaterialButton",
    "Input":     "com.google.android.material.textfield.TextInputLayout",
    "TextField": "com.google.android.material.textfield.TextInputLayout",
    "Image":     "ImageView",
    "Icon":      "ImageView",
    "Container": "androidx.constraintlayout.widget.ConstraintLayout",
    "Frame":     "androidx.constraintlayout.widget.ConstraintLayout",
    "Row":       "LinearLayout",
    "Column":    "LinearLayout",
    "Divider":   "View",
    "Switch":    "com.google.android.material.switchmaterial.SwitchMaterial",
    "Checkbox":  "CheckBox",
    "List":      "androidx.recyclerview.widget.RecyclerView",
    "Card":      "com.google.android.material.card.MaterialCardView",
    "FAB":       "com.google.android.material.floatingactionbutton.FloatingActionButton",
}

ANDROID = "http://schemas.android.com/apk/res/android"
APP     = "http://schemas.android.com/apk/res-auto"
TOOLS   = "http://schemas.android.com/tools"

def a(el, name, val):   el.set(f"{{{ANDROID}}}{name}", val)
def app(el, name, val): el.set(f"{{{APP}}}{name}", val)

def dp(px) -> str:
    return f"{round(float(px) / (DESIGN_DPI / 160))}dp"

def color(h: str) -> str:
    h = h.lstrip("#")
    return f"#{h.upper()}"

def convert(comp: dict, parent: Element, mode: str = "constraint"):
    ctype = comp.get("type", "Container")
    view  = COMPONENT_MAP.get(ctype, "View")
    props = comp.get("properties", {})

    el = SubElement(parent, view)
    a(el, "id", f"@+id/{comp.get('id', 'view')}")

    w, h_val = comp.get("width", 0), comp.get("height", 0)
    a(el, "layout_width",  dp(w) if w else "wrap_content")
    a(el, "layout_height", dp(h_val) if h_val else "wrap_content")

    # ConstraintLayout 위치
    if mode == "constraint":
        x, y = comp.get("x", 0), comp.get("y", 0)
        app(el, "layout_constraintStart_toStartOf", "parent")
        app(el, "layout_constraintTop_toTopOf",     "parent")
        if x: a(el, "layout_marginStart", dp(x))
        if y: a(el, "layout_marginTop",   dp(y))

    # 텍스트
    if "text"  in props: a(el, "text",  props["text"])
    if "hint"  in props: a(el, "hint",  props["hint"])

    # 색상 / 크기 / 스타일
    if "textColor"       in props: a(el,   "textColor",  color(props["textColor"]))
    if "fontSize"        in props: a(el,   "textSize",   f"{props['fontSize']}sp")
    if props.get("fontWeight") == "bold": a(el, "textStyle", "bold")

    # 배경
    if "backgroundColor" in props:
        bg = color(props["backgroundColor"])
        if "MaterialButton" in view: app(el, "backgroundTint", bg)
        else:                        a(el,   "background",     bg)

    # 모서리
    if "cornerRadius" in props: app(el, "cornerRadius", dp(props["cornerRadius"]))

    # LinearLayout orientation
    if ctype == "Row":    a(el, "orientation", "horizontal")
    if ctype == "Column": a(el, "orientation", "vertical")

    # ImageView placeholder
    if ctype in ("Image", "Icon"):
        a(el, "scaleType", "centerCrop")
        el.set(f"{{{TOOLS}}}src", "@tools:sample/avatars")

    # Divider 기본 스타일
    if ctype == "Divider":
        if not w:    a(el, "layout_width",  "match_parent")
        if not h_val: a(el, "layout_height", "1dp")
        if "backgroundColor" not in props: a(el, "background", "#DDDDDD")

    # 자식 재귀
    for child in comp.get("children", []):
        convert(child, el, mode)

    return el


def stitch_to_xml(screen_data: dict, output_path: str, mode: str = "constraint"):
    screen     = screen_data.get("screen", screen_data)
    components = screen.get("components", [])

    if mode == "constraint":
        root = Element("androidx.constraintlayout.widget.ConstraintLayout")
        root.set("xmlns:android", ANDROID)
        root.set("xmlns:app",     APP)
        root.set("xmlns:tools",   TOOLS)
    else:
        root = Element("LinearLayout")
        root.set("xmlns:android", ANDROID)
        root.set("xmlns:app",     APP)
        a(root, "orientation", "vertical")

    a(root, "layout_width",  "match_parent")
    a(root, "layout_height", "match_parent")

    for comp in components:
        convert(comp, root, mode)

    indent(root, space="    ")
    xml_out = '<?xml version="1.0" encoding="utf-8"?>\n' + tostring(root, encoding="unicode")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(xml_out)
    print(f"✅ XML 생성 완료: {output_path}")


if __name__ == "__main__":
    json_path   = sys.argv[1] if len(sys.argv) > 1 else "screen_data.json"
    output_path = sys.argv[2] if len(sys.argv) > 2 else "output.xml"
    mode        = sys.argv[3] if len(sys.argv) > 3 else "constraint"

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    stitch_to_xml(data, output_path, mode)
```

---

## Step 4: 실행 방법

```bash
# get_screen 응답을 screen_data.json으로 저장한 뒤

# ConstraintLayout XML 생성 (기본 권장)
python stitch_to_xml.py screen_data.json res/layout/activity_main.xml constraint

# LinearLayout XML 생성
python stitch_to_xml.py screen_data.json res/layout/fragment_home.xml linear
```

---

## Step 5: 후처리 체크리스트

Android Studio에서 생성된 XML 적용 전 확인:

- [ ] 하드코딩 색상 → `res/values/colors.xml`로 추출
- [ ] 하드코딩 텍스트 → `res/values/strings.xml`로 추출
- [ ] `ImageView` placeholder → 실제 drawable / Glide 연결
- [ ] `TextInputLayout` 내부에 `TextInputEditText` 수동 추가
- [ ] ConstraintLayout missing constraint 경고 해결
- [ ] 다양한 화면 크기 Preview 확인 (small / large phone)
- [ ] `RecyclerView`는 Adapter / ViewHolder 별도 구현 필요

---

## 주의사항

- `get_screen` 실제 응답 필드명이 예시와 다를 수 있으므로, 응답을 먼저 출력하여 구조를 확인한 뒤 스크립트 키를 조정한다.
- ConstraintLayout 절대 좌표 변환은 다양한 화면 크기에서 레이아웃이 깨질 수 있으므로 적용 후 반응형 제약 조건을 추가한다.
- 애니메이션, 그라디언트, 블러 같은 고급 속성은 별도 drawable XML로 수동 구현한다.
- `generate_variants`로 만든 변형 화면도 동일하게 `get_screen`으로 조회 후 변환 가능하다.
