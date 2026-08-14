/**
 * Two-level Google Merchant Center categories from 产品类目.txt.
 * The second level keeps the selector detailed without storing the full
 * 5,600-node tree in every site configuration. Prohibited branches are
 * removed from the source paths before this list is generated.
 */
export const PRODUCT_CATEGORIES = Object.freeze([
  '书籍', '五金', '五金/硬件',
  '五金/硬件|||五金工具', '五金/硬件|||五金泵', '五金/硬件|||五金配件', '五金/硬件|||供暖、通风及空调', '五金/硬件|||储罐', '五金/硬件|||小型发动机', '五金/硬件|||工具', '五金/硬件|||工具配件', '五金/硬件|||建材', '五金/硬件|||建筑耗材', '五金/硬件|||栅栏/围栏', '五金/硬件|||燃料', '五金/硬件|||燃料罐/箱', '五金/硬件|||电动/电气用品', '五金/硬件|||管道', '五金/硬件|||锁与钥匙',
  '交通工具/汽车/飞机/船舶', '交通工具/汽车/飞机/船舶|||交通工具', '交通工具/汽车/飞机/船舶|||交通工具零配件',
  '体育用品', '体育用品|||室内游戏', '体育用品|||户外休闲', '体育用品|||田径', '体育用品|||运动健身',
  '办公用品', '办公用品|||一般办公用品', '办公用品|||书籍用具', '办公用品|||办公室/椅子地垫', '办公用品|||办公室手推车', '办公用品|||办公文具', '办公用品|||办公设备', '办公用品|||包装快递用品', '办公用品|||名牌', '办公用品|||文件整理', '办公用品|||桌垫', '办公用品|||演示用品', '办公用品|||笔记本电脑托架', '办公用品|||纸张处理', '办公用品|||脉冲热封机',
  '动漫', '动物', '动物/宠物用品', '动物/宠物用品|||宠物用品', '动物/宠物用品|||活体动物',
  '商业/工业', '商业/工业|||农/畜牧/渔业专用设备', '商业/工业|||制造业', '商业/工业|||劳保/防护用品', '商业/工业|||医疗', '商业/工业|||工业仓储', '商业/工业|||工业仓储配件', '商业/工业|||广告与营销', '商业/工业|||建筑用品', '商业/工业|||影视', '商业/工业|||执法', '商业/工业|||材料处理', '商业/工业|||林业与伐木业', '商业/工业|||标识牌', '商业/工业|||清洁车/杂物篮', '商业/工业|||牙科', '商业/工业|||科学与实验', '商业/工业|||穿刺与纹身', '商业/工业|||美容美发业', '商业/工业|||自动化控制组件', '商业/工业|||酒店与宾馆', '商业/工业|||采矿与采石', '商业/工业|||重型机械', '商业/工业|||金融与保险', '商业/工业|||零售业', '商业/工业|||餐饮服务',
  '婴幼儿用品', '婴幼儿用品|||哺乳与喂养', '婴幼儿用品|||如厕训练器', '婴幼儿用品|||婴儿出行用品', '婴幼儿用品|||婴儿卫生', '婴幼儿用品|||婴儿安全用品', '婴幼儿用品|||婴儿洗浴用品', '婴幼儿用品|||婴儿玩具/活动设备', '婴幼儿用品|||婴儿礼品套装', '婴幼儿用品|||婴幼儿出行用品配件', '婴幼儿用品|||尿布相关用品', '婴幼儿用品|||襁褓/婴儿包毯',
  '媒体', '媒体|||DVD 和视频', '媒体|||乐谱', '媒体|||书', '媒体|||产品说明书', '媒体|||报纸/杂志', '媒体|||木工项目计划', '媒体|||音乐',
  '宗教/仪式', '宗教/仪式|||婚庆用品', '宗教/仪式|||宗教用品', '宗教/仪式|||纪念仪式用品',
  '家具', '家具|||办公家具', '家具|||办公家具配件', '家具|||可移动置物架', '家具|||娱乐中心/电视柜', '家具|||婴幼儿家具', '家具|||家具套装', '家具|||屏风/隔屏', '家具|||床具与配件', '家具|||户外家具', '家具|||户外家具配件', '家具|||房间隔板配件', '家具|||搁架', '家具|||日式床垫/折叠沙发床', '家具|||架子配件', '家具|||柜子/储物', '家具|||桌子', '家具|||桌子配件', '家具|||椅子', '家具|||椅子配件', '家具|||沙发', '家具|||沙发凳', '家具|||沙发配件', '家具|||蒲团/榻榻米底架', '家具|||蒲团垫', '家具|||长椅',
  '家居与园艺', '家居与园艺|||保险柜/保险箱', '家居与园艺|||厨房/餐厅', '家居与园艺|||壁炉', '家居与园艺|||壁炉与木炉配件', '家居与园艺|||家居用品', '家居与园艺|||家用电器', '家居与园艺|||家电配件', '家居与园艺|||床上用品', '家居与园艺|||应急准备', '家居与园艺|||柴火炉', '家居与园艺|||植物', '家居与园艺|||泳池/水疗', '家居与园艺|||浴室配件', '家居与园艺|||照明设备', '家居与园艺|||照明配件', '家居与园艺|||草坪与花园', '家居与园艺|||装饰', '家居与园艺|||防洪、消防与可燃气体安全设备', '家居与园艺|||雨伞/遮阳伞', '家居与园艺|||雨伞套/盒',
  '户外用品', '机械', '玩具/游戏', '玩具/游戏|||室外玩具设备', '玩具/游戏|||游戏', '玩具/游戏|||游戏计时器', '玩具/游戏|||玩具', '玩具/游戏|||益智玩具/游戏',
  '电子产品', '电子产品|||GPS 导航系统', '电子产品|||GPS 配件', '电子产品|||GPS跟踪设备', '电子产品|||大型游戏机/街机', '电子产品|||手机配件', '电子产品|||打印/复印/扫描/传真', '电子产品|||收费装置', '电子产品|||测速雷达', '电子产品|||电子游戏机', '电子产品|||电子游戏机配件', '电子产品|||电子配件', '电子产品|||电路板和组件', '电子产品|||组件', '电子产品|||网络', '电子产品|||航海电子设备', '电子产品|||视频', '电子产品|||计算机', '电子产品|||通讯', '电子产品|||雷达探测器', '电子产品|||音频',
  '相机与光学器件', '相机与光学器件|||光学器件', '相机与光学器件|||照片冲印/摄影棚器材', '相机与光学器件|||相机', '相机与光学器件|||相机与光学器件配件',
  '箱包', '箱包|||公文包', '箱包|||化妆箱', '箱包|||尿布包', '箱包|||手提旅行包/运动桶包', '箱包|||旅行箱/包', '箱包|||洗漱包/盥洗包', '箱包|||箱包配件', '箱包|||背包', '箱包|||腰包', '箱包|||购物袋', '箱包|||邮差包', '箱包|||防潮箱/盒',
  '艺术与娱乐', '艺术与娱乐|||活动门票', '艺术与娱乐|||爱好/艺术创作', '艺术与娱乐|||聚会/庆典',
  '软件', '软件|||数字商品与货币', '软件|||电子游戏软件', '软件|||电脑软件',
])

