/* ==========================================================================
   app.js —— 演示台装配
   --------------------------------------------------------------------------
   一个页面里同时给出：
     ① 可交互真机演示（列表⇄编辑器⇄搜索⇄设置 真的能走通）
     ② 全部页面的实时总览（所有视图共享同一份数据，改一处其它同步）
     ③ 小组件三种尺寸（4×3 / 3×2 / 2×2，2×2 会自动收起顶栏）
     ④ 组件库（卡片、分段 tab、按钮、勾选圈、空状态…）
     ⑤ 设计令牌
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.app = (function () {
  const PAL = [
    ['--c-canvas', '页面底 Canvas'], ['--c-surface', '纸面 Surface'],
    ['--c-ink', '主文字 Ink'], ['--c-ink-2', '次要 Ink-2'], ['--c-ink-3', '更弱 Ink-3'],
    ['--c-line', '发丝线 Line'],
    ['--c-accent', '强调色 纯黑'], ['--c-accent-soft', '强调浅底'],
    ['--c-card', '条目底 Card'], ['--c-card-done', '已完成条目']
  ];

  const el = (sel, root = document) => root.querySelector(sel);
  const esc = (s) => BitSay.ui.esc(s);

  /* ---------------- 手机外壳 ---------------- */
  function phoneShell(label, opts = {}) {
    const wrap = document.createElement('div');
    wrap.className = 'col';
    wrap.innerHTML = `
      <div class="phone ${opts.small ? 'phone--sm' : ''}">
        <div class="phone__screen">
          <div class="phone__notch"></div>
          <div class="statusbar"><span>19:48</span><span class="statusbar__icons">5G ▮▮▮ 72%</span></div>
          <div class="host"></div>
        </div>
      </div>
      <div class="col__cap">${esc(label)}</div>`;
    return { wrap, host: el('.host', wrap) };
  }

  /* ---------------- ① 可交互真机 ---------------- */
  function mountDemo(root) {
    const box = el('#demo', root);
    const { wrap, host } = phoneShell('可交互：点条目 / 长按多选 / + 新建 / 搜索 / 设置');
    box.appendChild(wrap);

    // 旁边放一块真桌面 + 小组件，和手机共用同一份数据
    const hs = homeScreen('真实桌面：小组件与手机同一份数据', '2x5', 'note');
    box.appendChild(hs.wrap);
    BitSay.pages.widget.mount(hs.mount, {
      size: '2x5',
      onOpen: (id) => show('editor', { id: BitSay.store.find(id)?.kind }),
      onNew: (kind) => show('editor', { kind, quick: true }),
      onSearch: () => show('search'),
      onOpenApp: () => show('list')
    });

    let current = null;
    function show(name, arg) {
      if (current && current.unmount) current.unmount();
      host.innerHTML = '';
      const common = { onBack: () => show('list'), onOpen: (id) => show('editor', { id }) };
      switch (name) {
        case 'editor':
          current = BitSay.pages.editor.mount(host, {
            id: arg && arg.id, kind: (arg && arg.kind) || 'note',
            quickCapture: !!(arg && arg.quick),
            onBack: () => show('list'),
            onDelete: () => show('list')
          });
          break;
        case 'search':
          current = BitSay.pages.search.mount(host, {
            onBack: () => show('list'),
            onOpen: (id) => show('editor', { id: BitSay.store.find(id)?.kind })
          });
          // 搜索页打开后要真的跳到对应条目：这里直接用 id 打开
          break;
        case 'settings':
          current = BitSay.pages.settings.mount(host, {
            onBack: () => show('list'),
            onTheme: (k) => applyThemeSetting(k)
          });
          break;
        default:
          current = BitSay.pages.list.mount(host, {
            onOpen: (id) => show('editor', { id, kind: BitSay.store.find(id)?.kind }),
            onNew: (kind) => show('editor', { kind, quick: true }),
            onSearch: () => show('search'),
            onSettings: () => show('settings')
          });
      }
    }
    show('list');
  }

  /* ---------------- ② 页面总览 ---------------- */
  function mountOverview(root) {
    const grid = el('#overview', root);
    const defs = [
      ['列表 · 笔记', (h) => BitSay.pages.list.mount(h, { tab: 'note', lockTab: true })],
      ['列表 · 待办', (h) => BitSay.pages.list.mount(h, { tab: 'todo', lockTab: true })],
      ['列表 · 多选态', (h) => BitSay.pages.list.mount(h, { tab: 'note', lockTab: true, selecting: true })],
      ['编辑器 · 查看已有', (h) => BitSay.pages.editor.mount(h, {
        id: BitSay.store.notes()[0]?.id, kind: 'note', autoFocus: false, onBack() {} })],
      ['编辑器 · 新建（快捷记录）', (h) => BitSay.pages.editor.mount(h, {
        kind: 'note', quickCapture: true, autoFocus: false, onBack() {} })],
      ['搜索页', (h) => BitSay.pages.search.mount(h, { autoFocus: false, onBack() {} })],
      ['设置页', (h) => BitSay.pages.settings.mount(h, { onBack() {}, onTheme: applyThemeSetting })]
    ];
    defs.forEach(([label, mount]) => {
      const cell = document.createElement('div');
      cell.className = 'grid__cell';
      const { wrap, host } = phoneShell(label, { small: true });
      cell.appendChild(wrap);
      grid.appendChild(cell);
      mount(host);
    });
    // 搜索页预填一个关键词，好看出"切分类保留关键词"的效果
    const q = el('#overview .phone:nth-of-type(1)');
    void q;
    const inputs = grid.querySelectorAll('.search-page .field__input');
    inputs.forEach((inp) => {
      inp.value = 'Q4';
      inp.dispatchEvent(new Event('input'));
    });
  }

  /* ---------------- ③ 桌面小组件（真实桌面） ---------------- */
  const HS_ICONS = [
    ['日历', '1', 'linear-gradient(#fff,#f2f2f2)', '#E5484D', '#333'],
    ['原子笔记', 'N', 'linear-gradient(#FFD84D,#F5B301)', '#7A5B00', '#7A5B00'],
    ['电子邮件', '✉', 'linear-gradient(#7FC4F0,#4A9FD8)', '#fff', '#fff'],
    ['豆包', '◍', 'linear-gradient(#F6D9C6,#E8B79A)', '#8A5A3B', '#8A5A3B'],
    ['办公', 'OA', 'linear-gradient(#F07B2A,#D95F10)', '#fff', '#fff'],
    ['微信', '❝', 'linear-gradient(#5BD16A,#25A63C)', '#fff', '#fff'],
    ['音乐', '♪', 'linear-gradient(#E5484D,#C0262B)', '#fff', '#fff']
  ];
  const DOCK = [
    ['电话', '✆', 'linear-gradient(#5BD16A,#1FA83A)'],
    ['信息', '💬', 'linear-gradient(#8FE0A8,#4FBF77)'],
    ['相机', '◉', 'linear-gradient(#4A4A4A,#1F1F1F)'],
    ['浏览器', 'X', 'linear-gradient(#4FA8E8,#1E6FB8)']
  ];

  function homeScreen(label) {
    const wrap = document.createElement('div');
    wrap.className = 'col';
    const icons = HS_ICONS.map(([name, glyph, bg, fg]) => `
      <div class="hs__icon"><div class="glyph" style="background:${bg};color:${fg}">${glyph}</div>
        <div class="cap">${esc(name)}</div></div>`).join('');
    const dock = DOCK.map(([, glyph, bg]) => `<div class="glyph" style="background:${bg}">${glyph}</div>`).join('');
    // 结构必须与 css/pages/homescreen.css 一致：槽位/图标列/Dock 都是 .hs 的**直接子元素**，
    // 否则 .hs > * 的层级规则命中不到，草地会把小组件盖住
    wrap.innerHTML = `
      <div class="hs-phone">
        <div class="hs">
          <div class="statusbar"><span>17:15</span>
            <span class="statusbar__icons">4.40 KB/s &nbsp;5G &nbsp;▮▮▮&nbsp; 41</span></div>
          <div class="hs__slot"><div class="hs-mount"></div></div>
          <div class="hs__name">比特记</div>
          <div class="hs__icons">${icons}</div>
          <div class="hs__dots"><i></i><i class="on"></i><i></i><i></i><i></i></div>
          <div class="hs__dock">${dock}</div>
        </div>
      </div>
      <div class="col__cap">${esc(label)}</div>`;
    return { wrap, mount: el('.hs-mount', wrap) };
  }

  function mountWidgets(root) {
    const box = el('#widgets', root);
    const specs = [
      ['竖长 2×5 · 206 × 535 dp（你桌面上的形态）', '2x5', 'note'],
      ['中等 2×3 · 少几行', '2x3', 'todo'],
      ['最小 2×2 · 装不下「顶栏 + 3 行」→ 顶栏自动收起', '2x2', 'note']
    ];
    specs.forEach(([label, size, kind]) => {
      const { wrap, mount } = homeScreen(label);
      box.appendChild(wrap);
      BitSay.pages.widget.mount(mount, {
        size, kind, interactive: true,
        onOpen: () => el('#demo')?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
        onSearch: () => el('#demo')?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
        onOpenApp: () => el('#demo')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      });
    });
  }

  /* ---------------- ④ 组件库 ---------------- */
  function mountComponents(root) {
    const box = el('#components', root);
    const I = BitSay.icons, U = BitSay.ui;
    const notes = BitSay.store.notes();
    const todos = BitSay.store.todos();
    box.innerHTML = `
      <div class="knob">
        <div class="knob__cap">条目卡片（笔记 · 颜色按序轮转）</div>
        <div class="demo-list">${notes.slice(0, 4).map((it, i) => U.card(it, i, {})).join('')}</div>
      </div>
      <div class="knob">
        <div class="knob__cap">条目卡片（待办 · 只有冷色，含已完成）</div>
        <div class="demo-list">${todos.map((it, i) => U.card(it, i, {})).join('')}</div>
      </div>
      <div class="knob">
        <div class="knob__cap">多选态（勾选圈 + 去掉待办完成圈）</div>
        <div class="demo-list">${notes.slice(0, 3).map((it, i) =>
          U.card(it, i, { selecting: true, selected: i < 2 })).join('')}</div>
      </div>
      <div class="knob">
        <div class="knob__cap">分段 tab</div>
        <div style="margin:0">${U.seg([{ key: 'note', label: '笔记' },
          { key: 'todo', label: '待办' }], 'note')}</div>
        <div style="margin-top:10px">${U.seg([{ key: 'note', label: '笔记' },
          { key: 'todo', label: '待办' }], 'todo')}</div>
      </div>
      <div class="knob">
        <div class="knob__cap">图标按钮 / FAB / 勾选圈</div>
        <div class="knob__row">
          ${U.ICON_BUTTON('search')}${U.ICON_BUTTON('settings')}${U.ICON_BUTTON('back', 'icon-btn--ink')}
          ${U.ICON_BUTTON('delete', 'icon-btn--ink')}
          <span class="tick">${''}</span>
          <span class="tick tick--on">${I.check()}</span>
          <span class="fab" style="position:static;width:46px;height:46px">${I.plus()}</span>
        </div>
      </div>
      <div class="knob">
        <div class="knob__cap">空状态</div>
        <div class="empty" style="height:88px">还没有笔记
点 + 写下第一条</div>
      </div>`;
    // 组件库里的勾选圈也让它能点
    box.querySelectorAll('.card').forEach((node) => {
      const id = Number(node.dataset.id);
      node.addEventListener('click', (e) => {
        if (e.target.closest('[data-act="tick"]')) { e.stopPropagation(); BitSay.store.toggleDone(id); }
      });
    });
  }

  /* ---------------- ⑤ 调色板 ---------------- */
  function mountPalette(root) {
    const box = el('#palette', root);
    const cs = getComputedStyle(document.body);
    box.innerHTML = PAL.map(([v, label]) => {
      const hex = cs.getPropertyValue(v).trim().toUpperCase();
      return `<div class="chip"><div class="sq" style="background:var(${v})"></div>
        <div class="chip__nm">${esc(label)}</div><div class="chip__hx">${esc(hex)}</div></div>`;
    }).join('');
  }

  /* ---------------- 亮度 ---------------- */
  function setDark(on) {
    document.body.dataset.dark = on ? '1' : '0';
    document.querySelectorAll('#modes .btn').forEach((b) =>
      b.setAttribute('aria-pressed', String((b.dataset.d === '1') === on)));
    mountPalette(document);
  }
  function applyThemeSetting(k) {
    if (k === 'dark') setDark(true);
    else if (k === 'light') setDark(false);
  }

  /* ---------------- 启动 ---------------- */
  function boot() {
    document.querySelectorAll('#modes .btn').forEach((b) =>
      b.addEventListener('click', () => setDark(b.dataset.d === '1')));
    el('#reset').addEventListener('click', () => BitSay.store.reset());

    // 允许用 URL 直接指定亮度，方便分享：index.html?dark=1
    const wantDark = new URLSearchParams(location.search).get('dark') === '1';

    mountDemo(document);
    mountOverview(document);
    mountWidgets(document);
    mountComponents(document);
    BitSay.store.subscribe(() => mountComponents(document));
    setDark(wantDark);
  }
  return { boot };
})();
document.addEventListener('DOMContentLoaded', BitSay.app.boot);
