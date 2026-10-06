/* ==========================================================================
   brand.js —— 品牌标记「破土」（无墨底版）
   --------------------------------------------------------------------------
   和 app 的 ic_launcher_foreground.xml 是同一份几何，只是去掉了墨色底板，
   好让它能直接落在页面底色上。首页顶栏的标题用它，不再写"笔记 / 待办"——
   下面那排分段 tab 已经说了现在是哪个列表，标题再写一遍等于用整个上三屏重复自己。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.brand = (function () {
  const SOIL = '#2C4A35';
  const SPROUT = '#7FC98F';
  /**
   * @param h 高度（px）。画布按 108 体系里的 40x50 主体等比缩放。
   */
  function svg(h = 46) {
    return `<svg class="brand" viewBox="33 28 42 52" width="${Math.round(h * 0.84)}"
        height="${h}" fill="none" aria-hidden="true">
      <path d="M34 79 C34 71 41 67 50 66 L52.5 79 Z" fill="${SOIL}"/>
      <path d="M57.5 79 L60 66 C69 67 74 71 74 79 Z" fill="${SOIL}"/>
      <path d="M54 79 C53 68 53 57 54 46" stroke="${SPROUT}" stroke-width="4.4" stroke-linecap="round"/>
      <path d="M54 46 C44 47 37 39 37 29 C47 29 54 36 54 46 Z" fill="${SPROUT}"/>
      <path d="M54 46 C64 47 71 39 71 29 C61 29 54 36 54 46 Z" fill="${SPROUT}"/>
    </svg>`;
  }
  return { svg };
})();
