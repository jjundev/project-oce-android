/* @ds-bundle: {"format":3,"namespace":"DesignSystem_498c1d","components":[{"name":"Badge","sourcePath":"components/core/Badge.jsx"},{"name":"Button","sourcePath":"components/core/Button.jsx"},{"name":"Card","sourcePath":"components/core/Card.jsx"},{"name":"Icon","sourcePath":"components/core/Icon.jsx"},{"name":"IconButton","sourcePath":"components/core/IconButton.jsx"},{"name":"ListRow","sourcePath":"components/data/ListRow.jsx"},{"name":"SavedCard","sourcePath":"components/data/SavedCard.jsx"},{"name":"ChatBubble","sourcePath":"components/dialogue/ChatBubble.jsx"},{"name":"MicButton","sourcePath":"components/dialogue/MicButton.jsx"},{"name":"Waveform","sourcePath":"components/dialogue/Waveform.jsx"},{"name":"BottomSheet","sourcePath":"components/feedback/BottomSheet.jsx"},{"name":"FeedbackSection","sourcePath":"components/feedback/FeedbackSection.jsx"},{"name":"FeedbackSheet","sourcePath":"components/feedback/FeedbackSheet.jsx"},{"name":"RewardStrip","sourcePath":"components/feedback/RewardStrip.jsx"},{"name":"VennDiagram","sourcePath":"components/feedback/VennDiagram.jsx"},{"name":"Input","sourcePath":"components/forms/Input.jsx"},{"name":"SegmentedControl","sourcePath":"components/forms/SegmentedControl.jsx"},{"name":"Switch","sourcePath":"components/forms/Switch.jsx"},{"name":"BottomNav","sourcePath":"components/navigation/BottomNav.jsx"}],"sourceHashes":{"components/core/Badge.jsx":"c4303f916805","components/core/Button.jsx":"a37c93fb1125","components/core/Card.jsx":"b22c167dc185","components/core/Icon.jsx":"e731998d9b0a","components/core/IconButton.jsx":"ed69ff82f586","components/data/ListRow.jsx":"b44333e59b38","components/data/SavedCard.jsx":"a425b1d1e1d5","components/dialogue/ChatBubble.jsx":"4deafe4f5ee1","components/dialogue/MicButton.jsx":"f52bd51f3de2","components/dialogue/Waveform.jsx":"e059b3b82286","components/feedback/BottomSheet.jsx":"455ce9cc7502","components/feedback/FeedbackSection.jsx":"5ac6f46368c2","components/feedback/FeedbackSheet.jsx":"547df3053e92","components/feedback/RewardStrip.jsx":"8b515729750c","components/feedback/VennDiagram.jsx":"b0f16156ba5d","components/forms/Input.jsx":"f8ebb9be53e7","components/forms/SegmentedControl.jsx":"e0e661da679b","components/forms/Switch.jsx":"dfeac1467e78","components/navigation/BottomNav.jsx":"b76c85577ccf"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.DesignSystem_498c1d = window.DesignSystem_498c1d || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/core/Badge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickBadge — pill label / streak chip. Color + text dual-signal (never color alone).
 * tones: neutral · natural (green) · correct (coral) · streak (orange) · gold · brand.
 */
