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
   * @param opts  { tab:'note'|'todo', lockTab:bool, selecting:bool, onOpen(item), onNew(kind) }
   */
  function mount(host, opts = {}) {
    const I = BitSay.icons;
    let tab = opts.tab || 'note';
    let selecting = !!opts.selecting;
    let selection = new Set();
    // 只是为了"总览"里能直接看到多选长什么样：先勾上两条
    if (selecting) S().list(tab).slice(0, 2).forEach((it) => selection.add(it.id));

    const root = document.createElement('div');
    root.className = 'app-root page list-page' + (selecting ? ' list-page--selecting' : '');
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
      el('head').className = 'topbar';
      el('head').innerHTML = `
        <div class="topbar__titles">
          <h3 class="topbar__title"><span class="hl">${tab === 'note' ? '笔记' : '待办'}</span></h3>
          <div class="topbar__sub">${tab === 'note'
            ? `${c.notes} 条笔记`
            : `${c.todos} 个待办 · ${c.openTodos} 个未完成`}</div>
        </div>
        <div class="topbar__actions">
          <button class="icon-btn" type="button" data-act="search" title="搜索">${I.search()}</button>
          <button class="icon-btn" type="button" data-act="settings" title="设置">${I.settings()}</button>
        </div>`;
    }

    function renderTabs() {
      el('tabs').innerHTML = selecting ? '' : U().seg([
        { key: 'note', label: '笔记' },
        { key: 'todo', label: '待办' }
      ], tab);
    }

    function renderList() {
      const items = S().list(tab);
      if (!items.length) {
        const note = tab === 'note';
        el('list').innerHTML = `<div class="empty">
          <div class="empty__title">${note ? '还没有笔记' : '今天没事要做'}</div>
          <div class="empty__hint">点右下角的 <b>+</b> ${note ? '写下第一条' : '加一条待办'}</div>
        </div>`;
        return;
      }
      el('list').innerHTML = items.map((it, i) =>
        U().card(it, i, { selecting, selected: selection.has(it.id) })).join('');
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
              S().removeMany([...selection]);
              U().toast(root, `已删除 ${selection.size} 项`);
              selecting = false; selection.clear(); update();
            }
            return;
        }
      }

      const tick = e.target.closest('[data-act="tick"]');
      if (tick) {
        e.stopPropagation();
        S().toggleDone(Number(tick.closest('.card').dataset.id));
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
