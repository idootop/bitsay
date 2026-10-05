/* ==========================================================================
   sprout.js —— 空状态的植物线描
   --------------------------------------------------------------------------
   一朵"正在长出来"的嫩芽：一根茎 + 两片叶，只有线条，没有五官、没有身体。
   和之前被否掉的卡通角色的区别：
     * 卡通角色是**拟人的物件**（便签/仓鼠），幼稚；
     * 这是**植物插画**，绘本里那种细笔触，安静、不抢戏。
   进场用 stroke-dashoffset 让它自己"长"出来 —— 这就是"生长"本身。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.sprout = (function () {

  /* 幼苗，不是藤蔓：茎从底部直长上来，**两片叶在顶端同一个节点张开**。
     之前两片叶错开长在茎的不同高度，读起来像爬藤。 */
  const STEM   = 'M32 84 C 31 68 31 50 32 36';
  const LEAF_L = 'M32 36 C 22 37 13 29 13 16 C 24 16 32 25 32 36 Z';
  const LEAF_R = 'M32 36 C 42 37 51 29 51 16 C 40 16 32 25 32 36 Z';

  /**
   * @param opts { size:number, delay:number }
   * 茎先长，接着两片顶叶同时张开 —— 这才是幼苗的姿态
   */
  function svg(opts = {}) {
    const size = opts.size || 96;
    return `<svg class="sprout" viewBox="0 0 64 88" width="${size}"
        height="${Math.round(size * 88 / 64)}" fill="none" aria-hidden="true"
        stroke="var(--c-leaf)" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
      <path class="sprout__stem" d="${STEM}"/>
      <path class="sprout__leaf-l" d="${LEAF_L}"/>
      <path class="sprout__leaf-r" d="${LEAF_R}"/>
    </svg>`;
  }
  return { svg };
})();