const TONES = {
  neutral: {
    bg: 'var(--surface-background)',
    fg: 'var(--text-secondary)'
  },
  natural: {
    bg: 'var(--feedback-natural-bg)',
    fg: 'var(--feedback-natural-accent)'
  },
  correct: {
    bg: 'var(--feedback-correct-bg)',
    fg: 'var(--feedback-correct-accent)'
  },
  streak: {
    bg: 'rgba(255,92,0,0.12)',
    fg: 'var(--game-streak)'
  },
  gold: {
    bg: 'rgba(255,193,7,0.16)',
    fg: '#A87900'
  },
  brand: {
    bg: 'rgba(57,160,237,0.12)',
    fg: 'var(--brand-primary)'
  }
};
function Badge({
  tone = 'neutral',
  children,
  style,
  ...rest
}) {
  const t = TONES[tone] || TONES.neutral;
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4,
      padding: '4px 10px',
      borderRadius: 'var(--radius-pill)',
      background: t.bg,
      color: t.fg,
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      fontWeight: 'var(--font-weight-bold)',
      lineHeight: 1.2,
      whiteSpace: 'nowrap',
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Badge.jsx", error: String((e && e.message) || e) }); }

// components/core/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickCard — flat surface container (radius.24, 1dp hairline, elevation 0).
 * Optional `gradient` for the brand hero card (135° blue). Depth via border, not shadow.
 */
function Card({
  gradient = false,
  padding = 'var(--space-xxl)',
  onClick,
  children,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    onClick: onClick,
    style: {
      background: gradient ? 'var(--brand-gradient)' : 'var(--surface-card)',
      border: gradient ? '1px solid transparent' : '1px solid var(--border-hairline)',
      borderRadius: 'var(--radius-24)',
      padding,
      color: gradient ? 'var(--text-on-primary)' : 'var(--text-primary)',
      cursor: onClick ? 'pointer' : 'default',
      boxSizing: 'border-box',
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Card.jsx", error: String((e && e.message) || e) }); }

// components/core/Icon.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickIcon — filled 24-grid glyph slot that inherits text color.
 *
 * NOTE: the product's official icon set is not yet selected. Per request, this
 * renders a BLANK placeholder that reserves the glyph's box (size×size) so layouts
 * stay intact — no substitute glyph is shown. When the official filled 24-grid set
 * is chosen, swap the render here (this is the single seam); the `name` prop already
 * carries the intended semantic glyph name for every call site.
 */
function Icon({
  name,
  size = 24,
  weight,
  fill = 1,
  color = 'currentColor',
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("span", _extends({
    "data-icon": name,
    "aria-hidden": "true",
    style: {
      display: 'inline-block',
      width: size,
      height: size,
      flexShrink: 0,
      color,
      ...style
    }
  }, rest));
}
Object.assign(__ds_scope, { Icon });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Icon.jsx", error: String((e && e.message) || e) }); }

// components/core/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickButton — global CTA / 다음 / 재시도.
 * Full spec: buildspec B.1. primary & secondary 52dp (h:24 v:14), ghost 48dp (h:16 v:12).
 * radius.12, type.body Bold. States (B.2): disabled alpha .38, pressed brand.primaryPressed,
 * focused brand.primary ring, loading = spinner + hidden label.
 */
function Button({
  variant = 'primary',
  disabled = false,
  loading = false,
  leadingIcon,
  trailingIcon,
  fullWidth = false,
  onClick,
  children,
  style,
  ...rest
}) {
  const [pressed, setPressed] = React.useState(false);
  const isGhost = variant === 'ghost';
  const base = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 'var(--space-sm)',
    position: 'relative',
    width: fullWidth ? '100%' : undefined,
    minHeight: isGhost ? 48 : 52,
    padding: isGhost ? '12px 16px' : '14px 24px',
    borderRadius: 'var(--radius-12)',
    fontFamily: 'var(--font-family)',
    fontSize: 'var(--type-body-size)',
    fontWeight: isGhost ? 'var(--font-weight-medium)' : 'var(--font-weight-bold)',
    lineHeight: 1.2,
    letterSpacing: '-0.01em',
    cursor: disabled || loading ? 'default' : 'pointer',
    opacity: disabled ? 0.38 : 1,
    transition: 'background-color var(--motion-duration-fast) var(--motion-ease-standard), border-color var(--motion-duration-fast) var(--motion-ease-standard)',
    WebkitTapHighlightColor: 'transparent',
    userSelect: 'none'
  };
  const variants = {
    primary: {
      background: pressed ? 'var(--brand-primary-pressed)' : 'var(--brand-primary)',
      color: 'var(--text-on-primary)',
      border: '1px solid transparent'
    },
    secondary: {
      background: 'var(--surface-card)',
      color: pressed ? 'var(--brand-primary-pressed)' : 'var(--brand-primary)',
      border: `1px solid ${pressed ? 'var(--brand-primary)' : 'var(--border-hairline)'}`
    },
    ghost: {
      background: pressed ? 'var(--surface-background)' : 'transparent',
      color: 'var(--text-secondary)',
      border: '1px solid transparent'
    }
  };
  const stop = () => setPressed(false);
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    disabled: disabled || loading,
    onClick: onClick,
    onMouseDown: () => !disabled && !loading && setPressed(true),
    onMouseUp: stop,
    onMouseLeave: stop,
    onTouchStart: () => !disabled && !loading && setPressed(true),
    onTouchEnd: stop,
    style: {
      ...base,
      ...variants[variant],
      ...style
    }
  }, rest), loading && /*#__PURE__*/React.createElement("span", {
    "aria-hidden": "true",
    style: {
      position: 'absolute',
      width: 18,
      height: 18,
      borderRadius: '50%',
      border: '2px solid currentColor',
      borderTopColor: 'transparent',
      animation: 'oc-spin 0.7s linear infinite'
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 'var(--space-sm)',
      visibility: loading ? 'hidden' : 'visible'
    }
  }, leadingIcon && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: leadingIcon,
    size: 20
  }), children, trailingIcon && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: trailingIcon,
    size: 20
  })), /*#__PURE__*/React.createElement("style", null, `@keyframes oc-spin { to { transform: rotate(360deg); } }`));
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Button.jsx", error: String((e && e.message) || e) }); }

// components/core/IconButton.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickIconButton — icon-only action (sheet close, settings rows).
 * radius.12, ≥48dp touch target. Subtle surface-background press state.
 */
function IconButton({
  icon,
  size = 48,
  iconSize = 24,
  disabled = false,
  onClick,
  label,
  style,
  ...rest
}) {
  const [pressed, setPressed] = React.useState(false);
  const stop = () => setPressed(false);
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    "aria-label": label,
    disabled: disabled,
    onClick: onClick,
    onMouseDown: () => !disabled && setPressed(true),
    onMouseUp: stop,
    onMouseLeave: stop,
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: size,
      height: size,
      padding: 0,
      borderRadius: 'var(--radius-12)',
      border: 'none',
      background: pressed ? 'var(--surface-background)' : 'transparent',
      color: 'var(--text-secondary)',
      cursor: disabled ? 'default' : 'pointer',
      opacity: disabled ? 0.38 : 1,
      transition: 'background-color var(--motion-duration-fast) var(--motion-ease-standard)',
      WebkitTapHighlightColor: 'transparent',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: iconSize
  }));
}
Object.assign(__ds_scope, { IconButton });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/IconButton.jsx", error: String((e && e.message) || e) }); }

// components/data/ListRow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickListRow — history / settings row on surface.card.
 * Optional leading icon, title + subtitle, trailing content (chevron / switch / value).
 */
