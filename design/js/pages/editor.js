/* ==========================================================================
   编辑器 —— 对应 ui/screen/EditorScreen.kt
   行为：没有保存按钮；输入 400ms 后"落库"，底部状态从「正在输入…」变「✓ 已自动保存 · 刚刚」；
        返回即离开（内容已存）；清空内容 = 删除。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.pages = BitSay.pages || {};
BitSay.pages.editor = (function () {
  const SAVE_DEBOUNCE = 400;   // 对应 WriteThrottle.AUTO_SAVE_THROTTLE_MS

  /**
   * @param host
   * @param opts { id:number|null, kind:'note'|'todo', onBack(), onDelete(), quickCapture:bool }
   */
  function mount(host, opts = {}) {
    const I = BitSay.icons;
    const U = () => BitSay.ui;   // 和 list.js 同一个约定：U() 取模块
    const store = BitSay.store;
    let id = opts.id ?? null;
    let kind = opts.kind || 'note';
    let dirty = false;
    let timer = null;
    let savedAt = id ? store.find(id)?.updatedAt || 0 : 0;

    const root = document.createElement('div');
    root.className = 'app-root page editor-page';
    root.innerHTML = `
      <header class="topbar topbar--page">
        <button class="icon-btn icon-btn--ink" type="button" data-act="back" title="返回">${I.back()}</button>
        <div class="topbar__titles"><h3 class="topbar__title" data-el="kind"></h3></div>
        <button class="icon-btn" type="button" data-act="delete" title="删除" data-el="del">${I.delete()}</button>
      </header>
      <div class="editor-page__body">
        <div class="sheet inked">
          <textarea class="sheet__input" data-el="ta" spellcheck="false"></textarea>
          <div class="sheet__ph" data-el="ph"></div>
        </div>
        <div class="editor-page__foot">
          <div class="editor-page__status" data-el="status"></div>
          <div data-el="created"></div>
        </div>
      </div>`;
    host.appendChild(root);

    const el = (n) => root.querySelector(`[data-el="${n}"]`);
    const ta = el('ta');

    /* ---------------- 初始内容 ---------------- */
    const item = id ? store.find(id) : null;
    ta.value = item ? item.text : '';
    if (opts.autoFocus !== false) setTimeout(() => ta.focus(), 60);

    /* ---------------- 渲染（只动状态区，绝不重建 textarea） ---------------- */
    function renderChrome() {
      el('kind').textContent = kind === 'note' ? '笔记' : '待办';
      el('del').style.visibility = id ? 'visible' : 'hidden';
      el('ph').textContent = kind === 'note' ? '写点什么…' : '要做什么？';
      el('ph').style.display = ta.value ? 'none' : '';

      const st = el('status');
      if (dirty) {
        st.className = 'editor-page__status';
        st.textContent = '正在输入…';
      } else if (savedAt) {
        st.className = 'editor-page__status editor-page__status--saved';
        st.innerHTML = `${I.check()}<span>已自动保存 · ${store.relative(savedAt)}</span>`;
      } else if (opts.quickCapture) {
        st.className = 'editor-page__status';
        st.textContent = '返回时自动保存';
      } else {
        st.className = 'editor-page__status';
        st.textContent = '边打字边保存，关掉也不会丢';
      }
      const created = id ? store.find(id)?.createdAt : 0;
      el('created').textContent = created ? `创建于 ${store.absolute(created)}` : '';
    }

    /* ---------------- 自动保存（前缘 + 后缘节流） ---------------- */
    function commit() {
      if (timer) { clearTimeout(timer); timer = null; }
      const text = ta.value;
      if (!text.trim()) {
        if (id) { store.remove(id); id = null; }
      } else if (id) {
        store.update(id, text);
      } else {
        const it = store.add(kind, text);   // 第一个字立刻落库
        if (it) id = it.id;
      }
      savedAt = Date.now();
      dirty = false;
      renderChrome();
    }

    ta.addEventListener('input', () => {
      dirty = true;
      renderChrome();
      if (timer) clearTimeout(timer);
      if (!id && ta.value.trim()) { commit(); return; }   // 第一次立刻存
      timer = setTimeout(commit, SAVE_DEBOUNCE);
    });

    root.addEventListener('click', (e) => {
      const act = e.target.closest('[data-act]');
      if (!act) return;
      if (act.dataset.act === 'back') { commit(); opts.onBack && opts.onBack(); }
      if (act.dataset.act === 'delete') {
        if (!id) return;
        const kindLabel = kind === 'note' ? '笔记' : '待办';
        // 不可撤销 → 先确认。和 App 的单条删除一致（同一个 ConfirmDialog）。
        U().confirm(root, `删除这条${kindLabel}？`, '删除后无法恢复。', '删除', () => {
          store.remove(id); id = null;
          if (timer) clearTimeout(timer);
          dirty = false;
          opts.onDelete ? opts.onDelete() : opts.onBack && opts.onBack();
        });
      }
    });

    renderChrome();
    return { commit, root, get id() { return id; } };
  }

  return { mount };
})();
