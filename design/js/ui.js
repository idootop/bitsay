/* ==========================================================================
   ui.js —— 复用组件（对应 ui/components/ItemList.kt、SegmentedTabs、CuteIconButton…）
   --------------------------------------------------------------------------
   关键约定：**卡片只有这一处实现**（BitSay.ui.card）。
   首页、搜索页都调它 —— 和 Android 侧共用 ItemList 是同一个理由：
   一旦有两套，样式就会慢慢漂移。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.ui = (function () {
  const I = BitSay.icons;

  const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /** 转义 + 只认 **加粗**：设计板上的说明文字用它，省得为了几个重点引 markdown 库 */
  const md = (s) => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>');

  /** 单行预览：换行/连续空白压成一个空格（对应 TextPreview.singleLine） */
  const singleLine = (s) => String(s).replace(/\s+/g, ' ').trim();

  const ICON_BUTTON = (name, cls = '') =>
    `<button class="icon-btn ${cls}" data-icon="${name}" type="button">${I[name]()}</button>`;

  /** 分段 tab（对应 SegmentedTabs）：选中态是浮起的白色药丸，不需要传颜色 */
  function seg(items, activeKey) {
    return `<div class="seg">` + items.map((it) =>
      `<button type="button" class="seg__item ${it.key === activeKey ? 'seg__item--on' : ''}"
        data-seg="${it.key}">${esc(it.label)}</button>`
    ).join('') + `</div>`;
  }

  /**
   * 列表条目卡片 —— 全站唯一实现。
   * @param item  数据
   * @param idx   在列表中的序号（用作错开的动画延迟）
   * @param opts  { selecting, selected, enter, settle }
   *              enter  = 这一条是刚出现的 → 播"萌发"动画
   *              settle = 刚刚被勾选 → 完成圈轻轻落定
   */
  function card(item, idx, opts = {}) {
    let lead = '';
    if (opts.selecting) {
      lead = `<span class="pick ${opts.selected ? 'pick--on' : ''}">
                ${opts.selected ? I.check() : ''}</span>`;
    } else if (item.kind === 'todo') {
      // 多选态下不显示完成圈：两个圆圈并排会读成噪音
      lead = `<button type="button" class="tick ${item.done ? 'tick--on' : ''}
                ${opts.settle ? 'tick--settle' : ''}" data-act="tick"
                title="勾选完成">${item.done ? I.check() : ''}</button>`;
    }

    const cls = ['card', 'inked',
      item.done ? 'card--done' : '',
      opts.enter ? 'card--sprout' : ''].filter(Boolean).join(' ');
    return `<article class="${cls}" data-id="${item.id}" style="--i:${idx}" tabindex="0">
      ${lead}
      <div class="card__body">
        <div class="card__text">${esc(singleLine(item.text))}</div>
        <div class="card__meta">${esc(BitSay.store.timeText(item.createdAt))}</div>
      </div>
    </article>`;
  }

  /**
   * 空状态 —— 全站唯一实现（列表 / 搜索都用它）。
   * 一株会自己长出来的嫩芽 + 衬线标题 + 一句人话。
   * @param o { title, hint, size }
   */
  function emptyState(o) {
    return `<div class="empty">
      ${BitSay.sprout.svg({ size: o.size || 92 })}
      <div class="empty__title">${esc(o.title)}</div>
      <div class="empty__hint">${o.hint}</div>
    </div>`;
  }

  /** 长按 / 右键 → 进多选（对应 combinedClickable 的 onLongClick） */
  function longPress(el, fn) {
    let timer = null;
    const start = () => { timer = setTimeout(() => { timer = null; fn(); }, 420); };
    const stop = () => { if (timer) { clearTimeout(timer); timer = null; } };
    el.addEventListener('pointerdown', start);
    el.addEventListener('pointerup', stop);
    el.addEventListener('pointerleave', stop);
    el.addEventListener('pointercancel', stop);
    el.addEventListener('contextmenu', (e) => { e.preventDefault(); fn(); });
  }

  function toast(host, msg) {
    let t = host.querySelector(':scope > .toast');
    if (!t) { t = document.createElement('div'); t.className = 'toast'; host.appendChild(t); }
    t.textContent = msg;
    requestAnimationFrame(() => t.classList.add('toast--on'));
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.classList.remove('toast--on'), 1800);
  }

  /** 单选对话框（对应设置页的 ChoiceDialog） */
  function choose(host, title, options, current, onPick) {
    const scrim = document.createElement('div');
    scrim.className = 'scrim';
    scrim.innerHTML = `<div class="dialog">
      <div class="dialog__title">${esc(title)}</div>
      ${options.map((o) => `<div class="dialog__opt" data-key="${o.key}">
          <span class="check">${o.key === current ? I.check() : ''}</span>${esc(o.label)}</div>`).join('')}
      <div class="dialog__actions"><button type="button" data-cancel>取消</button></div>
    </div>`;
    host.appendChild(scrim);
    requestAnimationFrame(() => scrim.classList.add('scrim--on'));
    const close = () => { scrim.classList.remove('scrim--on'); setTimeout(() => scrim.remove(), 200); };
    scrim.addEventListener('click', (e) => {
      if (e.target === scrim || e.target.closest('[data-cancel]')) return close();
      const opt = e.target.closest('[data-key]');
      if (opt) { onPick(opt.dataset.key); close(); }
    });
  }

  return { esc, md, singleLine, ICON_BUTTON, seg, card, emptyState, longPress, toast, choose };
})();