function ListRow({
  leadingIcon,
  iconColor = 'var(--text-secondary)',
  title,
  subtitle,
  trailing,
  showChevron = false,
  onClick,
  style,
  ...rest
}) {
  const [pressed, setPressed] = React.useState(false);
  const stop = () => setPressed(false);
  return /*#__PURE__*/React.createElement("div", _extends({
    onClick: onClick,
    onMouseDown: () => onClick && setPressed(true),
    onMouseUp: stop,
    onMouseLeave: stop,
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-md)',
      minHeight: 56,
      padding: '12px 16px',
      background: pressed ? 'var(--surface-background)' : 'var(--surface-card)',
      cursor: onClick ? 'pointer' : 'default',
      transition: 'background-color var(--motion-duration-fast) var(--motion-ease-standard)',
      ...style
    }
  }, rest), leadingIcon && /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      width: 40,
      height: 40,
      alignItems: 'center',
      justifyContent: 'center',
      borderRadius: 'var(--radius-12)',
      background: 'var(--surface-background)',
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: leadingIcon,
    size: 22,
    color: iconColor
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-body-size)',
      fontWeight: 'var(--font-weight-medium)',
      color: 'var(--text-primary)',
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis'
    }
  }, title), subtitle && /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      color: 'var(--text-tertiary)',
      marginTop: 2
    }
  }, subtitle)), trailing, showChevron && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "chevron_right",
    size: 22,
    color: "var(--text-tertiary)"
  }));
}
Object.assign(__ds_scope, { ListRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data/ListRow.jsx", error: String((e && e.message) || e) }); }

// components/data/SavedCard.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickSavedCard — saved item, 3 types: WORD / SENTENCE / EXPRESSION.
 * surface.card, radius.24, hairline. Type label badge + gold save mark.
 */
const TYPE_META = {
  WORD: {
    label: '단어',
    tone: 'brand',
    icon: 'match_word'
  },
  SENTENCE: {
    label: '문장',
    tone: 'natural',
    icon: 'notes'
  },
  EXPRESSION: {
    label: '표현',
    tone: 'correct',
    icon: 'format_quote'
  }
};
function SavedCard({
  type = 'WORD',
  term,
  meaning,
  example,
  saved = true,
  onToggleSave,
  style,
  ...rest
}) {
  const meta = TYPE_META[type] || TYPE_META.WORD;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      background: 'var(--surface-card)',
      border: '1px solid var(--border-hairline)',
      borderRadius: 'var(--radius-24)',
      padding: 'var(--space-xl)',
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-sm)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Badge, {
    tone: meta.tone
  }, meta.label), /*#__PURE__*/React.createElement("button", {
    type: "button",
    "aria-label": saved ? '저장 취소' : '저장',
    onClick: onToggleSave,
    style: {
      border: 'none',
      background: 'transparent',
      padding: 4,
      cursor: 'pointer',
      display: 'inline-flex'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: saved ? 'bookmark' : 'bookmark_border',
    size: 22,
    color: saved ? 'var(--game-save-gold)' : 'var(--text-tertiary)',
    fill: saved ? 1 : 0
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-dialog-header-size)',
      fontWeight: 'var(--font-weight-bold)',
      color: 'var(--text-primary)',
      letterSpacing: '-0.02em'
    }
  }, term), meaning && /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-body-size)',
      color: 'var(--text-secondary)'
    }
  }, meaning), example && /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      color: 'var(--text-tertiary)',
      fontStyle: 'italic',
      marginTop: 2
    },
    lang: "en"
  }, "\u201C", example, "\u201D"));
}
Object.assign(__ds_scope, { SavedCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data/SavedCard.jsx", error: String((e && e.message) || e) }); }

// components/dialogue/ChatBubble.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickChatBubble — dialogue turn bubble.
 * User = brand.primary / onPrimary; Opponent = surface.card / text.primary.
 * Body radius.18, tail radius.4 (on the speaker's bottom corner). maxWidth 78%.
 * padding h:14 v:10. Opponent bubble can show a TTS playing indicator.
 */
function ChatBubble({
  speaker = 'opponent',
  children,
  playing = false,
  onReplay,
  style,
  ...rest
}) {
  const isUser = speaker === 'user';
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: isUser ? 'flex-end' : 'flex-start',
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement("div", _extends({
    lang: isUser ? undefined : 'en',
    style: {
      maxWidth: '78%',
      padding: '10px 14px',
      background: isUser ? 'var(--brand-primary)' : 'var(--surface-card)',
      color: isUser ? 'var(--text-on-primary)' : 'var(--text-primary)',
      border: isUser ? '1px solid transparent' : '1px solid var(--border-hairline)',
      borderRadius: 'var(--radius-18)',
      borderBottomRightRadius: isUser ? 'var(--radius-4)' : 'var(--radius-18)',
      borderBottomLeftRadius: isUser ? 'var(--radius-18)' : 'var(--radius-4)',
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-body-size)',
      lineHeight: 'var(--type-body-line)',
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-sm)',
      ...style
    }
  }, rest), !isUser && onReplay && /*#__PURE__*/React.createElement("button", {
    type: "button",
    "aria-label": "\uB2E4\uC2DC \uB4E3\uAE30",
    onClick: onReplay,
    style: {
      border: 'none',
      background: 'transparent',
      padding: 0,
      display: 'inline-flex',
      cursor: 'pointer',
      color: playing ? 'var(--brand-primary)' : 'var(--text-tertiary)',
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: playing ? 'graphic_eq' : 'volume_up',
    size: 20
  })), /*#__PURE__*/React.createElement("span", null, children)));
}
Object.assign(__ds_scope, { ChatBubble });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/dialogue/ChatBubble.jsx", error: String((e && e.message) || e) }); }

// components/dialogue/MicButton.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickMicButton — 96×96dp circular voice control, 4 MicState appearances (§6).
 *   Ready     — voice.ready gray concentric circles, "녹음 시작"
 *   Recording — voice.recording red + 3 phase-offset ripples, "녹음 중"
 *   Analyzing — voice.analyzing blue-gray + progress ring, "분석 중"
 *   Complete  — voice.complete green check, "완료"
 * Pressed scale 0.96. reduce-motion → ripples/ring static.
 */
