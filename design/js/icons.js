/* ==========================================================================
   icons.js —— 图标（内联 SVG）
   对应 res/drawable/ic_*.xml。要换图标只改这里，所有页面一起变。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.icons = (function () {
  const P = {
    back:     'M15 5l-7 7 7 7',
    search:   'M17.6 17.6L21 21',
    settings: 'M9.49 4.72L10.23 2.87A9.3 9.3 0 0 1 13.77 2.87L13.47 4.44A7.7 7.7 0 0 1 17.81 6.95L19.02 5.9A9.3 9.3 0 0 1 20.79 8.97L19.28 9.49A7.7 7.7 0 0 1 19.28 14.51L20.79 15.03A9.3 9.3 0 0 1 19.02 18.1L17.81 17.05A7.7 7.7 0 0 1 13.47 19.56L13.77 21.13A9.3 9.3 0 0 1 10.23 21.13L10.53 19.56A7.7 7.7 0 0 1 6.19 17.05L4.98 18.1A9.3 9.3 0 0 1 3.21 15.03L4.72 14.51A7.7 7.7 0 0 1 4.72 9.49L3.21 8.97A9.3 9.3 0 0 1 4.98 5.9L6.19 6.95A7.7 7.7 0 0 1 10.53 4.44Z',
    plus:     'M12 5.5v13M5.5 12h13',
    close:    'M6.4 6.4l11.2 11.2M17.6 6.4L6.4 17.6',
    check:    'M5 12.8l4.6 4.4L19 7.4',
    delete:   'M5 7h14M10 7V5.2h4V7M6.6 7l.9 11.4h9l.9-11.4',
    exportIc: 'M12 15.5V4.6M8.2 8.4L12 4.6l3.8 3.8M5 15v3.4h14V15',
    importIc: 'M12 4.5v10.9M8.2 11.6L12 15.4l3.8-3.8M5 15v3.4h14V15',
    widget:   '',
  };
  const wrap = (inner) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;

  const I = {
    back:     () => wrap(`<path d="${P.back}"/>`),
    search:   () => wrap(`<circle cx="11" cy="11" r="6.2"/><path d="${P.search}"/>`),
    // 齿轮：**6 个**梯形齿的轮廓 + 中心孔。
    // 8 齿在 21px 下太密、齿间糊成一片；6 齿留得住齿形。
    // 注意别退回"圆圈 + 放射线" —— 那是太阳/亮度图标，和设置完全不是一回事。
    settings: () => wrap(`<path d="${P.settings}"/><circle cx="12" cy="12" r="3.2"/>`),
    plus:     () => wrap(`<path d="${P.plus}" stroke-width="2.4"/>`),
    close:    () => wrap(`<path d="${P.close}"/>`),
    delete:   () => wrap(`<path d="${P.delete}"/>`),
    check:    () => wrap(`<path d="${P.check}" stroke-width="2.6"/>`),
    todoOpen: () => wrap(`<circle cx="12" cy="12" r="8.6"/>`),
    // 已完成：圆圈 + 对勾。小组件用它（而不是光一个对勾），空圈/带勾才是一对
    todoDone: () => wrap(`<circle cx="12" cy="12" r="8.6"/><path d="M8 12.3l2.8 2.7L16.2 9.4"/>`),
    exportIc: () => wrap(`<path d="${P.exportIc}"/>`),
    importIc: () => wrap(`<path d="${P.importIc}"/>`),
    // 对比度：整圆 + 右半实心。原来那条路径没闭合，描出来是个逗号
    theme:    () => wrap(`<circle cx="12" cy="12" r="8.6"/>
                          <path d="M12 3.4a8.6 8.6 0 010 17.2z" fill="currentColor" stroke="none"/>`),
    // 地球：圆 + 赤道 + 经线。原来的"文A"在 20px 下笔画太密会糊
    lang:     () => wrap(`<circle cx="12" cy="12" r="8.6"/><path d="M3.4 12h17.2"/>
                          <ellipse cx="12" cy="12" rx="4.1" ry="8.6"/>`),
    // 顶栏"进 App"：便签 + 对勾
    // 品牌标记「破土」—— 和 Android 的 ic_widget_open_app.xml 是同一份几何（缩放 0.38 到 24）。
    // 这里不能复用 wrap()：破土是**填充**图形，wrap() 用的是描边。
    openApp:  () => `<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                          <path d="M4.4 21.5C4.4 18.46 7.44 16.94 12 16.94C16.56 16.94 19.6 18.46 19.6 21.5Z"/>
                          <path d="M12 21.5C11.62 17.32 11.62 13.14 12 8.96"
                                fill="none" stroke="currentColor" stroke-width="1.67" stroke-linecap="round"/>
                          <path d="M12 8.96C8.2 9.34 5.54 6.3 5.54 2.5C9.34 2.5 12 5.16 12 8.96Z"/>
                          <path d="M12 8.96C15.8 9.34 18.46 6.3 18.46 2.5C14.66 2.5 12 5.16 12 8.96Z"/>
                        </svg>`,
    widget:   () => wrap(`<rect x="4" y="4" width="7" height="7" rx="2"/>
                          <rect x="13" y="4" width="7" height="7" rx="2"/>
                          <rect x="4" y="13" width="7" height="7" rx="2"/>
                          <rect x="13" y="13" width="7" height="7" rx="2"/>`),
  };
  return I;
})();
