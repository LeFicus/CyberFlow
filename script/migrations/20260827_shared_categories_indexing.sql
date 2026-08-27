-- Editable shared catalog. Seed exactly once: deleted categories must not reappear at startup.
CREATE TABLE IF NOT EXISTS custom_category (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(100) NOT NULL, enabled TINYINT NOT NULL DEFAULT 1,
 sort_order INT NOT NULL DEFAULT 0, UNIQUE KEY uk_custom_category_name(name),
 KEY idx_custom_category_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS custom_category_seed (id INT PRIMARY KEY);
INSERT IGNORE INTO custom_category(id,parent_id,name,sort_order)
SELECT seed.id,seed.parent_id,seed.name,seed.id FROM (
SELECT 1 AS id,0 AS parent_id,'书籍' AS name
UNION ALL
SELECT 2 AS id,0 AS parent_id,'五金' AS name
UNION ALL
SELECT 3 AS id,0 AS parent_id,'五金/硬件' AS name
UNION ALL
SELECT 4 AS id,3 AS parent_id,'五金工具' AS name
UNION ALL
SELECT 5 AS id,3 AS parent_id,'五金泵' AS name
UNION ALL
SELECT 6 AS id,3 AS parent_id,'五金配件' AS name
UNION ALL
SELECT 7 AS id,3 AS parent_id,'供暖、通风及空调' AS name
UNION ALL
SELECT 8 AS id,3 AS parent_id,'储罐' AS name
UNION ALL
SELECT 9 AS id,3 AS parent_id,'小型发动机' AS name
UNION ALL
SELECT 10 AS id,3 AS parent_id,'工具' AS name
UNION ALL
SELECT 11 AS id,3 AS parent_id,'工具配件' AS name
UNION ALL
SELECT 12 AS id,3 AS parent_id,'建材' AS name
UNION ALL
SELECT 13 AS id,3 AS parent_id,'建筑耗材' AS name
UNION ALL
SELECT 14 AS id,3 AS parent_id,'栅栏/围栏' AS name
UNION ALL
SELECT 15 AS id,3 AS parent_id,'燃料' AS name
UNION ALL
SELECT 16 AS id,3 AS parent_id,'燃料罐/箱' AS name
UNION ALL
SELECT 17 AS id,3 AS parent_id,'电动/电气用品' AS name
UNION ALL
SELECT 18 AS id,3 AS parent_id,'管道' AS name
UNION ALL
SELECT 19 AS id,3 AS parent_id,'锁与钥匙' AS name
UNION ALL
SELECT 20 AS id,0 AS parent_id,'交通工具/汽车/飞机/船舶' AS name
UNION ALL
SELECT 21 AS id,20 AS parent_id,'交通工具' AS name
UNION ALL
SELECT 22 AS id,20 AS parent_id,'交通工具零配件' AS name
UNION ALL
SELECT 23 AS id,0 AS parent_id,'体育用品' AS name
UNION ALL
SELECT 24 AS id,23 AS parent_id,'室内游戏' AS name
UNION ALL
SELECT 25 AS id,23 AS parent_id,'户外休闲' AS name
UNION ALL
SELECT 26 AS id,23 AS parent_id,'田径' AS name
UNION ALL
SELECT 27 AS id,23 AS parent_id,'运动健身' AS name
UNION ALL
SELECT 28 AS id,0 AS parent_id,'办公用品' AS name
UNION ALL
SELECT 29 AS id,28 AS parent_id,'一般办公用品' AS name
UNION ALL
SELECT 30 AS id,28 AS parent_id,'书籍用具' AS name
UNION ALL
SELECT 31 AS id,28 AS parent_id,'办公室/椅子地垫' AS name
UNION ALL
SELECT 32 AS id,28 AS parent_id,'办公室手推车' AS name
UNION ALL
SELECT 33 AS id,28 AS parent_id,'办公文具' AS name
UNION ALL
SELECT 34 AS id,28 AS parent_id,'办公设备' AS name
UNION ALL
SELECT 35 AS id,28 AS parent_id,'包装快递用品' AS name
UNION ALL
SELECT 36 AS id,28 AS parent_id,'名牌' AS name
UNION ALL
SELECT 37 AS id,28 AS parent_id,'文件整理' AS name
UNION ALL
SELECT 38 AS id,28 AS parent_id,'桌垫' AS name
UNION ALL
SELECT 39 AS id,28 AS parent_id,'演示用品' AS name
UNION ALL
SELECT 40 AS id,28 AS parent_id,'笔记本电脑托架' AS name
UNION ALL
SELECT 41 AS id,28 AS parent_id,'纸张处理' AS name
UNION ALL
SELECT 42 AS id,28 AS parent_id,'脉冲热封机' AS name
UNION ALL
SELECT 43 AS id,0 AS parent_id,'动漫' AS name
UNION ALL
SELECT 44 AS id,0 AS parent_id,'动物' AS name
UNION ALL
SELECT 45 AS id,0 AS parent_id,'动物/宠物用品' AS name
UNION ALL
SELECT 46 AS id,45 AS parent_id,'宠物用品' AS name
UNION ALL
SELECT 47 AS id,45 AS parent_id,'活体动物' AS name
UNION ALL
SELECT 48 AS id,0 AS parent_id,'商业/工业' AS name
UNION ALL
SELECT 49 AS id,48 AS parent_id,'农/畜牧/渔业专用设备' AS name
UNION ALL
SELECT 50 AS id,48 AS parent_id,'制造业' AS name
UNION ALL
SELECT 51 AS id,48 AS parent_id,'劳保/防护用品' AS name
UNION ALL
SELECT 52 AS id,48 AS parent_id,'医疗' AS name
UNION ALL
SELECT 53 AS id,48 AS parent_id,'工业仓储' AS name
UNION ALL
SELECT 54 AS id,48 AS parent_id,'工业仓储配件' AS name
UNION ALL
SELECT 55 AS id,48 AS parent_id,'广告与营销' AS name
UNION ALL
SELECT 56 AS id,48 AS parent_id,'建筑用品' AS name
UNION ALL
SELECT 57 AS id,48 AS parent_id,'影视' AS name
UNION ALL
SELECT 58 AS id,48 AS parent_id,'执法' AS name
UNION ALL
SELECT 59 AS id,48 AS parent_id,'材料处理' AS name
UNION ALL
SELECT 60 AS id,48 AS parent_id,'林业与伐木业' AS name
UNION ALL
SELECT 61 AS id,48 AS parent_id,'标识牌' AS name
UNION ALL
SELECT 62 AS id,48 AS parent_id,'清洁车/杂物篮' AS name
UNION ALL
SELECT 63 AS id,48 AS parent_id,'牙科' AS name
UNION ALL
SELECT 64 AS id,48 AS parent_id,'科学与实验' AS name
UNION ALL
SELECT 65 AS id,48 AS parent_id,'穿刺与纹身' AS name
UNION ALL
SELECT 66 AS id,48 AS parent_id,'美容美发业' AS name
UNION ALL
SELECT 67 AS id,48 AS parent_id,'自动化控制组件' AS name
UNION ALL
SELECT 68 AS id,48 AS parent_id,'酒店与宾馆' AS name
UNION ALL
SELECT 69 AS id,48 AS parent_id,'采矿与采石' AS name
UNION ALL
SELECT 70 AS id,48 AS parent_id,'重型机械' AS name
UNION ALL
SELECT 71 AS id,48 AS parent_id,'金融与保险' AS name
UNION ALL
SELECT 72 AS id,48 AS parent_id,'零售业' AS name
UNION ALL
SELECT 73 AS id,48 AS parent_id,'餐饮服务' AS name
UNION ALL
SELECT 74 AS id,0 AS parent_id,'婴幼儿用品' AS name
UNION ALL
SELECT 75 AS id,74 AS parent_id,'哺乳与喂养' AS name
UNION ALL
SELECT 76 AS id,74 AS parent_id,'如厕训练器' AS name
UNION ALL
SELECT 77 AS id,74 AS parent_id,'婴儿出行用品' AS name
UNION ALL
SELECT 78 AS id,74 AS parent_id,'婴儿卫生' AS name
UNION ALL
SELECT 79 AS id,74 AS parent_id,'婴儿安全用品' AS name
UNION ALL
SELECT 80 AS id,74 AS parent_id,'婴儿洗浴用品' AS name
UNION ALL
SELECT 81 AS id,74 AS parent_id,'婴儿玩具/活动设备' AS name
UNION ALL
SELECT 82 AS id,74 AS parent_id,'婴儿礼品套装' AS name
UNION ALL
SELECT 83 AS id,74 AS parent_id,'婴幼儿出行用品配件' AS name
UNION ALL
SELECT 84 AS id,74 AS parent_id,'尿布相关用品' AS name
UNION ALL
SELECT 85 AS id,74 AS parent_id,'襁褓/婴儿包毯' AS name
UNION ALL
SELECT 86 AS id,0 AS parent_id,'媒体' AS name
UNION ALL
SELECT 87 AS id,86 AS parent_id,'DVD 和视频' AS name
UNION ALL
SELECT 88 AS id,86 AS parent_id,'乐谱' AS name
UNION ALL
SELECT 89 AS id,86 AS parent_id,'书' AS name
UNION ALL
SELECT 90 AS id,86 AS parent_id,'产品说明书' AS name
UNION ALL
SELECT 91 AS id,86 AS parent_id,'报纸/杂志' AS name
UNION ALL
SELECT 92 AS id,86 AS parent_id,'木工项目计划' AS name
UNION ALL
SELECT 93 AS id,86 AS parent_id,'音乐' AS name
UNION ALL
SELECT 94 AS id,0 AS parent_id,'宗教/仪式' AS name
UNION ALL
SELECT 95 AS id,94 AS parent_id,'婚庆用品' AS name
UNION ALL
SELECT 96 AS id,94 AS parent_id,'宗教用品' AS name
UNION ALL
SELECT 97 AS id,94 AS parent_id,'纪念仪式用品' AS name
UNION ALL
SELECT 98 AS id,0 AS parent_id,'家具' AS name
UNION ALL
SELECT 99 AS id,98 AS parent_id,'办公家具' AS name
UNION ALL
SELECT 100 AS id,98 AS parent_id,'办公家具配件' AS name
UNION ALL
SELECT 101 AS id,98 AS parent_id,'可移动置物架' AS name
UNION ALL
SELECT 102 AS id,98 AS parent_id,'娱乐中心/电视柜' AS name
UNION ALL
SELECT 103 AS id,98 AS parent_id,'婴幼儿家具' AS name
UNION ALL
SELECT 104 AS id,98 AS parent_id,'家具套装' AS name
UNION ALL
SELECT 105 AS id,98 AS parent_id,'屏风/隔屏' AS name
UNION ALL
SELECT 106 AS id,98 AS parent_id,'床具与配件' AS name
UNION ALL
SELECT 107 AS id,98 AS parent_id,'户外家具' AS name
UNION ALL
SELECT 108 AS id,98 AS parent_id,'户外家具配件' AS name
UNION ALL
SELECT 109 AS id,98 AS parent_id,'房间隔板配件' AS name
UNION ALL
SELECT 110 AS id,98 AS parent_id,'搁架' AS name
UNION ALL
SELECT 111 AS id,98 AS parent_id,'日式床垫/折叠沙发床' AS name
UNION ALL
SELECT 112 AS id,98 AS parent_id,'架子配件' AS name
UNION ALL
SELECT 113 AS id,98 AS parent_id,'柜子/储物' AS name
UNION ALL
SELECT 114 AS id,98 AS parent_id,'桌子' AS name
UNION ALL
SELECT 115 AS id,98 AS parent_id,'桌子配件' AS name
UNION ALL
SELECT 116 AS id,98 AS parent_id,'椅子' AS name
UNION ALL
SELECT 117 AS id,98 AS parent_id,'椅子配件' AS name
UNION ALL
SELECT 118 AS id,98 AS parent_id,'沙发' AS name
UNION ALL
SELECT 119 AS id,98 AS parent_id,'沙发凳' AS name
UNION ALL
SELECT 120 AS id,98 AS parent_id,'沙发配件' AS name
UNION ALL
SELECT 121 AS id,98 AS parent_id,'蒲团/榻榻米底架' AS name
UNION ALL
SELECT 122 AS id,98 AS parent_id,'蒲团垫' AS name
UNION ALL
SELECT 123 AS id,98 AS parent_id,'长椅' AS name
UNION ALL
SELECT 124 AS id,0 AS parent_id,'家居与园艺' AS name
UNION ALL
SELECT 125 AS id,124 AS parent_id,'保险柜/保险箱' AS name
UNION ALL
SELECT 126 AS id,124 AS parent_id,'厨房/餐厅' AS name
UNION ALL
SELECT 127 AS id,124 AS parent_id,'壁炉' AS name
UNION ALL
SELECT 128 AS id,124 AS parent_id,'壁炉与木炉配件' AS name
UNION ALL
SELECT 129 AS id,124 AS parent_id,'家居用品' AS name
UNION ALL
SELECT 130 AS id,124 AS parent_id,'家用电器' AS name
UNION ALL
SELECT 131 AS id,124 AS parent_id,'家电配件' AS name
UNION ALL
SELECT 132 AS id,124 AS parent_id,'床上用品' AS name
UNION ALL
SELECT 133 AS id,124 AS parent_id,'应急准备' AS name
UNION ALL
SELECT 134 AS id,124 AS parent_id,'柴火炉' AS name
UNION ALL
SELECT 135 AS id,124 AS parent_id,'植物' AS name
UNION ALL
SELECT 136 AS id,124 AS parent_id,'泳池/水疗' AS name
UNION ALL
SELECT 137 AS id,124 AS parent_id,'浴室配件' AS name
UNION ALL
SELECT 138 AS id,124 AS parent_id,'照明设备' AS name
UNION ALL
SELECT 139 AS id,124 AS parent_id,'照明配件' AS name
UNION ALL
SELECT 140 AS id,124 AS parent_id,'草坪与花园' AS name
UNION ALL
SELECT 141 AS id,124 AS parent_id,'装饰' AS name
UNION ALL
SELECT 142 AS id,124 AS parent_id,'防洪、消防与可燃气体安全设备' AS name
UNION ALL
SELECT 143 AS id,124 AS parent_id,'雨伞/遮阳伞' AS name
UNION ALL
SELECT 144 AS id,124 AS parent_id,'雨伞套/盒' AS name
UNION ALL
SELECT 145 AS id,0 AS parent_id,'户外用品' AS name
UNION ALL
SELECT 146 AS id,0 AS parent_id,'机械' AS name
UNION ALL
SELECT 147 AS id,0 AS parent_id,'玩具/游戏' AS name
UNION ALL
SELECT 148 AS id,147 AS parent_id,'室外玩具设备' AS name
UNION ALL
SELECT 149 AS id,147 AS parent_id,'游戏' AS name
UNION ALL
SELECT 150 AS id,147 AS parent_id,'游戏计时器' AS name
UNION ALL
SELECT 151 AS id,147 AS parent_id,'玩具' AS name
UNION ALL
SELECT 152 AS id,147 AS parent_id,'益智玩具/游戏' AS name
UNION ALL
SELECT 153 AS id,0 AS parent_id,'电子产品' AS name
UNION ALL
SELECT 154 AS id,153 AS parent_id,'GPS 导航系统' AS name
UNION ALL
SELECT 155 AS id,153 AS parent_id,'GPS 配件' AS name
UNION ALL
SELECT 156 AS id,153 AS parent_id,'GPS跟踪设备' AS name
UNION ALL
SELECT 157 AS id,153 AS parent_id,'大型游戏机/街机' AS name
UNION ALL
SELECT 158 AS id,153 AS parent_id,'手机配件' AS name
UNION ALL
SELECT 159 AS id,153 AS parent_id,'打印/复印/扫描/传真' AS name
UNION ALL
SELECT 160 AS id,153 AS parent_id,'收费装置' AS name
UNION ALL
SELECT 161 AS id,153 AS parent_id,'测速雷达' AS name
UNION ALL
SELECT 162 AS id,153 AS parent_id,'电子游戏机' AS name
UNION ALL
SELECT 163 AS id,153 AS parent_id,'电子游戏机配件' AS name
UNION ALL
SELECT 164 AS id,153 AS parent_id,'电子配件' AS name
UNION ALL
SELECT 165 AS id,153 AS parent_id,'电路板和组件' AS name
UNION ALL
SELECT 166 AS id,153 AS parent_id,'组件' AS name
UNION ALL
SELECT 167 AS id,153 AS parent_id,'网络' AS name
UNION ALL
SELECT 168 AS id,153 AS parent_id,'航海电子设备' AS name
UNION ALL
SELECT 169 AS id,153 AS parent_id,'视频' AS name
UNION ALL
SELECT 170 AS id,153 AS parent_id,'计算机' AS name
UNION ALL
SELECT 171 AS id,153 AS parent_id,'通讯' AS name
UNION ALL
SELECT 172 AS id,153 AS parent_id,'雷达探测器' AS name
UNION ALL
SELECT 173 AS id,153 AS parent_id,'音频' AS name
UNION ALL
SELECT 174 AS id,0 AS parent_id,'相机与光学器件' AS name
UNION ALL
SELECT 175 AS id,174 AS parent_id,'光学器件' AS name
UNION ALL
SELECT 176 AS id,174 AS parent_id,'照片冲印/摄影棚器材' AS name
UNION ALL
SELECT 177 AS id,174 AS parent_id,'相机' AS name
UNION ALL
SELECT 178 AS id,174 AS parent_id,'相机与光学器件配件' AS name
UNION ALL
SELECT 179 AS id,0 AS parent_id,'箱包' AS name
UNION ALL
SELECT 180 AS id,179 AS parent_id,'公文包' AS name
UNION ALL
SELECT 181 AS id,179 AS parent_id,'化妆箱' AS name
UNION ALL
SELECT 182 AS id,179 AS parent_id,'尿布包' AS name
UNION ALL
SELECT 183 AS id,179 AS parent_id,'手提旅行包/运动桶包' AS name
UNION ALL
SELECT 184 AS id,179 AS parent_id,'旅行箱/包' AS name
UNION ALL
SELECT 185 AS id,179 AS parent_id,'洗漱包/盥洗包' AS name
UNION ALL
SELECT 186 AS id,179 AS parent_id,'箱包配件' AS name
UNION ALL
SELECT 187 AS id,179 AS parent_id,'背包' AS name
UNION ALL
SELECT 188 AS id,179 AS parent_id,'腰包' AS name
UNION ALL
SELECT 189 AS id,179 AS parent_id,'购物袋' AS name
UNION ALL
SELECT 190 AS id,179 AS parent_id,'邮差包' AS name
UNION ALL
SELECT 191 AS id,179 AS parent_id,'防潮箱/盒' AS name
UNION ALL
SELECT 192 AS id,0 AS parent_id,'艺术与娱乐' AS name
UNION ALL
SELECT 193 AS id,192 AS parent_id,'活动门票' AS name
UNION ALL
SELECT 194 AS id,192 AS parent_id,'爱好/艺术创作' AS name
UNION ALL
SELECT 195 AS id,192 AS parent_id,'聚会/庆典' AS name
UNION ALL
SELECT 196 AS id,0 AS parent_id,'软件' AS name
UNION ALL
SELECT 197 AS id,196 AS parent_id,'数字商品与货币' AS name
UNION ALL
SELECT 198 AS id,196 AS parent_id,'电子游戏软件' AS name
UNION ALL
SELECT 199 AS id,196 AS parent_id,'电脑软件' AS name
) seed WHERE NOT EXISTS (SELECT 1 FROM custom_category_seed WHERE id=1);
INSERT IGNORE INTO custom_category_seed VALUES(1);
INSERT INTO sys_menu(id,parent_id,menu_name,menu_type,perms,path,component,icon,sort_order,status) VALUES
(6,0,'收录数据',0,NULL,NULL,NULL,'DataLine',2,1),
(16,6,'站点明细',1,'dashboard:site:view','/indexing/sites','dashboard/IndexingList','Document',1,1),
(68,6,'建站者汇总',1,'dashboard:site:view','/indexing/builders','dashboard/IndexingList','User',2,1),
(69,6,'服务器汇总',1,'dashboard:site:view','/indexing/servers','dashboard/IndexingList','Monitor',3,1),
(70,0,'自定义分类',1,'category:list','/categories','category/CategoryList','CollectionTag',6,1),
(71,70,'维护自定义分类',2,'category:manage',NULL,NULL,NULL,1,1)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),perms=VALUES(perms),path=VALUES(path),component=VALUES(component),icon=VALUES(icon),sort_order=VALUES(sort_order),status=VALUES(status);
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6 FROM sys_role_menu WHERE menu_id=16;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,68 FROM sys_role_menu WHERE menu_id=16;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,69 FROM sys_role_menu WHERE menu_id=16;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES(1,70),(1,71),(2,70),(2,71);