const ANNOUNCE = {
  Ready: '녹음 시작',
  Recording: '녹음 중',
  Analyzing: '분석 중',
  Complete: '완료'
};
function MicButton({
  state = 'Ready',
  onClick,
  disabled = false,
  size = 96,
  style,
  ...rest
}) {
  const [pressed, setPressed] = React.useState(false);
  const stop = () => setPressed(false);
  const core = {
    Ready: 'var(--voice-ready-core)',
    Recording: 'var(--voice-recording-core)',
    Analyzing: 'var(--voice-analyzing)',
    Complete: 'var(--voice-complete)'
  }[state];
  const ring = {
    Ready: 'var(--voice-ready-ring)',
    Recording: 'var(--voice-recording-ring)',
    Analyzing: 'transparent',
    Complete: 'rgba(76,175,80,0.16)'
  }[state];
  const icon = state === 'Complete' ? 'check' : state === 'Analyzing' ? 'autorenew' : 'mic';
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    "aria-label": ANNOUNCE[state],
    "aria-live": "assertive",
    disabled: disabled,
    onClick: onClick,
    onMouseDown: () => !disabled && setPressed(true),
    onMouseUp: stop,
    onMouseLeave: stop,
    style: {
      position: 'relative',
      width: size,
      height: size,
      border: 'none',
      borderRadius: '50%',
      background: ring,
      cursor: disabled ? 'default' : 'pointer',
      opacity: disabled ? 0.38 : 1,
      transform: pressed ? 'scale(0.96)' : 'scale(1)',
      transition: 'transform var(--motion-duration-fast) var(--motion-ease-standard), background-color var(--motion-duration-base) var(--motion-ease-standard)',
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      WebkitTapHighlightColor: 'transparent',
      ...style
    }
  }, rest), state === 'Recording' && /*#__PURE__*/React.createElement("span", {
    "aria-hidden": "true",
    className: "oc-mic-ripples"
  }, [0, 1, 2].map(i => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "oc-mic-ripple",
    style: {
      animationDelay: `${i * 200}ms`,
      borderColor: 'var(--voice-recording-core)'
    }
  }))), state === 'Analyzing' && /*#__PURE__*/React.createElement("span", {
    "aria-hidden": "true",
    className: "oc-mic-progress",
    style: {
      borderTopColor: 'var(--voice-analyzing)'
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      position: 'relative',
      width: size * 0.62,
      height: size * 0.62,
      borderRadius: '50%',
      background: state === 'Ready' ? 'transparent' : core,
      border: state === 'Ready' ? `3px solid ${core}` : 'none',
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: state === 'Ready' ? core : '#FFFFFF'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: size * 0.3
  })), /*#__PURE__*/React.createElement("style", null, `
        .oc-mic-ripples { position:absolute; inset:0; }
        .oc-mic-ripple {
          position:absolute; inset:0; border-radius:50%;
          border:2px solid; opacity:0;
          animation: oc-ripple var(--motion-ripple-loop) ease-out infinite;
        }
        @keyframes oc-ripple {
          0% { transform: scale(0.7); opacity:0.55; }
          100% { transform: scale(1.35); opacity:0; }
        }
        .oc-mic-progress {
          position:absolute; inset:6px; border-radius:50%;
          border:3px solid var(--border-hairline);
          animation: oc-spin 0.9s linear infinite;
        }
        @keyframes oc-spin { to { transform: rotate(360deg); } }
        @media (prefers-reduced-motion: reduce) {
          .oc-mic-ripple { animation:none; opacity:0.3; transform:scale(1.15); }
          .oc-mic-progress { animation:none; }
        }
      `));
}
Object.assign(__ds_scope, { MicButton });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/dialogue/MicButton.jsx", error: String((e && e.message) || e) }); }

// components/dialogue/Waveform.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickWaveform — real-time crackle waveform (MicState.Recording).
 * 40 bars, 4dp gap, bar radius.4, amplitude 0–1 with per-bar ±0.3 jiggle.
 * Gray vertical gradient #9E9E9E→#757575. height 48dp.
 * `active` animates; otherwise renders a static low-amplitude trace.
 * reduce-motion → static.
 */
const BAR_COUNT = 40;
function Waveform({
  active = true,
  height = 48,
  style,
  ...rest
}) {
  const [tick, setTick] = React.useState(0);
  const reduce = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)').matches : false;
  React.useEffect(() => {
    if (!active || reduce) return;
    const id = setInterval(() => setTick(t => t + 1), 90);
    return () => clearInterval(id);
  }, [active, reduce]);

  // stable base envelope (mid-loud center), modulated by transient jiggle
  const bars = React.useMemo(() => {
    return Array.from({
      length: BAR_COUNT
    }, (_, i) => {
      const env = 0.35 + 0.5 * Math.sin(i / BAR_COUNT * Math.PI);
      return env;
    });
  }, []);
  return /*#__PURE__*/React.createElement("div", _extends({
    "aria-hidden": "true",
    style: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 4,
      height,
      ...style
    }
  }, rest), bars.map((env, i) => {
    const jiggle = active && !reduce ? Math.sin(tick * 0.8 + i * 1.7) * 0.3 : 0;
    const amp = Math.max(0.08, Math.min(1, (active ? env : 0.18) + jiggle));
    return /*#__PURE__*/React.createElement("span", {
      key: i,
      style: {
        width: 3,
        height: `${amp * 100}%`,
        borderRadius: 'var(--radius-4)',
        background: 'linear-gradient(180deg, var(--waveform-top) 0%, var(--waveform-bottom) 100%)',
        transition: active && !reduce ? 'height 90ms linear' : 'none'
      }
    });
  }));
}
Object.assign(__ds_scope, { Waveform });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/dialogue/Waveform.jsx", error: String((e && e.message) || e) }); }

