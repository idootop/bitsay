/* ==========================================================================
   设置页 —— 对应 ui/screen/SettingsScreen.kt
   行为：概览统计 · 导出/导入（演示用 toast 模拟） · 添加到桌面 · 主题 / 语言
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.pages = BitSay.pages || {};
BitSay.pages.settings = (function () {
  const THEMES = [
    { key: 'system', label: '跟随系统' },
    { key: 'light', label: '浅色' },
    { key: 'dark', label: '深色' }
  ];
  const LANGS = [
    { key: 'system', label: '跟随系统' },
    { key: 'zh', label: '简体中文' },
    { key: 'en', label: 'English' }
  ];
  const labelOf = (list, key) => (list.find((o) => o.key === key) || list[0]).label;

  function mount(host, opts = {}) {
    const I = BitSay.icons, S = BitSay.store, U = BitSay.ui;

    const root = document.createElement('div');
    root.className = 'app-root page settings-page';
    root.innerHTML = `
      <header class="topbar topbar--page">
        <button class="icon-btn icon-btn--ink" type="button" data-act="back" title="返回">${I.back()}</button>
        <div class="topbar__titles"><h3 class="topbar__title">设置</h3></div>
      </header>
      <div class="settings-page__body" data-el="body"></div>`;
    host.appendChild(root);
    const el = (n) => root.querySelector(`[data-el="${n}"]`);

    function render() {
      const c = S.counts();
      const th = S.state.settings.theme, lg = S.state.settings.lang;
      el('body').innerHTML = `
        <div class="sect">概览</div>
        <div class="block inked">
          <div class="block__pad">
            <div class="big-stat">${c.notes} 条笔记 · ${c.todos} 个待办</div>
            <div class="note-text" style="margin-top:5px">其中 ${c.openTodos} 个还没完成</div>
          </div>
        </div>

        <div class="sect">备份与恢复</div>
        <div class="block inked">
          <div class="srow" data-act="export">
            <span class="srow__icon">${I.exportIc()}</span>
            <div class="srow__text"><div class="srow__title">导出备份</div>
              <div class="srow__desc">把所有笔记和待办写进一个压缩包</div></div>
            <span class="srow__chev">›</span>
          </div>
          <div class="srow" data-act="import">
            <span class="srow__icon">${I.importIc()}</span>
            <div class="srow__text"><div class="srow__title">导入备份</div>
              <div class="srow__desc">从备份文件恢复 —— 可合并或覆盖</div></div>
            <span class="srow__chev">›</span>
          </div>
        </div>

        <div class="sect">桌面小组件</div>
        <div class="block inked">
          <div class="srow" data-act="pin">
            <span class="srow__icon">${I.widget()}</span>
            <div class="srow__text"><div class="srow__title">添加到桌面</div>
              <div class="srow__desc">把笔记或待办放到桌面，一点就能记</div></div>
            <span class="srow__chev">›</span>
          </div>
          <div class="block__pad" style="padding-top:0">
            <div class="note-text">长按桌面空白处 → 小组件 → 找到「比特记」</div>
          </div>
        </div>

        <div class="sect">外观</div>
        <div class="block inked">
          <div class="srow" data-act="theme">
            <span class="srow__icon">${I.theme()}</span>
            <div class="srow__text"><div class="srow__title">主题</div></div>
            <span class="srow__value">${labelOf(THEMES, th)}</span>
            <span class="srow__chev">›</span>
          </div>
          <div class="srow" data-act="lang">
            <span class="srow__icon">${I.lang()}</span>
            <div class="srow__text"><div class="srow__title">显示语言</div></div>
            <span class="srow__value">${labelOf(LANGS, lg)}</span>
            <span class="srow__chev">›</span>
          </div>
        </div>

        <div class="sect">关于</div>
        <div class="block inked">
          <div class="block__pad">
            <div style="font-weight:700">比特记</div>
            <div class="note-text" style="margin-top:3px">版本 1.0.0</div>
            <div class="note-text" style="margin-top:8px">纯文字笔记与待办，不要任何权限，数据只留在你手里。</div>
          </div>
        </div>`;
    }

    root.addEventListener('click', (e) => {
      const row = e.target.closest('[data-act]');
      if (!row) return;
      switch (row.dataset.act) {
        case 'back': opts.onBack && opts.onBack(); break;
        case 'export': U.toast(root, '已导出 7 条记录到 bitsay-20261003-1948.bitsay.gz'); break;
        case 'import': U.toast(root, '演示环境不读文件'); break;
        case 'pin':
          U.toast(root, '已请求添加到桌面；若桌面没有反应，请长按桌面手动添加'); break;
        case 'theme':
          U.choose(root, '主题', THEMES, S.state.settings.theme, (k) => {
            S.setSetting('theme', k);
            opts.onTheme && opts.onTheme(k);      // 让演示台真的跟着切亮暗
          });
          break;
        case 'lang':
          U.choose(root, '显示语言', LANGS, S.state.settings.lang,
            (k) => { S.setSetting('lang', k); U.toast(root, `语言已切到「${labelOf(LANGS, k)}」`); });
          break;
      }
    });

    const unsub = S.subscribe(render);
    render();
    return { unmount: unsub };
  }
  return { mount };
})();
