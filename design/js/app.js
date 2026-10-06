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
    ['--c-card', '条目底 Card'], ['--c-card-done', '已完成条目'],
    ['--c-divider', '宽屏分栏线']
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

    /** 首页列表当前是笔记还是待办 —— 搜索页照它决定搜哪一种 */
    const listTab = () => host.querySelector('.seg__item--on')?.dataset.seg || 'note';

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
            kind: listTab(),
            onBack: () => show('list'),
            onOpen: (id) => show('editor', { id: BitSay.store.find(id)?.kind })
          });
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

  /* ---------------- ② 宽屏两栏（横屏 / 平板 / 折叠屏展开） ----------------
     和 Android 侧 BitSayRoot 的宽屏分支一一对应：
     左栏永远是列表（App 的脊柱，不该被推走），右栏 = 当前 screen。
     手机 360dp 宽时这一整套不生效，走单栏前进/后退。 */
  function wideShell(w, h, label) {
    const wrap = document.createElement('div');
    wrap.className = 'col';
    wrap.innerHTML = `
      <div class="wide" style="--w:${w}px;--h:${h}px">
        <div class="wide__screen">
          <div class="wide__pane wide__pane--list"><div class="wlist"></div></div>
          <div class="wide__split"></div>
          <div class="wide__pane wide__pane--detail"><div class="wdetail"></div></div>
        </div>
      </div>
      <div class="col__cap">${esc(label)}</div>`;
    return { wrap, list: el('.wlist', wrap), detail: el('.wdetail', wrap) };
  }

  /** 列表页返回的是退订函数，编辑器/设置返回的是 {unmount} —— 两种都要能收 */
  function dispose(inst) {
    if (typeof inst === 'function') inst();
    else if (inst && inst.unmount) inst.unmount();
  }

  function mountWide(root) {
    const box = el('#wide', root);
    if (!box) return;
    const specs = [
      [800, 360, '手机 · 横屏 800×360dp（宽够、矮 —— 两栏反而救了键盘）'],
      [720, 900, '折叠屏 · 展开竖屏 720×900dp'],
      [1120, 700, '平板 · 横屏 1120×700dp（右栏正文被 --measure 收住）']
    ];

    // 和 Compose 的 CompactHeaderHeight 同一个门限
    const COMPACT_HEAD_H = 480;

    specs.forEach(([w, h, label]) => {
      const { wrap, list, detail } = wideShell(w, h, label);
      box.appendChild(wrap);

      let inst = null;
      const clear = () => { dispose(inst); inst = null; detail.innerHTML = ''; };

      const blank = () => {
        clear();
        // 只有一句。原来还有一句"宽屏下列表留在原地…"——那是写给评审的，
        // 用户脑子里没有"宽屏"这个概念，他只看到列表还在那儿。
        detail.innerHTML = `<div class="wide__blank">
          ${BitSay.brand.svg(46)}
          <div class="wide__blank__text">从左侧选一条，或按 + 新建</div>
        </div>`;
      };

      /* 右栏 = 当前 screen。和手机唯一的差别是「列表」这一档：
         手机上它是整屏，宽屏上它已经在左栏了，所以右栏退回占位。 */
      const show = (name, arg) => {
        clear();
        if (name === 'list') return blank();
        const host = document.createElement('div');
        host.className = 'wide__measure';
        detail.appendChild(host);
        switch (name) {
          case 'editor':
            inst = BitSay.pages.editor.mount(host, {
              id: arg && arg.id, kind: (arg && arg.kind) || 'note',
              quickCapture: !!(arg && arg.quick),
              onBack: () => show('list'),
              onDelete: () => show('list')
            });
            break;
          case 'search':
            inst = BitSay.pages.search.mount(host, {
              // 搜索只搜一种：跟着左栏当时那个 tab
              kind: listTab(),
              onBack: () => show('list'),
              onOpen: (id) => show('editor', { id: BitSay.store.find(id)?.kind })
            });
            break;
          case 'settings':
            inst = BitSay.pages.settings.mount(host, {
              onBack: () => show('list'),
              onTheme: (k) => applyThemeSetting(k)
            });
            break;
        }
      };

      /* 左栏当前是笔记还是待办 —— 搜索页照它决定搜哪一种。
         不去问 store：列表自己的 tab 是它自己的状态。 */
      const listTab = () => list.querySelector('.seg__item--on')?.dataset.seg || 'note';

      // 左栏只挂一次，全程不重建 —— 这正是宽屏布局的意义
      BitSay.pages.list.mount(list, {
        // 800×360 那台会走紧凑顶栏，另外两台不会
        compactTop: h < COMPACT_HEAD_H,
        onOpen: (id) => show('editor', { id, kind: BitSay.store.find(id)?.kind }),
        onNew: (kind) => show('editor', { kind, quick: true }),
        onSearch: () => show('search'),
        onSettings: () => show('settings')
      });
      blank();
    });
  }

  /* ---------------- ③ 页面总览 ---------------- */
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
      ['搜索页 · 搜笔记', (h) => BitSay.pages.search.mount(h, {
        kind: 'note', autoFocus: false, onBack() {} })],
      ['搜索页 · 搜待办', (h) => BitSay.pages.search.mount(h, {
        kind: 'todo', autoFocus: false, onBack() {} })],
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

  /* ---------------- ③.5 空状态 ---------------- */
  function mountEmptyStates(root) {
    const box = el('#empties', root);
    if (!box) return;
    const S = BitSay.store;

    /* 让某个页面在"空数据"下渲染一帧，然后立刻退订把它**冻住**，
       再把数据还原 —— 这样设计台里能同时看到"有数据"和"没数据"两种样子，
       而不需要给页面加任何测试专用的开关。 */
    function frozen(host, mount, label) {
      const real = S.state.items;
      S.state.items = [];
      const inst = mount(host);
      if (inst && inst.unmount) inst.unmount();   // list / search 返回的是退订函数
      S.state.items = real;
      S.emit();
      return label;
    }

    const specs = [
      ['空列表 · 笔记', (h) => frozen(h, (host) =>
        BitSay.pages.list.mount(host, { tab: 'note', lockTab: true }))],
      ['空列表 · 待办', (h) => frozen(h, (host) =>
        BitSay.pages.list.mount(host, { tab: 'todo', lockTab: true }))],
      ['空搜索 · 还没输入', (h) => BitSay.pages.search.mount(h, {
        kind: 'note', autoFocus: false, onBack() {} })],
      ['空搜索 · 没有结果', (h) => {
        const inst = BitSay.pages.search.mount(h, { kind: 'note', autoFocus: false, onBack() {} });
        const i = h.querySelector('.field__input');
        i.value = 'zzz';
        i.dispatchEvent(new Event('input'));
        return inst;
      }],
      ['小组件 · 空列表', (h) => {
        const real = S.state.items;
        S.state.items = [];
        const inst = BitSay.pages.widget.mount(h, { size: '2x2', kind: 'note' });
        if (inst && inst.unmount) inst.unmount();
        S.state.items = real;
        S.emit();
      }]
    ];

    specs.forEach(([label, mount]) => {
      const cell = document.createElement('div');
      cell.className = 'grid__cell';
      if (label.indexOf('小组件') === 0) {
        const wall = document.createElement('div');
        wall.className = 'wall';
        wall.innerHTML = `<div class="wall__cap">${esc(label)}</div>`;
        cell.appendChild(wall);
        box.appendChild(cell);
        mount(wall);
      } else {
        const { wrap, host } = phoneShell(label, { small: true });
        cell.appendChild(wrap);
        box.appendChild(cell);
        mount(host);
      }
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
          <div class="hs__name">碎碎念</div>
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

  /* ---------------- ③.8 应用图标 ---------------- */
  function mountAppIcons(root) {
    const box = el('#appicons', root);
    if (!box) return;
    const { esc, md } = BitSay.ui;
    const F = BitSay.appicon.FINAL;

    const hero = el('#appicon-final', root);
    if (hero) hero.innerHTML = `
      <div class="icfinal">
        <div class="icfinal__left">
          <span class="icfinal__badge">已定稿 · 已落地</span>
          <div class="icfinal__shot">${F.svg}</div>
        </div>
        <div class="icfinal__right">
          <div class="icfinal__title">${esc(F.key)} · ${esc(F.name)}</div>
          <div class="icfinal__note">${md(F.note)}</div>
          <div class="icfinal__masks">
            <span class="icshot"><span class="mask mask--circle" style="width:66px;height:66px">${F.svg}</span><span class="cap">圆形</span></span>
            <span class="icshot"><span class="mask mask--squircle" style="width:66px;height:66px">${F.svg}</span><span class="cap">圆角方</span></span>
            <span class="icshot"><span class="mask mask--round" style="width:66px;height:66px">${F.svg}</span><span class="cap">方形</span></span>
            <span class="icshot"><span class="mask mask--circle" style="width:48px;height:48px">${F.svg}</span><span class="cap">桌面 48</span></span>
            <span class="icshot"><span class="mask mask--circle" style="width:24px;height:24px">${F.svg}</span><span class="cap">极小 24</span></span>
          </div>
          <div class="icfinal__files">已落到 <code>ic_launcher_background.xml</code> ·
            <code>ic_launcher_foreground.xml</code> · <code>ic_launcher_monochrome.xml</code> ·
            widget 的 <code>ic_widget_open_app.xml</code></div>
        </div>
      </div>`;

    box.innerHTML = BitSay.appicon.VARIANTS.filter((v) => !v.final).map((v) => `
      <div class="iccard">
        <div class="iccard__head">
          <span class="iccard__key">${esc(v.key)}</span>
          <span class="iccard__name">${esc(v.name)}</span>
          <span class="iccard__tag">${esc(v.tag)}</span>
        </div>
        <div class="iccard__row">
          <span class="icshot"><span style="width:76px;height:76px">${v.svg}</span>
            <span class="cap">108 母版</span></span>
          <span class="icshot"><span class="mask mask--circle" style="width:60px;height:60px">${v.svg}</span>
            <span class="cap">圆形</span></span>
          <span class="icshot"><span class="mask mask--squircle" style="width:60px;height:60px">${v.svg}</span>
            <span class="cap">圆角方</span></span>
          <span class="icshot"><span class="mask mask--round" style="width:60px;height:60px">${v.svg}</span>
            <span class="cap">方形</span></span>
        </div>
        <div class="iccard__sizes">
          <span class="lbl">桌面 48</span><span style="width:48px;height:48px" class="mask mask--circle">${v.svg}</span>
          <span class="lbl">极小 24</span><span style="width:24px;height:24px" class="mask mask--circle">${v.svg}</span>
        </div>
        <div class="iccard__note">${md(v.note)}</div>
      </div>`).join('');
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
          <span class="tick tick--on">${I.tickCheck()}</span>
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
    mountWide(document);
    mountOverview(document);
    mountWidgets(document);
    mountEmptyStates(document);
    mountAppIcons(document);
    mountComponents(document);
    BitSay.store.subscribe(() => mountComponents(document));
    setDark(wantDark);
  }
  return { boot };
})();
document.addEventListener('DOMContentLoaded', BitSay.app.boot);
