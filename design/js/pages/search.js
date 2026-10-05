/* ==========================================================================
   搜索页 —— 对应 ui/screen/SearchScreen.kt
   行为：进入即聚焦；输入实时搜（同一分类内全文匹配）；
        **切分类不清空关键词**，而是在新分类下重查；结果复用列表页的卡片。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.pages = BitSay.pages || {};
BitSay.pages.search = (function () {
  function mount(host, opts = {}) {
    const I = BitSay.icons, S = BitSay.store, U = BitSay.ui;
    let tab = opts.tab || 'note';

    const root = document.createElement('div');
    root.className = 'app-root page search-page';
    root.innerHTML = `
      <div class="search-page__bar">
        <button class="icon-btn icon-btn--ink" type="button" data-act="back" title="返回">${I.back()}</button>
        <label class="field inked">
          ${I.search()}
          <input class="field__input" data-el="q" type="search" placeholder="搜索" autocomplete="off">
        </label>
      </div>
      <div class="seg-host" data-el="tabs"></div>
      <div class="list" data-el="list"></div>`;
    host.appendChild(root);

    const el = (n) => root.querySelector(`[data-el="${n}"]`);
    const input = el('q');
    if (opts.autoFocus !== false) setTimeout(() => input.focus(), 60);

    function renderTabs() {
      el('tabs').innerHTML = U.seg([
        { key: 'note', label: '笔记' },
        { key: 'todo', label: '待办' }
      ], tab);
    }

    function renderResults() {
      const q = input.value;
      if (!q.trim()) {
        el('list').innerHTML = U.emptyState({
          title: '想找什么？',
          hint: '输入关键词，笔记和待办一起搜',
        });
        return;
      }
      const hits = S.search(tab, q);          // ← 同一个词，在当前分类下重查
      if (!hits.length) {
        el('list').innerHTML = U.emptyState({ title: '没找到', hint: '换个词试试' });
        return;
      }
      el('list').innerHTML = hits.map((it, i) =>
        U.card(it, i, {})).join('');
      el('list').querySelectorAll('.card').forEach((node) => {
        node.addEventListener('click', (e) => {
          if (e.target.closest('[data-act="tick"]')) return;
          opts.onOpen && opts.onOpen(Number(node.dataset.id));
        });
      });
    }

    input.addEventListener('input', renderResults);   // 真实 App 有 250ms 防抖，这里即时即可
    root.addEventListener('click', (e) => {
      const seg = e.target.closest('[data-seg]');
      if (seg) { tab = seg.dataset.seg; renderTabs(); renderResults(); return; }
      const tick = e.target.closest('[data-act="tick"]');
      if (tick) { e.stopPropagation(); S.toggleDone(Number(tick.closest('.card').dataset.id)); return; }
      if (e.target.closest('[data-act="back"]')) opts.onBack && opts.onBack();
    });

    const unsub = S.subscribe(renderResults);   // 数据变了，结果跟着刷新
    renderTabs(); renderResults();
    return { unmount: unsub, focus: () => input.focus() };
  }
  return { mount };
})();