// components/feedback/BottomSheet.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickBottomSheet — net-new modal sheet (PRD primary action stage).
 * Top radius.24, drag handle, 24dp padding, max height 90%, scrim = overlay-dim.
 * Slides up; reduce-motion → instant. Focus returns to caller on close (caller responsibility).
 */
function BottomSheet({
  open = false,
  onClose,
  children,
  maxHeight = '90%',
  style,
  ...rest
}) {
  if (!open) return null;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      position: 'absolute',
      inset: 0,
      zIndex: 50,
      display: 'flex',
      alignItems: 'flex-end',
      justifyContent: 'center'
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    onClick: onClose,
    style: {
      position: 'absolute',
      inset: 0,
      background: 'var(--surface-overlay-dim)',
      animation: 'oc-scrim-in var(--motion-duration-base) var(--motion-ease-standard)'
    }
  }), /*#__PURE__*/React.createElement("div", {
    role: "dialog",
    "aria-modal": "true",
    style: {
      position: 'relative',
      width: '100%',
      maxHeight,
      background: 'var(--surface-card)',
      borderTopLeftRadius: 'var(--radius-24)',
      borderTopRightRadius: 'var(--radius-24)',
      padding: 'var(--space-sheet-padding)',
      paddingTop: 'var(--space-md)',
      boxSizing: 'border-box',
      overflowY: 'auto',
      animation: 'oc-sheet-up var(--motion-duration-base) var(--motion-ease-out)',
      ...style
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'center',
      paddingBottom: 'var(--space-md)'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 36,
      height: 4,
      borderRadius: 'var(--radius-pill)',
      background: 'var(--border-strong)'
    }
  })), children), /*#__PURE__*/React.createElement("style", null, `
        @keyframes oc-sheet-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
        @keyframes oc-scrim-in { from { opacity: 0; } to { opacity: 1; } }
        @media (prefers-reduced-motion: reduce) {
          [role="dialog"] { animation: none !important; }
        }
      `));
}
Object.assign(__ds_scope, { BottomSheet });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/BottomSheet.jsx", error: String((e && e.message) || e) }); }

// components/feedback/FeedbackSection.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickFeedbackSection — one labeled block inside the feedback sheet.
 * States: Loading (shimmer skeleton), Ready (real data), ErrorRecoverable (inline retry).
 * Section label = type.sectionLabel; body = type.body. v:16 between sections.
 */
function Skeleton({
  lines = 2
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 8
    }
  }, Array.from({
    length: lines
  }).map((_, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "oc-shimmer",
    style: {
      height: 14,
      width: i === lines - 1 ? '62%' : '100%',
      borderRadius: 'var(--radius-4)'
    }
  })), /*#__PURE__*/React.createElement("style", null, `
        .oc-shimmer {
          background: linear-gradient(90deg, var(--surface-background) 25%, var(--border-hairline) 37%, var(--surface-background) 63%);
          background-size: 400% 100%;
          animation: oc-shimmer var(--motion-shimmer-loop) ease-in-out infinite;
        }
        @keyframes oc-shimmer { 0% { background-position: 100% 0; } 100% { background-position: 0 0; } }
        @media (prefers-reduced-motion: reduce) { .oc-shimmer { animation: none; } }
      `));
}
function FeedbackSection({
  label,
  state = 'Ready',
  icon,
  accent = 'var(--text-secondary)',
  onRetry,
  children,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("section", _extends({
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-sm)',
      marginBottom: 'var(--space-section-gap)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, icon && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 18,
    color: accent
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-section-label-size)',
      fontWeight: 'var(--font-weight-bold)',
      color: accent
    }
  }, label)), state === 'Loading' && /*#__PURE__*/React.createElement(Skeleton, null), state === 'Ready' && /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-body-size)',
      lineHeight: 'var(--type-body-line)',
      color: 'var(--text-primary)'
    }
  }, children), (state === 'ErrorRecoverable' || state === 'Error') && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-sm)',
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      color: 'var(--state-error)'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "error",
    size: 16,
    color: "var(--state-error)"
  }), /*#__PURE__*/React.createElement("span", null, "\uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC5B4\uC694"), /*#__PURE__*/React.createElement("button", {
    type: "button",
    onClick: onRetry,
    style: {
      border: 'none',
      background: 'transparent',
      color: 'var(--brand-primary)',
      fontWeight: 'var(--font-weight-bold)',
      fontSize: 'var(--type-helper-size)',
      cursor: 'pointer',
      padding: 0
    }
  }, "\uB2E4\uC2DC \uC2DC\uB3C4")));
}
Object.assign(__ds_scope, { FeedbackSection });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/FeedbackSection.jsx", error: String((e && e.message) || e) }); }

