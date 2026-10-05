/* ==========================================================================
   appicon.js —— 应用图标候选方案
   --------------------------------------------------------------------------
   画布按 Android 自适应图标规范：108×108，可见区是中间 72×72，
   安全区是以 (54,54) 为心、半径 33 的圆。下面每个方案的最远点都核过到 ≤31，
   所以圆形 / 方形 / 圆角方形三种裁切都不会切到主体。

   概念：**空状态那株嫩芽**。它在页面里已经建立了"生长"的语言，
   图标就是把它压缩成一个能被认出来的记号。底色一律墨黑（见下）。
   ========================================================================== */
window.BitSay = window.BitSay || {};
BitSay.appicon = (function () {

  const GREEN_DEEP = '#2F6B4A';
  const GREEN      = '#3E8A5C';
  const GREEN_LITE = '#7FC98F';
  const PAPER      = '#F7FBF6';
  const INK        = '#17140F';
  const SAND       = '#F2EDE4';
  /* 石灰：只在墨底上给"石头"用。比墨底亮、比芽暗，中性偏暖，不会被读成土绿。 */
  const STONE      = '#55534C';

  /* 嫩芽 —— **幼苗**，不是藤蔓。
     区别在叶子的位置：
       * 藤蔓：两片叶错开长在茎的不同高度上（之前画错了，读起来像爬藤）；
       * 幼苗：茎从土里直直长上来，**两片叶在顶端同一个节点向左右张开**。
     节点 (54,46)，叶尖 (37,29) / (71,29)，茎从 (54,78) 到节点。
     所有端点都在半径 33 的安全圆内（Android 自适应图标的安全区）。 */
  const sprout = (c, sw = 4.4) => `
    <path d="M54 79 C53 68 53 57 54 46"
          stroke="${c}" stroke-width="${sw}" stroke-linecap="round" fill="none"/>
    <path d="M54 46 C44 47 37 39 37 29 C47 29 54 36 54 46 Z" fill="${c}"/>
    <path d="M54 46 C64 47 71 39 71 29 C61 29 54 36 54 46 Z" fill="${c}"/>`;

  /* 对勾：短促有力，笔画粗到 48px 时还是一条实线 */
  const check = (c, sw = 8) => `
    <path d="M34 55 L48 69 L76 37" stroke="${c}" stroke-width="${sw}"
          stroke-linecap="round" stroke-linejoin="round" fill="none"/>`;

  const svg = (inner, bg) => `<svg class="appicon" viewBox="0 0 108 108"
      xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect width="108" height="108" fill="${bg}"/>${inner}</svg>`;

  /* 土壤：一丘收在安全区内的土，不是一条通到底的横带
     （横带被圆形遮罩切完只剩一弯月牙，很怪）。 */
  const soil = (c) => '<path d="M34 79 C34 71 42 67 54 67 C66 67 74 71 74 79 Z" fill="' + c + '"/>';

  /* ------------------------------------------------------------------ 方案
     **定稿：C5「破土」**（2026-10 用户敲定，已落到 Android 资源）。
     两个方向并存，**底色统一墨黑**（绿底已删，纸色底不受欢迎）：
       * C 族（C/C1–C5）—— 墨底 · 土上发芽，在"怎么画土、怎么画芽"上分身位；
       * G —— 墨底 · 石缝探芽，换叙事：不是站在土上，是从缝里挣出来。
     任何新方案都必须先过这一关：**放在墨底上还看不看得见**。 */

  /* 土：一丘收在安全区内的弧。底边左右两端 (34,79)/(74,79) 到圆心 32.0，
     是这一族里离安全圆最近的点（半径 33），已经核过。 */
  const SOIL = '#2C4A35';

  const VARIANTS = [
    {
      key: 'C', name: '墨底 · 土上发芽', tag: '基准',
      note: '墨底 + 一丘深绿的土 + 一株亮绿的幼苗。延续 App 内「纯黑是强调色、绿只给植物」的规则。下面 C1–C5 都是它的分身。',
      svg: svg(soil(SOIL) + sprout(GREEN_LITE), INK),
    },
    {
      key: 'C1', name: '亮芽', tag: '对比',
      note: '只换明度：芽提到最亮的绿、土压到中绿。墨底上对比度拉满，**24px 时轮廓最不容易糊**——如果只在乎小尺寸可读性，选它。',
      svg: svg(soil(GREEN_DEEP) + sprout('#A8E0AE'), INK),
    },
    {
      key: 'C2', name: '描边', tag: '线描',
      note: '芽和土都只留轮廓、不上填充，和空状态那株**线描**的芽是同一套语言。土必须画成**闭合的穹顶、茎从穹顶顶端起笔** —— 开口的土配一根插进去的茎，会读成一只杯子。轻，但 24px 下笔画细到快看不见。',
      svg: svg(`
        <path d="M34 79 C34 71 42 67 54 67 C66 67 74 71 74 79 Z"
              fill="none" stroke="${SOIL}" stroke-width="3.4" stroke-linejoin="round"/>
        <path d="M54 67 C53 60 53 52 54 46"
              fill="none" stroke="${GREEN_LITE}" stroke-width="3.2" stroke-linecap="round"/>
        <path d="M54 46 C44 47 37 39 37 29 C47 29 54 36 54 46 Z"
              fill="none" stroke="${GREEN_LITE}" stroke-width="3.2" stroke-linejoin="round"/>
        <path d="M54 46 C64 47 71 39 71 29 C61 29 54 36 54 46 Z"
              fill="none" stroke="${GREEN_LITE}" stroke-width="3.2" stroke-linejoin="round"/>`, INK),
    },
    {
      key: 'C3', name: '圆土', tag: '几何',
      note: '土从"手画的丘"换成**正半圆**，芽立在平边上。少一笔随意、多一分规整，是这一族里最"像个图标"的一版；代价是土不再像土，更像一个底座。',
      svg: svg(`
        <path d="M33 79 A21 21 0 0 1 75 79 Z" fill="${SOIL}"/>
        ${sprout(GREEN_LITE)}`, INK),
    },
    {
      key: 'C4', name: '特写', tag: '小尺寸',
      note: '干脆不要土：两片叶放大到几乎顶满安全圆，茎只剩一小截。**叶片越少细节、越大，24px 越清楚** —— 这是纯为小尺寸做的一版，代价是丢了"土"这个叙事。',
      svg: svg(`
        <path d="M54 80 C53.4 68 53.4 60 54 53"
              fill="none" stroke="${GREEN_LITE}" stroke-width="6" stroke-linecap="round"/>
        <path d="M54 53 C42 54 33 44 33 30 C45 30 54 40 54 53 Z" fill="${GREEN_LITE}"/>
        <path d="M54 53 C66 54 75 44 75 30 C63 30 54 40 54 53 Z" fill="${GREEN_LITE}"/>`, INK),
    },
    {
      key: 'C5', name: '破土', tag: '叙事', final: true,
      note: '土被顶开成左右两半，中间留一道刚好容下茎的缝 —— 芽是**从土里钻出来**的，不是站在土上面。比基准多讲了半句话，也是这一族里唯一有动作的一版。',
      svg: svg(`
        <path d="M34 79 C34 71 41 67 50 66 L52.5 79 Z" fill="${SOIL}"/>
        <path d="M57.5 79 L60 66 C69 67 74 71 74 79 Z" fill="${SOIL}"/>
        ${sprout(GREEN_LITE)}`, INK),
    },
    {
      key: 'G', name: '墨底 · 石缝探芽', tag: '叙事',
      note: '一块石头裂了道缝，小芽从缝里探出脑袋 —— 生长是件需要使劲的事。**墨底上石头必须改成石灰色**（墨色石头放在墨底上等于不存在），缝用更暗的墨线压出来，才读得出是"裂开的石"而不是"一丘土"。石头画成有棱角的多面体，圆丘会被读成土、和 C 族重复。',
      svg: svg(`
        ${sprout(GREEN_LITE)}
        <path d="M34 79 L38 68 L46 63 L53 69 L60 62 L69 66 L74 79 Z" fill="${STONE}"/>
        <path d="M41 74.5 L49 70.5 M59 70.5 L70 75" stroke="${INK}" stroke-opacity=".42"
              stroke-width="2.2" stroke-linecap="round" fill="none"/>`, INK),
    },
  ];

  /* 定稿：'破土'。已落到 app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
     和 ic_widget_open_app.xml；改这里就必须同步改那边。 */
  const FINAL = VARIANTS.find((v) => v.final);

  return { VARIANTS, FINAL };
})();
