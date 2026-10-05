/* ==========================================================================
   icons.js —— 图标（内联 SVG）
   对应 res/drawable/ic_*.xml。要换图标只改这里，所有页面一起变。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.icons = (function () {
  const P = {
    back:     'M15 5l-7 7 7 7',
    search:   'M17.6 17.6L21 21',
    settings: 'M10.66 4.42L10.39 2.84A9.3 9.3 0 0 1 13.61 2.84L13.34 4.42A7.7 7.7 0 0 1 16.42 5.69L17.33 4.38A9.3 9.3 0 0 1 19.62 6.67L18.31 7.58A7.7 7.7 0 0 1 19.58 10.66L21.16 10.39A9.3 9.3 0 0 1 21.16 13.61L19.58 13.34A7.7 7.7 0 0 1 18.31 16.42L19.62 17.33A9.3 9.3 0 0 1 17.33 19.62L16.42 18.31A7.7 7.7 0 0 1 13.34 19.58L13.61 21.16A9.3 9.3 0 0 1 10.39 21.16L10.66 19.58A7.7 7.7 0 0 1 7.58 18.31L6.67 19.62A9.3 9.3 0 0 1 4.38 17.33L5.69 16.42A7.7 7.7 0 0 1 4.42 13.34L2.84 13.61A9.3 9.3 0 0 1 2.84 10.39L4.42 10.66A7.7 7.7 0 0 1 5.69 7.58L4.38 6.67A9.3 9.3 0 0 1 6.67 4.38L7.58 5.69A7.7 7.7 0 0 1 10.66 4.42Z',
    plus:     'M12 5.5v13M5.5 12h13',
    close:    'M6.4 6.4l11.2 11.2M17.6 6.4L6.4 17.6',
    check:    'M5 12.8l4.6 4.4L19 7.4',
    delete:   'M5 7h14M10 7V5.2h4V7M6.6 7l.9 11.4h9l.9-11.4',
    exportIc: 'M12 15.5V4.6M8.2 8.4L12 4.6l3.8 3.8M5 15v3.4h14V15',
    importIc: 'M12 4.5v10.9M8.2 11.6L12 15.4l3.8-3.8M5 15v3.4h14V15',
    openApp:  'M6.6 9.5h10.8M9.4 15.2l2 1.9 3.2-3.6',
    widget:   '',
  };
  const wrap = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;

  const I = {
    back:     () => wrap(`<path d="${P.back}"/>`),
    search:   () => wrap(`<circle cx="11" cy="11" r="6.2"/><path d="${P.search}"/>`),
    // 齿轮：8 个梯形齿的轮廓 + 中心孔。
    // 注意别退回"圆圈 + 放射线" —— 那是太阳/亮度图标，和设置完全不是一回事。
    settings: () => wrap(`<path d="${P.settings}"/><circle cx="12" cy="12" r="3.1"/>`),
    plus:     () => wrap(`<path d="${P.plus}" stroke-width="2.4"/>`),
    close:    () => wrap(`<path d="${P.close}"/>`),
    delete:   () => wrap(`<path d="${P.delete}"/>`),
    check:    () => wrap(`<path d="${P.check}" stroke-width="2.6"/>`),
    todoOpen: () => wrap(`<circle cx="12" cy="12" r="8.6"/>`),
    exportIc: () => wrap(`<path d="${P.exportIc}"/>`),
    importIc: () => wrap(`<path d="${P.importIc}"/>`),
    // 对比度：整圆 + 右半实心。原来那条路径没闭合，描出来是个逗号
    theme:    () => wrap(`<circle cx="12" cy="12" r="8.6"/>
                          <path d="M12 3.4a8.6 8.6 0 010 17.2z" fill="currentColor" stroke="none"/>`),
    // 地球：圆 + 赤道 + 经线。原来的"文A"在 20px 下笔画太密会糊
    lang:     () => wrap(`<circle cx="12" cy="12" r="8.6"/><path d="M3.4 12h17.2"/>
                          <ellipse cx="12" cy="12" rx="4.1" ry="8.6"/>`),
    // 顶栏"进 App"：便签 + 对勾
    openApp:  () => wrap(`<rect x="3.4" y="4.6" width="17.2" height="14.8" rx="3"/>
                          <path d="M3.4 9h17.2"/><path d="${P.openApp}"/>`),
    widget:   () => wrap(`<rect x="4" y="4" width="7" height="7" rx="2"/>
                          <rect x="13" y="4" width="7" height="7" rx="2"/>
                          <rect x="4" y="13" width="7" height="7" rx="2"/>
                          <rect x="13" y="13" width="7" height="7" rx="2"/>`),
  };
  return I;
})();