// components/feedback/FeedbackSheet.jsx
try { (() => {
/**
 * OneClickFeedbackSheet — single bottom sheet hosting turn feedback.
 * slim 3 sections (writingScore → grammar → naturalExpression) always; deep 3
 * (conceptualBridge → toneStyle → paraphrasing) expand inline after slim Ready.
 * `다음` gating: enabled when slim Ready (no score gate). deep is non-blocking.
 * This component is a convenience composition; screens may also compose
 * BottomSheet + FeedbackSection directly.
 */
function FeedbackSheet({
  open,
  onClose,
  score,
  slimState = 'Ready',
  showDeep = false,
  onMore,
  onNext,
  style
}) {
  return /*#__PURE__*/React.createElement(__ds_scope.BottomSheet, {
    open: open,
    onClose: onClose,
    style: style
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'baseline',
      justifyContent: 'space-between',
      marginBottom: 'var(--space-lg)'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-dialog-header-size)',
      fontWeight: 'var(--font-weight-bold)',
      color: 'var(--text-primary)',
      letterSpacing: '-0.02em'
    }
  }, "\uD134 \uD53C\uB4DC\uBC31"), typeof score === 'number' && /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-family)',
      fontWeight: 'var(--font-weight-bold)',
      color: 'var(--brand-primary)',
      fontSize: 22
    }
  }, score)), /*#__PURE__*/React.createElement(__ds_scope.FeedbackSection, {
    label: "\uC791\uBB38 \uC810\uC218",
    icon: "edit_note",
    accent: "var(--brand-primary)",
    state: slimState
  }, "\uD0C4\uD0C4\uD55C \uBB38\uC7A5\uC774\uC5D0\uC694. \uC2DC\uC81C\uB9CC \uD55C \uACF3 \uB2E4\uB4EC\uC73C\uBA74 \uC644\uBCBD\uD574\uC694."), /*#__PURE__*/React.createElement(__ds_scope.FeedbackSection, {
    label: "\uBB38\uBC95",
    icon: "spellcheck",
    accent: "var(--text-secondary)",
    state: slimState
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      textDecoration: 'line-through',
      color: 'var(--feedback-correct-accent)'
    }
  }, "I go"), ' ', "\u2192 ", /*#__PURE__*/React.createElement("strong", null, "I went"), " hiking last weekend."), /*#__PURE__*/React.createElement(__ds_scope.FeedbackSection, {
    label: "\uC790\uC5F0\uC2A4\uB7EC\uC6B4 \uD45C\uD604",
    icon: "auto_awesome",
    accent: "var(--feedback-natural-accent)",
    state: slimState
  }, /*#__PURE__*/React.createElement("mark", {
    style: {
      background: 'var(--feedback-natural-bg)',
      color: 'var(--feedback-natural-accent)',
      padding: '0 4px',
      borderRadius: 4,
      textDecoration: 'underline'
    }
  }, "went on a hike"), ' ', "\uC774 \uB354 \uC6D0\uC5B4\uBBFC\uC2A4\uB7EC\uC6CC\uC694."), showDeep && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(__ds_scope.FeedbackSection, {
    label: "\uAC1C\uB150 \uBE0C\uB9BF\uC9C0",
    icon: "hub",
    accent: "var(--text-secondary)",
    state: "Ready"
  }, "\uD55C\uAD6D\uC5B4 \u201C\uB4F1\uC0B0\u201D\uC740 \uC601\uC5B4\uB85C hiking / trekking / climbing \uC73C\uB85C \uAC08\uB77C\uC838\uC694."), /*#__PURE__*/React.createElement(__ds_scope.FeedbackSection, {
    label: "\uD1A4 \xB7 \uC2A4\uD0C0\uC77C",
    icon: "format_paint",
    accent: "var(--text-secondary)",
    state: "Ready"
  }, "\uCE90\uC8FC\uC5BC\uD55C \uB300\uD654\uC5D4 \u201Cgrab a coffee\u201D \uAC19\uC740 \uD45C\uD604\uC774 \uC798 \uC5B4\uC6B8\uB824\uC694."), /*#__PURE__*/React.createElement(__ds_scope.FeedbackSection, {
    label: "\uD328\uB7EC\uD504\uB808\uC774\uC988",
    icon: "bookmark",
    accent: "var(--feedback-natural-accent)",
    state: "Ready"
  }, "\u201CI went on a hike up the mountain this weekend.\u201D")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--space-action-gap)',
      marginTop: 'var(--space-sm)'
    }
  }, !showDeep && /*#__PURE__*/React.createElement(__ds_scope.Button, {
    variant: "secondary",
    fullWidth: true,
    onClick: onMore,
    disabled: slimState !== 'Ready'
  }, "\uB354 \uBCF4\uAE30"), /*#__PURE__*/React.createElement(__ds_scope.Button, {
    variant: "primary",
    fullWidth: true,
    onClick: onNext,
    disabled: slimState !== 'Ready'
  }, "\uB2E4\uC74C")));
}
Object.assign(__ds_scope, { FeedbackSheet });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/FeedbackSheet.jsx", error: String((e && e.message) || e) }); }

// components/feedback/RewardStrip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickRewardStrip — completion-reward metrics with slot-machine count-up (§6).
 * Only on the completion-reward surface (SessionPhase.Completed); static elsewhere.
 * spring snap (scaleY 0.92→1.0). reduce-motion → instant snap to final.
 */