/**
 * Convert the flattened `一级|||二级` catalog into Element Plus tree-select data.
 * Values remain the complete path so the backend can filter by the exact
 * selected catalog path while labels stay compact in the UI.
 */
export function buildProductCategoryTree(categories = PRODUCT_CATEGORIES) {
  const roots = new Map()
  for (const path of categories) {
    const [rootLabel, childLabel] = String(path).split('|||')
    if (!rootLabel) continue
    if (!roots.has(rootLabel)) {
      roots.set(rootLabel, { value: rootLabel, label: rootLabel, children: [] })
    }
    if (childLabel) {
      roots.get(rootLabel).children.push({ value: path, label: childLabel })
    }
  }
  return Array.from(roots.values()).map(root => (
    root.children.length ? root : { value: root.value, label: root.label }
  ))
}

export const PRODUCT_CATEGORY_TREE = buildProductCategoryTree()

/** Resolve legacy leaf-only values (for example `草坪与花园`) to their full path. */
export function resolveProductCategoryPath(value) {
  const text = String(value || '').trim()
  if (!text || PRODUCT_CATEGORIES.includes(text)) return text
  return PRODUCT_CATEGORIES.find(path => path.endsWith(`|||${text}`)) || text
}

/** Return the category label that should be stored on a product. */
export function productCategoryLabel(value) {
  const path = resolveProductCategoryPath(value)
  return path.includes('|||') ? path.split('|||').pop() : path
}

export const PROHIBITED_PRODUCT_CATEGORY_PATTERN = /保健品|保健|食品|枪支|枪械|弹药|武器|毒品|烟酒|烟草|烟具|酒精|服装|服饰|成人/i

export function isAllowedProductCategory(value) {
  const category = String(value || '').trim()
  return Boolean(category) && !PROHIBITED_PRODUCT_CATEGORY_PATTERN.test(category)
}
