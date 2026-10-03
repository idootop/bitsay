/* ==========================================================================
   store.js —— 业务层（对应 core/repo/ItemRepository.kt + AppViewModel 的状态部分）
   --------------------------------------------------------------------------
   这里刻意和 Android 侧保持同样的规则，演示才有意义：
   * 排序：done ASC, createdAt DESC, id DESC（完成的沉底，其余按创建时间倒序）
   * 搜索：只在同一 kind 内按全文 includes（对应 SQL 的 LIKE）
   * 空文本不入库；勾选完成不会让条目跳到最前（因为排序键是 createdAt）
   * 数据只有一份，所有页面订阅同一份 → 在任一视图里改，其它视图同步刷新
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.store = (function () {
  const listeners = new Set();
  let seq = 0;
  const now = () => Date.now();

  /** 演示用初始数据（和真机上那几条一致，方便对照） */
  function seed() {
    const t = now();
    const mk = (kind, text, ageMin, done) => ({
      id: ++seq, kind, text, done: !!done,
      createdAt: t - ageMin * 60000,
      updatedAt: t - ageMin * 60000
    });
    return [
      mk('note', '会议纪要 1. 确定 Q4 目标 2. 排期评审', 60 * 26),
      mk('note', '中文笔记：今天天气不错，去公园走走吧 🌿', 60 * 30),
      mk('note', '读书笔记：不完美的完美', 60 * 48),
      mk('note', '测试一下好的好的好的好', 60 * 7),
      mk('note', '安卓桌面小组件笔记 question：为什么行高要固定 48dp', 60 * 3),
      mk('note', '不要过于悲观，也不要过于乐观', 60 * 11),
      mk('note', '整理一下这周要做的事情：先把备份做完，再补测试', 60 * 20),
      mk('note', '输入输出的 shape 决定了很多性能问题', 60 * 34),
      mk('note', '热路径强化：生物神经元学到的稀疏激活', 60 * 40),
      mk('note', '2026：不完美的完美', 60 * 52),
      mk('note', '专注于真正重要的问题', 60 * 60),
      mk('todo', '买牛奶和鸡蛋', 60 * 20),
      mk('todo', '回复客户邮件', 60 * 28),
      mk('todo', '预约牙医', 60 * 55, true),
      mk('todo', '把备份导出一次，换机前确认能导入', 60 * 5),
      mk('todo', '给小组件补一个 2×2 的截图', 60 * 9),
      mk('todo', '读完《不完美的完美》最后两章', 60 * 44)
    ];
  }

  const state = {
    items: seed(),
    settings: { theme: 'system', lang: 'system' },
    /** 小组件当前显示哪一类（对应 WidgetPrefs.kindOf） */
    widgetKind: 'note',
    /** 小组件尺寸：用于演示"顶栏自动收起" */
    widgetSize: '4x3'
  };

  const emit = () => listeners.forEach((fn) => fn());
  const subscribe = (fn) => { listeners.add(fn); return () => listeners.delete(fn); };

  /** 列表排序：完成的沉底，其余按创建时间倒序（同 DISPLAY_ORDER） */
  function sorted(kind) {
    return state.items
      .filter((i) => i.kind === kind)
      .sort((a, b) =>
        (a.done ? 1 : 0) - (b.done ? 1 : 0) ||
        b.createdAt - a.createdAt ||
        b.id - a.id);
  }

  /** 搜索：同一 kind 内全文匹配，忽略大小写（对应 SQL LIKE） */
  function search(kind, q) {
    const needle = (q || '').trim().toLowerCase();
    if (!needle) return sorted(kind);
    return sorted(kind).filter((i) => i.text.toLowerCase().includes(needle));
  }

  const api = {
    state,
    subscribe,
    emit,
    notes: () => sorted('note'),
    todos: () => sorted('todo'),
    list: (kind) => sorted(kind),
    search,
    find: (id) => state.items.find((i) => i.id === id) || null,
    counts: () => ({
      notes: state.items.filter((i) => i.kind === 'note').length,
      todos: state.items.filter((i) => i.kind === 'todo').length,
      openTodos: state.items.filter((i) => i.kind === 'todo' && !i.done).length
    }),

    /** 空白文本不入库（对应 ItemRepository.normalize） */
    add(kind, text) {
      const clean = (text || '').trim();
      if (!clean) return null;
      const t = now();
      const item = { id: ++seq, kind, text: clean, done: false, createdAt: t, updatedAt: t };
      state.items.push(item);
      emit();
      return item;
    },
    update(id, text) {
      const it = api.find(id);
      if (!it) return;
      const clean = (text || '').trim();
      if (!clean) { api.remove(id); return; }   // 清空即删除，和 App 一致
      it.text = clean;
      it.updatedAt = now();
      emit();
    },
    toggleDone(id) {
      const it = api.find(id);
      if (!it || it.kind !== 'todo') return;
      it.done = !it.done;
      it.updatedAt = now();
      emit();
    },
    remove(id) {
      state.items = state.items.filter((i) => i.id !== id);
      emit();
    },
    removeMany(ids) {
      const set = new Set(ids);
      state.items = state.items.filter((i) => !set.has(i.id));
      emit();
    },
    setWidgetKind(kind) { state.widgetKind = kind; emit(); },
    setWidgetSize(size) { state.widgetSize = size; emit(); },
    setSetting(key, value) { state.settings[key] = value; emit(); },
    reset() { state.items = seed(); state.widgetKind = 'note'; emit(); }
  };

  /* ---------- 时间文案（对应 core/util/TimeText.kt） ---------- */
  function pad(n) { return String(n).padStart(2, '0'); }
  api.timeText = function (ts) {
    const d = new Date(ts), n = new Date();
    const sameDay = d.toDateString() === n.toDateString();
    const yest = new Date(n.getTime() - 86400000).toDateString() === d.toDateString();
    if (sameDay) return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    if (yest) return `昨天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    if (d.getFullYear() === n.getFullYear()) return `${d.getMonth() + 1}月${d.getDate()}日`;
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
  };
  /** 绝对时间 yyyy-MM-dd HH:mm（对应 time_pattern_full，编辑器的"创建于"用） */
  api.absolute = function (ts) {
    const d = new Date(ts);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
           `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };
  /** 相对时间：刚刚 / N 分钟前 / 今天 HH:mm …（编辑器底部状态用） */
  api.relative = function (ts) {
    const diff = Date.now() - ts;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
    return api.timeText(ts);
  };
  return api;
})();