function useCountUp(target, animate) {
  const [n, setN] = React.useState(animate ? 0 : target);
  React.useEffect(() => {
    if (!animate) {
      setN(target);
      return;
    }
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) {
      setN(target);
      return;
    }
    let raf;
    const start = performance.now();
    const dur = 1260;
    const tick = t => {
      const p = Math.min(1, (t - start) / dur);
      const eased = 1 - Math.pow(1 - p, 3);
      setN(Math.round(eased * target));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, animate]);
  return n;
}
function Metric({
  icon,
  color,
  value,
  suffix,
  label,
  animate
}) {
  const n = useCountUp(value, animate);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 22,
    color: color
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 22,
      fontWeight: 'var(--font-weight-extrabold)',
      color: 'var(--text-primary)',
      letterSpacing: '-0.02em'
    }
  }, n.toLocaleString(), suffix), /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      color: 'var(--text-tertiary)'
    }
  }, label));
}
function RewardStrip({
  xp = 120,
  minutes = 8,
  streak = 7,
  animate = true,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'flex',
      gap: 'var(--space-md)',
      padding: 'var(--space-xl)',
      background: 'var(--surface-card)',
      border: '1px solid var(--border-hairline)',
      borderRadius: 'var(--radius-24)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement(Metric, {
    icon: "bolt",
    color: "var(--brand-primary)",
    value: xp,
    suffix: " XP",
    label: "\uD68D\uB4DD \uACBD\uD5D8\uCE58",
    animate: animate
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      width: 1,
      background: 'var(--border-hairline)'
    }
  }), /*#__PURE__*/React.createElement(Metric, {
    icon: "schedule",
    color: "var(--text-secondary)",
    value: minutes,
    suffix: "\uBD84",
    label: "\uD559\uC2B5 \uC2DC\uAC04",
    animate: animate
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      width: 1,
      background: 'var(--border-hairline)'
    }
  }), /*#__PURE__*/React.createElement(Metric, {
    icon: "local_fire_department",
    color: "var(--game-streak)",
    value: streak,
    suffix: "\uC77C",
    label: "\uC5F0\uC18D \uD559\uC2B5",
    animate: animate
  }));
}
Object.assign(__ds_scope, { RewardStrip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/RewardStrip.jsx", error: String((e && e.message) || e) }); }

// components/feedback/VennDiagram.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickVennDiagram — conceptual-bridge two-circle overlap (240dp square).
 * Side circles alpha ~0.5, intersection darker. Color-distance guard ≥50 (light/dark).
 * MUST provide a text alternative (two words + intersection meaning) — never color alone.
 */
function VennDiagram({
  left = {
    label: 'hiking',
    color: 'var(--brand-primary)'
  },
  right = {
    label: 'climbing',
    color: 'var(--feedback-natural-accent)'
  },
  intersection = '산을 오르다',
  size = 240,
  style,
  ...rest
}) {
  const r = size * 0.3;
  const cx = size / 2;
  const offset = r * 0.62;
  return /*#__PURE__*/React.createElement("figure", _extends({
    style: {
      margin: 0,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 'var(--space-md)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("svg", {
    width: size,
    height: size * 0.75,
    viewBox: `0 0 ${size} ${size * 0.75}`,
    role: "img",
    "aria-label": `${left.label} 와 ${right.label} 의 공통 의미: ${intersection}`
  }, /*#__PURE__*/React.createElement("circle", {
    cx: cx - offset,
    cy: size * 0.37,
    r: r,
    fill: left.color,
    opacity: "0.5"
  }), /*#__PURE__*/React.createElement("circle", {
    cx: cx + offset,
    cy: size * 0.37,
    r: r,
    fill: right.color,
    opacity: "0.5"
  }), /*#__PURE__*/React.createElement("text", {
    x: cx - offset - r * 0.3,
    y: size * 0.4,
    textAnchor: "middle",
    style: {
      fontFamily: 'var(--font-family)',
      fontWeight: 700,
      fontSize: 14,
      fill: 'var(--text-primary)'
    }
  }, left.label), /*#__PURE__*/React.createElement("text", {
    x: cx + offset + r * 0.3,
    y: size * 0.4,
    textAnchor: "middle",
    style: {
      fontFamily: 'var(--font-family)',
      fontWeight: 700,
      fontSize: 14,
      fill: 'var(--text-primary)'
    }
  }, right.label)), /*#__PURE__*/React.createElement("figcaption", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      color: 'var(--text-secondary)',
      textAlign: 'center'
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      color: 'var(--text-primary)'
    }
  }, "\uACF5\uD1B5:"), " ", intersection));
}
Object.assign(__ds_scope, { VennDiagram });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/VennDiagram.jsx", error: String((e && e.message) || e) }); }

// components/forms/Input.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickInput — text field (직접입력 주제 / 텍스트 턴 입력).
 * radius.8, border.strong rest → brand.primary focus. Error: state.error border + helper.
 * States (B.2): disabled alpha .38, focused brand.primary, error border + helper text.
 */
function Input({
  value,
  onChange,
  placeholder,
  label,
  helper,
  error = false,
  disabled = false,
  trailingIcon,
  onTrailingClick,
  style,
  ...rest
}) {
  const [focused, setFocused] = React.useState(false);
  const borderColor = error ? 'var(--state-error)' : focused ? 'var(--brand-primary)' : 'var(--border-strong)';
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      opacity: disabled ? 0.38 : 1,
      ...style
    }
  }, label && /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-section-label-size)',
      fontWeight: 'var(--font-weight-bold)',
      color: 'var(--text-secondary)'
    }
  }, label), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-sm)',
      background: 'var(--surface-card)',
      border: `1.5px solid ${borderColor}`,
      borderRadius: 'var(--radius-8)',
      padding: '12px 14px',
      transition: 'border-color var(--motion-duration-fast) var(--motion-ease-standard)'
    }
  }, /*#__PURE__*/React.createElement("input", _extends({
    value: value,
    onChange: onChange,
    placeholder: placeholder,
    disabled: disabled,
    onFocus: () => setFocused(true),
    onBlur: () => setFocused(false),
    style: {
      flex: 1,
      border: 'none',
      outline: 'none',
      background: 'transparent',
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-body-size)',
      color: 'var(--text-primary)',
      minWidth: 0
    }
  }, rest)), trailingIcon && /*#__PURE__*/React.createElement("span", {
    onClick: onTrailingClick,
    style: {
      cursor: onTrailingClick ? 'pointer' : 'default',
      display: 'inline-flex'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: trailingIcon,
    size: 20,
    color: "var(--text-tertiary)"
  }))), helper && /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4,
      fontFamily: 'var(--font-family)',
      fontSize: 'var(--type-helper-size)',
      color: error ? 'var(--state-error)' : 'var(--text-tertiary)'
    }
  }, error && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "error",
    size: 15,
    color: "var(--state-error)"
  }), helper));
}
Object.assign(__ds_scope, { Input });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Input.jsx", error: String((e && e.message) || e) }); }

// components/forms/SegmentedControl.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickSegmentedControl — pill segmented tabs (기록 탭: WORD/SENTENCE/EXPRESSION).
 * radius.pill track; selected segment gets surface-card fill + bold weight.
 */
