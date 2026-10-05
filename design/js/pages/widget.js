/* ==========================================================================
   桌面小组件 —— 对应 res/layout/widget_bitsay.xml + WidgetRenderer.kt
   行为：笔记/待办 tab · 点行看详情 · 点圆圈勾完成 · + 快捷新建 · 搜索
        装不下「顶栏 + 3 行」时顶栏自动收起（对应 WidgetLayout.showHeader）
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.pages = window.BitSay.pages || {};
BitSay.pages.widget = (function () {
  const ROW_H = 46, HEAD_H = 44;   // 与 widget.css 的 --w-row-h / --w-head-h 保持一致
  const MIN_ROWS_WITH_HEADER = 3;  // 对应 WidgetLayout.MIN_ROWS_WITH_HEADER

  /**
   * 尺寸表。px 是按真机 dp 折算的演示值：
   *   真机 360dp 宽、我们的手机框屏宽约 290px → 1dp ≈ 0.8px。
   *   "2x5" 就是参照用户真实桌面那个竖长形态（2 列宽 ≈ 216dp）。
   */
  const SIZES = {
    // 竖长 2×5 = 用户真机桌面上那个形态，实测 206 × 535 dp
    '2x5': { w: '100%', h: 535 },
    '2x3': { w: '100%', h: 348 },   // 中等：少 4 行
    '2x2': { w: '100%', h: 158 }    // 最小：装不下「顶栏 + 3 行」→ 顶栏收起
  };

  /**
   * @param host  .wall 容器
   * @param opts  { size:'4x3'|'3x2'|'2x2', kind, onOpen(id), onNew(kind), onSearch(), onOpenApp(), interactive }
   */
  function mount(host, opts = {}) {
    const I = BitSay.icons, S = BitSay.store;
    const size = opts.size || '4x3';
    const dims = SIZES[size] || SIZES['4x3'];
    const interactive = opts.interactive !== false;

    const box = document.createElement('div');
    box.className = 'widget inked';
    box.style.width = typeof dims.w === 'number' ? dims.w + 'px' : dims.w;
    box.style.height = dims.h + 'px';
    if (size === '2x2') box.classList.add('widget--tiny');
    host.appendChild(box);

    /** n 行需要 n*ROW_H + (n-1)*gap，反解出最多能放几行 */
    const rowsFor = (space) => Math.max(0, Math.floor((space + 4) / (ROW_H + 4)));

    function layout() {
      const h = box.clientHeight || dims.h;
      const avail = h - 10;                                   // 减去卡片自身 padding
      // 先按"带顶栏"算行数；装不下 3 行就不给顶栏，把空间全留给列表
      const withHeader = rowsFor(avail - HEAD_H) >= MIN_ROWS_WITH_HEADER;
      return { withHeader, rows: rowsFor(avail - (withHeader ? HEAD_H : 0)) };
    }

    /* 渲染签名：内容没变就不重绘。
       ResizeObserver 挂载后会立刻回调一次，如果无条件重绘，就会把
       DOM 整体换掉 —— 空状态里那株嫩芽的绘制动画会被打回起点，看起来像没画。
       （真机上小组件是 RemoteViews，没有这个问题；但设计台里必须防住。） */
    let lastSig = null;

    function render() {
      const kind = opts.kind || S.state.widgetKind;
      const items = S.list(kind);
      const { withHeader: showHeader, rows: visible } = layout();
      const sig = kind + '|' + (showHeader ? 1 : 0) + '|' + visible + '|' +
                  items.slice(0, Math.max(visible, 1)).map((i) => i.id + (i.done ? 'd' : '') + i.text).join(',');
      if (sig === lastSig) return;
      lastSig = sig;

      box.innerHTML = `
        <div class="widget__head" ${showHeader ? '' : 'hidden'}>
          <button class="widget__icon" type="button" data-act="openapp" title="打开应用">${I.openApp()}</button>
          <button class="widget__tab ${kind === 'note' ? 'widget__tab--on' : ''}" type="button"
            data-kind="note">笔记</button>
          <button class="widget__tab ${kind === 'todo' ? 'widget__tab--on' : ''}" type="button"
            data-kind="todo">待办</button>
          <button class="widget__icon" type="button" data-act="search" title="搜索">${I.search()}</button>
        </div>
        ${items.length ? `<div class="widget__list">${items.slice(0, Math.max(visible, 1)).map((it, i) => {
          return `<div class="widget__row ${it.done ? 'widget__row--done' : ''} ${it.kind === 'todo' ? '' : 'widget__row--plain'}" data-id="${it.id}">
            ${it.kind === 'todo'
              ? `<span class="widget__tick" data-act="tick">${it.done ? I.todoDone() : I.todoOpen()}</span>`
              : ''}
            <span class="widget__text">${BitSay.ui.esc(BitSay.ui.singleLine(it.text))}</span>
          </div>`;
        }).join('')}</div>`
        : `<div class="widget__empty">
             ${BitSay.sprout.svg({ size: dims.h >= 400 ? 74 : dims.h >= 250 ? 60 : 38 })}
             <span>这里还什么都没有\n去 App 里加一条吧</span>
           </div>`}
        <button class="widget__fab" type="button" data-act="new" title="新建">${I.plus()}</button>`;

      if (!interactive) box.style.pointerEvents = 'none';
    }

    box.addEventListener('click', (e) => {
      if (!interactive) return;
      const kindBtn = e.target.closest('[data-kind]');
      if (kindBtn) { S.setWidgetKind(kindBtn.dataset.kind); return; }
      const act = e.target.closest('[data-act]');
      if (act) {
        switch (act.dataset.act) {
          case 'openapp': opts.onOpenApp && opts.onOpenApp(); return;
          case 'search':  opts.onSearch && opts.onSearch(); return;
          case 'new':     opts.onNew && opts.onNew(opts.kind || S.state.widgetKind); return;
          case 'tick':
            e.stopPropagation();
            S.toggleDone(Number(act.closest('.widget__row').dataset.id));
            return;
        }
      }
      const row = e.target.closest('.widget__row');
      if (row) opts.onOpen && opts.onOpen(Number(row.dataset.id));
    });

    const unsub = S.subscribe(render);
    render();
    // 尺寸变化后重新判断能不能放下顶栏
    const ro = window.ResizeObserver ? new ResizeObserver(() => render()) : null;
    if (ro) ro.observe(box);
    /* unmount 必须同时断开 observer —— 只退订的话，尺寸回调还会再触发一次 render，
       调用方以为已经"冻住"了，其实没有。 */
    return {
      render,
      unmount() { unsub(); if (ro) ro.disconnect(); },
    };
  }
  return { mount };
})();
