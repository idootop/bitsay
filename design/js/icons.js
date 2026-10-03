/* ==========================================================================
   icons.js —— 图标（内联 SVG）
   对应 res/drawable/ic_*.xml。要换图标只改这里，所有页面一起变。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.icons = (function () {
  const P = {
    back:     'M15 5l-7 7 7 7',
    search:   'M17.6 17.6L21 21',
    settings: 'M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6.4 6.4l1.4 1.4M16.2 16.2l1.4 1.4M17.6 6.4l-1.4 1.4M7.8 16.2l-1.4 1.4',
    plus:     'M12 5.5v13M5.5 12h13',
    close:    'M6.4 6.4l11.2 11.2M17.6 6.4L6.4 17.6',
    check:    'M5 12.8l4.6 4.4L19 7.4',
    delete:   'M5 7h14M10 7V5.2h4V7M6.6 7l.9 11.4h9l.9-11.4',
    exportIc: 'M12 15.5V4.6M8.2 8.4L12 4.6l3.8 3.8M5 15v3.4h14V15',
    importIc: 'M12 4.5v10.9M8.2 11.6L12 15.4l3.8-3.8M5 15v3.4h14V15',
    theme:    'M12 3.6a8.4 8.4 0 100 16.8 4.2 4.2 0 010-8.4 4.2 4.2 0 000-8.4z',
    openApp:  'M6.6 9.5h10.8M9.4 15.2l2 1.9 3.2-3.6',
    widget:   '',
    lang:     'M4 6.4h9M8.5 4.4v2M5.4 11.6c1.5 1.3 3.2 2 5.2 2.2M13.4 19.6l3.4-8.4 3.4 8.4M14.6 16.8h4.4'
  };
  const wrap = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;

  const I = {
    back:     () => wrap(`<path d="${P.back}"/>`),
    search:   () => wrap(`<circle cx="11" cy="11" r="6.2"/><path d="${P.search}"/>`),
    settings: () => wrap(`<circle cx="12" cy="12" r="3.1"/><path d="${P.settings}"/>`),
    plus:     () => wrap(`<path d="${P.plus}" stroke-width="2.4"/>`),
    close:    () => wrap(`<path d="${P.close}"/>`),
    delete:   () => wrap(`<path d="${P.delete}"/>`),
    check:    () => wrap(`<path d="${P.check}" stroke-width="2.6"/>`),
    todoOpen: () => wrap(`<circle cx="12" cy="12" r="8.6"/>`),
    todoDone: () => wrap(`<circle cx="12" cy="12" r="8.6"/><path d="M8 12.3l2.8 2.7L16.2 9.4"/>`),
    exportIc: () => wrap(`<path d="${P.exportIc}"/>`),
    importIc: () => wrap(`<path d="${P.importIc}"/>`),
    theme:    () => wrap(`<path d="${P.theme}"/>`),
    lang:     () => wrap(`<path d="${P.lang}"/>`),
    // 顶栏"进 App"：便签 + 对勾
    openApp:  () => wrap(`<rect x="3.4" y="4.6" width="17.2" height="14.8" rx="3"/>
                          <path d="M3.4 9h17.2"/><path d="${P.openApp}"/>`),
    widget:   () => wrap(`<rect x="4" y="4" width="7" height="7" rx="2"/>
                          <rect x="13" y="4" width="7" height="7" rx="2"/>
                          <rect x="4" y="13" width="7" height="7" rx="2"/>
                          <rect x="13" y="13" width="7" height="7" rx="2"/>`),
    // 多选时的实心/空心圆
    selected:   () => wrap(`<circle cx="12" cy="12" r="10" fill="currentColor" stroke="none"/>
                            <path d="M7.4 12.4l3 2.9 6.2-6.6" stroke="var(--c-ink)" stroke-width="2.4"/>`),
    unselected: () => wrap(`<circle cx="12" cy="12" r="9"/>`)
  };
  return I;
})();