function SegmentedControl({
  options = [],
  value,
  onChange,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    role: "tablist",
    style: {
      display: 'inline-flex',
      gap: 4,
      padding: 4,
      background: 'var(--surface-background)',
      borderRadius: 'var(--radius-pill)',
      ...style
    }
  }, rest), options.map(opt => {
    const val = typeof opt === 'string' ? opt : opt.value;
    const lbl = typeof opt === 'string' ? opt : opt.label;
    const selected = val === value;
    return /*#__PURE__*/React.createElement("button", {
      key: val,
      type: "button",
      role: "tab",
      "aria-selected": selected,
      onClick: () => onChange && onChange(val),
      style: {
        border: 'none',
        padding: '8px 18px',
        borderRadius: 'var(--radius-pill)',
        background: selected ? 'var(--surface-card)' : 'transparent',
        color: selected ? 'var(--text-primary)' : 'var(--text-tertiary)',
        fontFamily: 'var(--font-family)',
        fontSize: 'var(--type-helper-size)',
        fontWeight: selected ? 'var(--font-weight-bold)' : 'var(--font-weight-medium)',
        boxShadow: selected ? '0 1px 3px rgba(14,15,18,0.08)' : 'none',
        cursor: 'pointer',
        transition: 'color var(--motion-duration-fast) var(--motion-ease-standard)',
        whiteSpace: 'nowrap'
      }
    }, lbl);
  }));
}
Object.assign(__ds_scope, { SegmentedControl });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/SegmentedControl.jsx", error: String((e && e.message) || e) }); }

// components/forms/Switch.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickSwitch — on/off toggle (settings). track brand.primary on / text.tertiary off.
 * 51×31 track, 27 thumb. Disabled alpha .38.
 */
function Switch({
  checked = false,
  onChange,
  disabled = false,
  label,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    role: "switch",
    "aria-checked": checked,
    "aria-label": label,
    disabled: disabled,
    onClick: () => !disabled && onChange && onChange(!checked),
    style: {
      position: 'relative',
      width: 51,
      height: 31,
      flexShrink: 0,
      padding: 0,
      border: 'none',
      borderRadius: 'var(--radius-pill)',
      background: checked ? 'var(--brand-primary)' : 'var(--text-tertiary)',
      opacity: disabled ? 0.38 : 1,
      cursor: disabled ? 'default' : 'pointer',
      transition: 'background-color var(--motion-duration-base) var(--motion-ease-standard)',
      WebkitTapHighlightColor: 'transparent',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("span", {
    style: {
      position: 'absolute',
      top: 2,
      left: checked ? 22 : 2,
      width: 27,
      height: 27,
      borderRadius: '50%',
      background: '#FFFFFF',
      boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
      transition: 'left var(--motion-duration-base) var(--motion-ease-out)'
    }
  }));
}
Object.assign(__ds_scope, { Switch });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Switch.jsx", error: String((e && e.message) || e) }); }

// components/navigation/BottomNav.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * OneClickBottomNav — bottom navigation (the one elevation exception, 8dp).
 * Active tab: 13sp Bold + brand.primary; inactive: 11sp Normal + text.tertiary.
 * Size AND weight change together on selection.
 */
function BottomNav({
  items = [],
  value,
  onChange,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("nav", _extends({
    style: {
      display: 'flex',
      background: 'var(--surface-card)',
      boxShadow: 'var(--elevation-nav)',
      padding: '8px 8px calc(8px + env(safe-area-inset-bottom, 0px))',
      ...style
    }
  }, rest), items.map(item => {
    const active = item.value === value;
    return /*#__PURE__*/React.createElement("button", {
      key: item.value,
      type: "button",
      "aria-current": active ? 'page' : undefined,
      onClick: () => onChange && onChange(item.value),
      style: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 3,
        border: 'none',
        background: 'transparent',
        padding: '6px 0',
        cursor: 'pointer',
        color: active ? 'var(--brand-primary)' : 'var(--text-tertiary)',
        transition: 'color var(--motion-duration-fast) var(--motion-ease-standard)'
      }
    }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: item.icon,
      size: 24,
      fill: active ? 1 : 0
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: 'var(--font-family)',
        fontSize: active ? 'var(--type-tab-active-size)' : 'var(--type-tab-inactive-size)',
        fontWeight: active ? 'var(--font-weight-bold)' : 'var(--font-weight-regular)',
        lineHeight: 1.2
      }
    }, item.label));
  }));
}
Object.assign(__ds_scope, { BottomNav });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/BottomNav.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Badge = __ds_scope.Badge;

__ds_ns.Button = __ds_scope.Button;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.Icon = __ds_scope.Icon;

__ds_ns.IconButton = __ds_scope.IconButton;

__ds_ns.ListRow = __ds_scope.ListRow;

__ds_ns.SavedCard = __ds_scope.SavedCard;

__ds_ns.ChatBubble = __ds_scope.ChatBubble;

__ds_ns.MicButton = __ds_scope.MicButton;

__ds_ns.Waveform = __ds_scope.Waveform;

__ds_ns.BottomSheet = __ds_scope.BottomSheet;

__ds_ns.FeedbackSection = __ds_scope.FeedbackSection;

__ds_ns.FeedbackSheet = __ds_scope.FeedbackSheet;

__ds_ns.RewardStrip = __ds_scope.RewardStrip;

__ds_ns.VennDiagram = __ds_scope.VennDiagram;

__ds_ns.Input = __ds_scope.Input;

__ds_ns.SegmentedControl = __ds_scope.SegmentedControl;

__ds_ns.Switch = __ds_scope.Switch;

__ds_ns.BottomNav = __ds_scope.BottomNav;

})();
