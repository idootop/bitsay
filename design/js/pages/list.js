/* ==========================================================================
   列表页 —— 对应 ui/screen/ListScreen.kt
   行为：切换笔记/待办 · 点条目进编辑器 · 点圆圈勾完成 · 长按进多选 · FAB 新建
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.pages = BitSay.pages || {};
BitSay.pages.list = (function () {
  const S = () => BitSay.store;
  const U = () => BitSay.ui;

  /**
   * @param host  挂载点
   * @param opts  { tab, lockTab, selecting, compactTop, onOpen, onNew, ... }
   *              compactTop = 窗口矮到放不下「大标题 + 计数 + 一整行 tab」时
   *              （手机横屏、分屏下半屏），把 tab 提到标题那一行、去掉计数行。
   */
  function mount(host, opts = {}) {
    const I = BitSay.icons;
    let tab = opts.tab || 'note';
    let selecting = !!opts.selecting;
    const compactTop = !!opts.compactTop;
    let selection = new Set();
    /* 上一帧出现过的 id。只给"这一帧新出现的"挂萌发动画 ——
       否则每次勾选重绘，整列都会重新长一遍。 */
    let prevIds = new Set();
    /* 刚刚被勾选的那条，播一次"落定" */
    let settleId = null;
    // 只是为了"总览"里能直接看到多选长什么样：先勾上两条
    if (selecting) S().list(tab).slice(0, 2).forEach((it) => selection.add(it.id));

    const root = document.createElement('div');
    root.className = 'app-root page list-page'
      + (selecting ? ' list-page--selecting' : '')
      + (compactTop ? ' list-page--compact' : '');
    root.innerHTML = `
      <header class="topbar" data-el="head"></header>
      <div class="seg-host" data-el="tabs"></div>
      <div class="list" data-el="list"></div>
      <button class="fab inked" type="button" data-el="fab" title="新建">${I.plus()}</button>`;
    host.appendChild(root);

    const el = (n) => root.querySelector(`[data-el="${n}"]`);

    /* ---------------- 渲染 ---------------- */
    function renderHead() {
      const c = S().counts();
      if (selecting) {
        el('head').className = 'selbar';
        el('head').innerHTML = `
          <button class="icon-btn icon-btn--ink" type="button" data-act="clear" title="退出多选">${I.close()}</button>
          <div class="selbar__count">已选 ${selection.size} 项</div>
          <button class="icon-btn icon-btn--ink" type="button" data-act="delete" title="删除">${I.delete()}</button>`;
        return;
      }
      const actions = `
        <div class="topbar__actions">
          <button class="icon-btn" type="button" data-act="search" title="搜索">${I.search()}</button>
          <button class="icon-btn" type="button" data-act="settings" title="设置">${I.settings()}</button>
        </div>`;
      // 紧凑顶栏里两个图标**分开站**：设置放最左、搜索放最右，tab 夹在中间。
      // 两个 40dp 的按钮一样宽，所以 tab 正好落在中线上 —— 原来把两个都堆在右边，
      // 整行会头重脚轻，tab 又被挤到左边、和右边的图标连成一片。
      const settingsBtn = `<button class="icon-btn" type="button" data-act="settings" title="设置">${I.settings()}</button>`;
      const searchBtn = `<button class="icon-btn" type="button" data-act="search" title="搜索">${I.search()}</button>`;
      // 矮窗口：tab 顶到标题位置，和搜索/设置同一行；计数行整条去掉。
      // 标题不再单独出现 —— tab 上的「笔记/待办」本来就是标题，
      // 两行说同一件事的时候，先砍掉的是比较小的那一行。
      el('head').className = compactTop ? 'topbar topbar--compact' : 'topbar';
      el('head').innerHTML = compactTop
        ? `${settingsBtn}<div class="seg-slot" data-el="tabs-inline"></div>${searchBtn}`
        : `<div class="topbar__titles">
             <h3 class="topbar__title">${tab === 'note' ? '笔记' : '待办'}</h3>
             <div class="topbar__sub">${tab === 'note'
               ? `${c.notes} 条笔记`
               : `${c.todos} 个待办 · ${c.openTodos} 个未完成`}</div>
           </div>${actions}`;
    }

    function renderTabs() {
      // 紧凑顶栏把 tab 塞进了 header，这里每次都重新找挂载点
      const host = root.querySelector('[data-el="tabs-inline"]') || el('tabs');
      if (!host) return;
      host.innerHTML = selecting ? '' : U().seg([
        { key: 'note', label: '笔记' },
        { key: 'todo', label: '待办' }
      ], tab, compactTop);
    }

    function renderList() {
      const items = S().list(tab);
      if (!items.length) {
        const note = tab === 'note';
        el('list').innerHTML = U().emptyState({
          title: note ? '还没有笔记' : '今天没事要做',
          hint: `点右下角的 <b>+</b> ${note ? '写下第一条' : '加一条待办'}`,
        });
        return;
      }
      el('list').innerHTML = items.map((it, i) =>
        U().card(it, i, {
          selecting,
          selected: selection.has(it.id),
          enter: !prevIds.has(it.id),
          settle: it.id === settleId,
        })).join('');
      prevIds = new Set(items.map((it) => it.id));
      settleId = null;
      el('list').querySelectorAll('.card').forEach((node) => {
        const id = Number(node.dataset.id);
        U().longPress(node, () => {
          if (!selecting) { selecting = true; selection = new Set([id]); update(); }
        });
        node.addEventListener('click', (e) => {
          if (e.target.closest('[data-act="tick"]')) return;   // 圆圈自己处理
          if (selecting) {
            selection.has(id) ? selection.delete(id) : selection.add(id);
            if (!selection.size) selecting = false;
            update();
          } else {
            opts.onOpen && opts.onOpen(id);
          }
        });
      });
    }

    function update() {
      root.classList.toggle('list-page--selecting', selecting);
      renderHead(); renderTabs(); renderList();
    }


    /* ---------------- 事件 ---------------- */
    root.addEventListener('click', (e) => {
      const seg = e.target.closest('[data-seg]');
      if (seg) { tab = seg.dataset.seg; update(); return; }

      const act = e.target.closest('[data-act]');
      if (act) {
        switch (act.dataset.act) {
          case 'search':   opts.onSearch && opts.onSearch(); return;
          case 'settings': opts.onSettings && opts.onSettings(); return;
          case 'clear':    selecting = false; selection.clear(); update(); return;
          case 'delete':
            if (selection.size) {
              const n = selection.size;
              // 不可撤销 → 先确认，确认按钮是危险色。和 App 的批量删除一致。
              U().confirm(root, `删除这 ${n} 项？`, '删除后无法恢复。', '删除',
                () => {
                  S().removeMany([...selection]);
                  U().toast(root, `已删除 ${n} 项`);
                  selecting = false; selection.clear(); update();
                });
            }
            return;
        }
      }

      const tick = e.target.closest('[data-act="tick"]');
      if (tick) {
        e.stopPropagation();
        const id = Number(tick.closest('.card').dataset.id);
        /* 只有"勾上"才播落定；取消勾选是把动作倒回去，不该有庆祝感 */
        if (!S().find(id)?.done) settleId = id;
        S().toggleDone(id);
        return;
      }

      if (e.target.closest('[data-el="fab"]')) {
        opts.onNew && opts.onNew(tab);
      }
    });

    const unsub = S().subscribe(update);
    update();
    return { update, unmount: unsub, openSelection() { selecting = true; update(); } };
  }

  return { mount };
})();
