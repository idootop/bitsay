/* ==========================================================================
   设置页 —— 对应 ui/screen/SettingsScreen.kt
   顺序：关于 → 外观 → 备份与恢复（和 Android 一致）。
   备份/还原用 toast 模拟。
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
      const th = S.state.settings.theme, lg = S.state.settings.lang;
      el('body').innerHTML = `
        <div class="sect">关于</div>
        <div class="block inked">
          <div class="block__pad">
            <div class="about-row">
              <span class="app-plate">${BitSay.brand.svg(34)}</span>
              <div class="about-row__text">
                <div class="about-row__name">碎碎念</div>
                <div class="note-text">版本 1.0.0</div>
              </div>
            </div>
            <div class="note-text" style="margin-top:14px">纯文字笔记与待办，不要任何权限，数据只留在你手里。</div>
          </div>
        </div>

        <div class="sect">外观</div>
        <div class="block inked">
          <div class="srow" data-act="theme">
            <span class="srow__icon">${I.theme()}</span>
            <div class="srow__text"><div class="srow__title">主题</div></div>
            <span class="srow__value">${labelOf(THEMES, th)}</span>
          </div>
          <div class="srow" data-act="lang">
            <span class="srow__icon">${I.lang()}</span>
            <div class="srow__text"><div class="srow__title">显示语言</div></div>
            <span class="srow__value">${labelOf(LANGS, lg)}</span>
          </div>
        </div>

        <div class="sect">备份与恢复</div>
        <div class="block inked">
          <div class="srow" data-act="export">
            <span class="srow__icon">${I.exportIc()}</span>
            <div class="srow__text"><div class="srow__title">备份</div>
              <div class="srow__desc">把全部笔记和待办打包成一个备份文件</div></div>
          </div>
          <div class="srow" data-act="import">
            <span class="srow__icon">${I.importIc()}</span>
            <div class="srow__text"><div class="srow__title">还原</div>
              <div class="srow__desc">从备份文件恢复，可覆盖或并入现有内容</div></div>
          </div>
        </div>

        <div class="sect">作者</div>
        <div class="block inked">
          <div class="srow" data-act="site">
            <span class="srow__icon">${I.lang()}</span>
            <div class="srow__text"><div class="srow__title">Del Wang</div>
              <div class="srow__desc">https://del.wang</div></div>
          </div>
          <div class="srow" data-act="repo">
            <span class="srow__icon">${I.link()}</span>
            <div class="srow__text"><div class="srow__title">源代码</div>
              <div class="srow__desc">https://github.com/idootop/bitsay</div></div>
          </div>
        </div>`;
    }

    root.addEventListener('click', (e) => {
      const row = e.target.closest('[data-act]');
      if (!row) return;
      switch (row.dataset.act) {
        case 'back': opts.onBack && opts.onBack(); break;
        case 'export': U.toast(root, '已打包成一个备份文件'); break;
        case 'site': U.toast(root, 'https://del.wang'); break;
        case 'repo': U.toast(root, 'https://github.com/idootop/bitsay'); break;
        case 'import': U.toast(root, '演示环境不读文件'); break;
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
