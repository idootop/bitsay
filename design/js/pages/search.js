/* ==========================================================================
   搜索页 —— 对应 ui/screen/SearchScreen.kt
   行为：进入即聚焦；输入实时搜；结果复用列表页的卡片。

   **只搜一种类型**，由 opts.kind 决定（笔记还是待办）—— 也就是"你从哪儿点进来的"：
   首页列表看的是哪个 tab，widget 当前显示的是哪个 tab，就搜哪个。
   这一页没有 tab 切换：输入框就是它唯一的标题，所以占位文字必须把"搜的是哪一种"说出来。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.pages = BitSay.pages || {};
BitSay.pages.search = (function () {
  function mount(host, opts = {}) {
    const I = BitSay.icons, S = BitSay.store, U = BitSay.ui;
    const kind = opts.kind || 'note';
    const kindLabel = kind === 'note' ? '笔记' : '待办';

    const root = document.createElement('div');
    root.className = 'app-root page search-page';
    root.innerHTML = `
      <div class="search-page__bar">
        <button class="icon-btn icon-btn--ink" type="button" data-act="back" title="返回">${I.back()}</button>
        <!-- 输入框里**不放放大镜**（和 Compose 的 SearchField 一致）：这一页的标题就是输入框，
             图标只会把占位文字往右挤，而占位文字恰恰是唯一说明"搜的是笔记还是待办"的地方。 -->
        <label class="field inked">
          <input class="field__input" data-el="q" type="search"
                 placeholder="搜索${kindLabel}" autocomplete="off">
        </label>
      </div>
      <div class="list" data-el="list"></div>`;
    host.appendChild(root);

    const el = (n) => root.querySelector(`[data-el="${n}"]`);
    const input = el('q');
    if (opts.autoFocus !== false) setTimeout(() => input.focus(), 60);

    function renderResults() {
      const q = input.value;
      if (!q.trim()) {
        // 只有一句。输入框就聚焦在下面，占位文字写着搜的是哪一种 ——「输入关键词，只在笔记里找」
        // 是在向用户解释这一页的实现，而他要的只是把词打进去。
        el('list').innerHTML = U.emptyState({ title: '想找什么？' });
        return;
      }
      const hits = S.search(kind, q);
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
      const tick = e.target.closest('[data-act="tick"]');
      if (tick) { e.stopPropagation(); S.toggleDone(Number(tick.closest('.card').dataset.id)); return; }
      if (e.target.closest('[data-act="back"]')) opts.onBack && opts.onBack();
    });

    const unsub = S.subscribe(renderResults);   // 数据变了，结果跟着刷新
    renderResults();
    return { unmount: unsub, focus: () => input.focus() };
  }
  return { mount };
})();
