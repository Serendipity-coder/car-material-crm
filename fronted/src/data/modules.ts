export type FieldKind = 'text' | 'number' | 'date' | 'select' | 'multiselect' | 'textarea' | 'money' | 'lines'

export interface SelectOption {
  label: string
  value: string
}

export interface FormField {
  key: string
  label: string
  kind?: FieldKind
  required?: boolean
  placeholder?: string
  options?: SelectOption[]
  span?: number
}

export interface ModuleStat {
  label: string
  key: string
  tone?: 'mint' | 'gold' | 'rose'
  prefix?: string
}

export interface TableColumn {
  key: string
  label: string
  width?: number
  type?: 'status' | 'money' | 'date' | 'muted' | 'progress'
}

export interface ModuleDefinition {
  key: string
  path: string
  title: string
  eyebrow: string
  description: string
  icon: string
  permission: string
  group: string
  singular: string
  searchPlaceholder?: string
  filters: FormField[]
  fields: FormField[]
  columns: TableColumn[]
  stats: ModuleStat[]
  primaryAction?: string
}

const statusOptions: SelectOption[] = [
  { label: '进行中', value: '进行中' },
  { label: '待处理', value: '待处理' },
  { label: '已完成', value: '已完成' },
  { label: '已暂停', value: '已暂停' },
]
const customerTypeOptions: SelectOption[] = ['汽修厂', '洗车店', '汽车美容店', '快修店', '轮胎店', '4S 店', '汽车用品店', '区域经销商', '其他门店'].map((x) => ({ label: x, value: x }))
const industryCategoryOptions: SelectOption[] = ['轮胎及轮胎配件', '轮胎养护', '洗车耗材', '汽车美容耗材', '维修辅料', '工具设备', '防护用品', '门店套餐'].map((x) => ({ label: x, value: x }))
const detailedQuoteStatusOptions: SelectOption[] = ['草稿', '已提交', '客户查看', '议价中', '已接受', '已拒绝', '已过期', '已通过'].map((x) => ({ label: x, value: x }))
const orderStatusOptions: SelectOption[] = ['草稿', '待确认', '已确认', '已完成', '已关闭', '已作废'].map((x) => ({ label: x, value: x }))
const shipmentStatusOptions: SelectOption[] = ['未发货', '部分发货', '全部发货', '运输中', '已签收', '发货异常'].map((x) => ({ label: x, value: x }))
const aftersalesStatusOptions: SelectOption[] = ['待处理', '处理中', '待客户确认', '已解决', '已关闭'].map((x) => ({ label: x, value: x }))
const returnStatusOptions: SelectOption[] = ['待审核', '待寄回', '处理中', '待发出', '已完成', '已驳回'].map((x) => ({ label: x, value: x }))
const healthStatusOptions: SelectOption[] = ['健康', '正常', '需要关注', '高风险', '已流失'].map((x) => ({ label: x, value: x }))
const leadStageOptions = ['待分配', '已分配', '首次联系', '持续跟进', '高意向', '转化为客户', '无法联系', '无采购需求', '已有固定供应商', '价格不接受', '门店已关闭', '重复线索', '暂不考虑'].map((x) => ({ label: x, value: x }))
const customerLevelOptions = ['S级｜战略客户', 'A级｜重点客户', 'B级｜稳定客户', 'C级｜普通客户', 'D级｜沉睡客户'].map((x) => ({ label: x, value: x }))
const field = (key: string, label: string, kind: FieldKind = 'text', options?: SelectOption[], required = false): FormField => ({ key, label, kind, options, required })
const col = (key: string, label: string, type?: TableColumn['type'], width?: number): TableColumn => ({ key, label, type, width })

export const moduleDefinitions: Record<string, ModuleDefinition> = {
  todos: {
    key: 'todos', path: '/todos', title: '我的待办', eyebrow: 'MY WORK QUEUE', description: '把重要事项排好顺序，专注推进今天的客户工作。', icon: 'Checked', permission: 'work:todo:list', group: '工作台', singular: '待办事项', searchPlaceholder: '搜索待办事项',
    filters: [field('status', '状态', 'select', statusOptions), field('priority', '优先级', 'select', ['高', '中', '低'].map((x) => ({ label: x, value: x })))],
    fields: [field('title', '事项名称', 'text', undefined, true), field('taskType', '任务类型', 'select', ['销售跟进', '报价跟进', '补货提醒', '回款提醒', '客户回访', '售后处理'].map((x) => ({ label: x, value: x }))), field('relatedType', '关联对象类型'), field('relatedId', '关联对象 ID'), field('related', '关联业务', 'text'), field('dueDate', '截止日期', 'date', undefined, true), field('priority', '优先级', 'select', ['高', '中', '低'].map((x) => ({ label: x, value: x })), true), field('owner', '负责人', 'select', [], true), field('status', '状态', 'select', ['待处理', '已完成', '已取消'].map((x) => ({ label: x, value: x })), true), field('result', '处理结果', 'textarea'), field('note', '备注', 'textarea')],
    columns: [col('title', '待办事项'), col('related', '关联业务'), col('owner', '负责人'), col('dueDate', '截止日期', 'date'), col('priority', '优先级'), col('status', '状态', 'status')],
    stats: [{ label: '待处理', key: '待处理', tone: 'mint' }, { label: '今日到期', key: 'today', tone: 'gold' }, { label: '高优先级', key: '高', tone: 'rose' }, { label: '已完成', key: '已完成' }],
  },
  leads: {
    key: 'leads', path: '/leads', title: '销售线索', eyebrow: 'LEAD PIPELINE', description: '捕捉每一次新需求，让潜在机会更快进入销售流程。', icon: 'Aim', permission: 'crm:lead:list', group: '客户管理', singular: '销售线索', searchPlaceholder: '搜索线索名称或公司',
    filters: [field('stage', '线索阶段', 'select', leadStageOptions), field('customerType', '门店类型', 'select', customerTypeOptions), field('owner', '负责人', 'select', [])],
    fields: [field('name', '线索名称', 'text', undefined, true), field('company', '主体名称', 'text', undefined, true), field('storeName', '门店名称'), field('customerType', '门店类型', 'select', customerTypeOptions), field('contact', '联系人'), field('phone', '联系电话'), field('region', '所在地区'), field('storeSize', '门店规模'), field('businessItems', '经营项目'), field('currentBrand', '当前采购品牌'), field('purchaseFrequency', '预计采购频率', 'select', ['每周', '每月', '每季度', '不确定'].map((x) => ({ label: x, value: x }))), field('interestCategories', '意向产品品类', 'multiselect', industryCategoryOptions), field('source', '线索来源', 'select', ['地推拜访', '展会', '老客户转介绍', '短视频平台', '微信添加', '电话开发', '官网提交', '电商平台', '区域市场名单', '合作伙伴推荐', '其他'].map((x) => ({ label: x, value: x })), true), field('intentLevel', '意向等级', 'select', ['高', '中', '低'].map((x) => ({ label: x, value: x }))), field('stage', '线索阶段', 'select', leadStageOptions, true), field('owner', '负责人', 'select', []), field('score', '线索评分', 'number'), field('nextDate', '下次跟进', 'date'), field('invalidReason', '失效原因'), field('note', '主要需求/备注', 'textarea')],
    columns: [col('name', '线索名称'), col('company', '公司'), col('source', '来源'), col('owner', '负责人'), col('stage', '阶段', 'status'), col('score', '评分'), col('nextDate', '下次跟进', 'date')],
    stats: [{ label: '有效线索', key: '有效线索', tone: 'mint' }, { label: '待首次联系', key: '新线索', tone: 'gold' }, { label: '本月新增', key: '本月' }, { label: '转化率', key: '转化率', prefix: '%' }],
  },
  pool: {
    key: 'pool', path: '/pool', title: '客户公海', eyebrow: 'SHARED CUSTOMER POOL', description: '统一管理待认领客户，按规则分配跟进责任。', icon: 'Connection', permission: 'crm:pool:list', group: '客户管理', singular: '公海客户', searchPlaceholder: '搜索公海客户名称',
    filters: [field('industry', '所属行业', 'select', ['汽修服务', '洗车美容', '汽车用品经销', '汽配批发', '轮胎服务'].map((x) => ({ label: x, value: x }))), field('level', '客户等级', 'select', customerLevelOptions)],
    fields: [field('name', '客户名称', 'text', undefined, true), field('customerType', '客户类型', 'select', customerTypeOptions, true), field('unifiedCode', '统一社会信用代码'), field('industry', '所属行业', 'select', ['汽车后市场服务', '汽修服务', '洗车美容', '汽车用品经销', '其他'].map((x) => ({ label: x, value: x })), true), field('level', '客户等级', 'select', customerLevelOptions, true), field('purchaseCategories', '需求产品品类', 'multiselect', industryCategoryOptions), field('tags', '客户标签', 'multiselect', ['高价值客户', '价格敏感', '品牌忠诚', '需要账期', '高频采购', '低频采购', '有欠款', '易流失', '重点维护', '适合发展为经销商'].map((x) => ({ label: x, value: x }))), field('source', '客户来源'), field('owner', '负责人'), field('storeCount', '门店数量', 'number'), field('phone', '联系电话'), field('email', '电子邮箱'), field('firstCooperationDate', '首次合作时间', 'date'), field('lastContact', '最后跟进', 'date'), field('nextReplenishmentDate', '预计补货日期', 'date'), field('healthStatus', '客户健康度', 'select', healthStatusOptions), field('storeName', '默认门店'), field('address', '收货地址'), field('annualValue', '累计采购额', 'money'), field('purchaseCount', '累计采购次数', 'number'), field('status', '客户状态', 'select', ['潜在客户', '正常合作', '重点维护', '暂停合作', '沉睡客户', '高风险客户', '已流失客户'].map((x) => ({ label: x, value: x })), true), field('note', '客户备注', 'textarea')],
    columns: [col('name', '客户名称'), col('industry', '行业'), col('level', '客户等级'), col('source', '来源'), col('lastContact', '最后跟进', 'date'), col('status', '状态', 'status')],
    stats: [{ label: '公海客户', key: '客户总数', tone: 'mint' }, { label: '待认领', key: '待认领', tone: 'gold' }, { label: '本周新增', key: '本周' }, { label: '待分配', key: '待分配', tone: 'rose' }], primaryAction: '新增客户',
  },
  customers: {
    key: 'customers', path: '/customers', title: '客户档案', eyebrow: 'CUSTOMER DIRECTORY', description: '沉淀客户全景信息，连接联系人、跟进和销售机会。', icon: 'User', permission: 'crm:customer:list', group: '客户管理', singular: '客户', searchPlaceholder: '搜索客户名称',
    filters: [field('customerType', '客户类型', 'select', customerTypeOptions), field('industry', '所属行业', 'select', ['汽车后市场服务', '汽修服务', '洗车美容', '汽车用品经销', '其他'].map((x) => ({ label: x, value: x }))), field('level', '客户等级', 'select', customerLevelOptions), field('owner', '负责人', 'select', [])],
    fields: [field('name', '客户名称', 'text', undefined, true), field('customerType', '客户类型', 'select', customerTypeOptions, true), field('unifiedCode', '统一社会信用代码'), field('industry', '所属行业', 'select', ['汽车后市场服务', '汽修服务', '洗车美容', '汽车用品经销', '其他'].map((x) => ({ label: x, value: x })), true), field('level', '客户等级', 'select', customerLevelOptions, true), field('purchaseCategories', '需求产品品类', 'multiselect', industryCategoryOptions), field('tags', '客户标签', 'multiselect', ['高价值客户', '价格敏感', '品牌忠诚', '需要账期', '高频采购', '低频采购', '有欠款', '易流失', '重点维护', '适合发展为经销商'].map((x) => ({ label: x, value: x }))), field('source', '客户来源'), field('owner', '客户负责人', 'select', [], true), field('storeCount', '门店数量', 'number'), field('phone', '联系电话'), field('email', '电子邮箱'), field('firstCooperationDate', '首次合作时间', 'date'), field('lastContact', '最后跟进', 'date'), field('nextReplenishmentDate', '预计补货日期', 'date'), field('healthStatus', '客户健康度', 'select', healthStatusOptions), field('storeName', '默认门店'), field('address', '详细地址'), field('annualValue', '累计采购额', 'money'), field('purchaseCount', '累计采购次数', 'number'), field('status', '客户状态', 'select', ['潜在客户', '正常合作', '重点维护', '暂停合作', '沉睡客户', '高风险客户', '已流失客户'].map((x) => ({ label: x, value: x })), true), field('note', '客户备注', 'textarea')],
    columns: [col('name', '客户名称'), col('industry', '行业'), col('level', '客户等级'), col('owner', '负责人'), col('annualValue', '年度采购额', 'money'), col('lastContact', '最近跟进', 'date'), col('status', '状态', 'status')],
    stats: [{ label: '客户总数', key: '客户总数', tone: 'mint' }, { label: '重点客户', key: '重点客户', tone: 'gold' }, { label: '本月新增', key: '本月' }, { label: '待跟进', key: '待跟进', tone: 'rose' }],
  },
  contacts: {
    key: 'contacts', path: '/contacts', title: '联系人', eyebrow: 'CONTACT NETWORK', description: '维护客户关键联系人与沟通方式，协作触达不遗漏。', icon: 'Avatar', permission: 'crm:contact:list', group: '客户管理', singular: '联系人', searchPlaceholder: '搜索联系人姓名',
    filters: [field('customer', '所属客户', 'select', []), field('role', '联系人角色', 'select', ['老板', '采购负责人', '店长', '技术负责人', '财务人员', '仓库人员'].map((x) => ({ label: x, value: x })))],
    fields: [field('name', '联系人姓名', 'text', undefined, true), field('customer', '所属客户', 'select', [], true), field('storeName', '所属门店'), field('role', '联系人角色', 'select', ['老板', '采购负责人', '店长', '技术负责人', '财务人员', '仓库人员'].map((x) => ({ label: x, value: x })), true), field('position', '职位'), field('phone', '手机号码'), field('wechat', '微信'), field('contactPreference', '联系偏好', 'select', ['电话', '微信', '短信', '上门'].map((x) => ({ label: x, value: x }))), field('email', '电子邮箱'), field('isPrimary', '主要联系人', 'select', [{ label: '是', value: '是' }, { label: '否', value: '否' }]), field('lastContact', '最近联系', 'date'), field('birthday', '生日', 'date'), field('note', '备注', 'textarea')],
    columns: [col('name', '联系人'), col('customer', '所属客户'), col('position', '职位'), col('role', '角色'), col('phone', '联系电话'), col('email', '邮箱')],
    stats: [{ label: '联系人总数', key: '总数', tone: 'mint' }, { label: '决策人', key: '决策人', tone: 'gold' }, { label: '关联客户', key: '关联客户' }, { label: '本月新增', key: '本月' }],
  },
  followups: {
    key: 'followups', path: '/followups', title: '跟进记录', eyebrow: 'CUSTOMER TOUCHPOINTS', description: '持续记录关键沟通、阶段反馈与下一步计划。', icon: 'ChatDotRound', permission: 'crm:followup:list', group: '客户管理', singular: '跟进记录', searchPlaceholder: '搜索客户或跟进内容',
    filters: [field('customer', '客户', 'select', []), field('type', '跟进方式', 'select', ['电话', '微信', '到店拜访', '视频会议', '展会沟通', '试用反馈', '报价沟通', '需求确认', '商机推进', '补货需求沟通'].map((x) => ({ label: x, value: x }))), field('owner', '负责人', 'select', [])],
    fields: [field('customer', '关联客户', 'select', [], true), field('contact', '关联联系人', 'select', []), field('storeName', '关联门店'), field('lead', '关联线索', 'select', []), field('date', '跟进时间', 'date', undefined, true), field('type', '跟进方式', 'select', ['电话', '微信', '到店拜访', '视频会议', '展会沟通', '试用反馈', '报价沟通', '需求确认', '商机推进', '补货需求沟通'].map((x) => ({ label: x, value: x })), true), field('currentNeed', '当前需求', 'textarea'), field('interestChange', '客户意向变化', 'select', ['上升', '稳定', '下降', '无明确变化'].map((x) => ({ label: x, value: x }))), field('hasOpportunity', '是否产生商机', 'select', [{ label: '是', value: '是' }, { label: '否', value: '否' }]), field('needsQuote', '是否需要报价', 'select', [{ label: '是', value: '是' }, { label: '否', value: '否' }]), field('owner', '跟进人员', 'select', [], true), field('outcome', '沟通结果', 'textarea', undefined, true), field('customerFeedback', '客户反馈', 'textarea'), field('attachment', '附件/沟通凭证'), field('nextDate', '下次跟进日期', 'date'), field('nextPlan', '下次跟进计划', 'textarea'), field('status', '状态', 'select', statusOptions, true)],
    columns: [col('customer', '客户'), col('contact', '联系人'), col('date', '跟进时间', 'date'), col('type', '方式'), col('owner', '跟进人员'), col('outcome', '沟通摘要'), col('nextDate', '下次计划', 'date')],
    stats: [{ label: '跟进总数', key: '总数', tone: 'mint' }, { label: '本周跟进', key: '本周', tone: 'gold' }, { label: '待继续跟进', key: '待处理', tone: 'rose' }, { label: '覆盖客户', key: '客户数' }],
  },
  'product-categories': {
    key: 'product-categories', path: '/product-categories', title: '产品分类', eyebrow: 'PRODUCT CATALOG', description: '用清晰的分类结构管理产品目录与展示口径。', icon: 'Grid', permission: 'product:category:list', group: '产品管理', singular: '产品分类', searchPlaceholder: '搜索分类名称',
    filters: [field('status', '状态', 'select', statusOptions)],
    fields: [field('name', '分类名称', 'text', undefined, true), field('parent', '上级分类', 'select', [{ label: '无上级分类', value: '—' }]), field('sort', '排序', 'number'), field('path', '分类路径'), field('description', '分类说明', 'textarea'), field('status', '状态', 'select', statusOptions, true)],
    columns: [col('name', '分类名称'), col('parent', '上级分类'), col('productCount', '产品数量'), col('sort', '排序'), col('status', '状态', 'status')],
    stats: [{ label: '分类总数', key: '总数', tone: 'mint' }, { label: '启用分类', key: '启用中', tone: 'gold' }, { label: '在售产品', key: '产品数' }, { label: '本月新增', key: '本月' }],
  },
  products: {
    key: 'products', path: '/products', title: '产品档案', eyebrow: 'PRODUCT MASTER', description: '统一维护产品编码、价格、库存与销售状态。', icon: 'Goods', permission: 'product:product:list', group: '产品管理', singular: '产品', searchPlaceholder: '搜索产品名称或编码',
    filters: [field('category', '产品分类', 'select', ['清洗美容', '润滑养护', '底盘制动', '滤清器', '电气照明', '工具设备', '轮胎'].map((x) => ({ label: x, value: x }))), field('status', '销售状态', 'select', statusOptions)],
    fields: [field('name', '产品名称', 'text', undefined, true), field('sku', 'SKU 编码', 'text', undefined, true), field('brand', '品牌'), field('category', '产品分类', 'select', [...industryCategoryOptions], true), field('applicableScenes', '适用场景'), field('spec', '规格型号'), field('packaging', '包装单位'), field('conversion', '包装换算'), field('unit', '基础计量单位'), field('repurchaseCycle', '预计复购周期'), field('relatedProducts', '关联产品'), field('substituteProducts', '可替代产品'), field('salesTags', '销售属性标签', 'multiselect', ['适合首次试用', '高复购产品', '高毛利产品', '适合套餐', '适合交叉销售'].map((x) => ({ label: x, value: x }))), field('usage', '使用说明', 'textarea'), field('price', '标准价格', 'money', undefined, true), field('stock', '库存数量', 'number'), field('status', '产品状态', 'select', ['在售', '下架', '草稿'].map((x) => ({ label: x, value: x })), true), field('description', '产品说明/销售话术', 'textarea')],
    columns: [col('sku', '产品编码'), col('name', '产品名称'), col('category', '分类'), col('spec', '规格'), col('price', '标准价格', 'money'), col('stock', '库存'), col('status', '状态', 'status')],
    stats: [{ label: '产品总数', key: '总数', tone: 'mint' }, { label: '在售产品', key: '在售', tone: 'gold' }, { label: '低库存', key: '低库存', tone: 'rose' }, { label: '产品分类', key: '分类数' }],
  },
  'customer-prices': {
    key: 'customer-prices', path: '/customer-prices', title: '客户价格', eyebrow: 'CUSTOMER PRICING', description: '为客户维护专属报价，确保合同与订单使用一致。', icon: 'PriceTag', permission: 'product:price:list', group: '产品管理', singular: '客户价格', searchPlaceholder: '搜索客户或产品',
    filters: [field('customer', '客户', 'select', []), field('status', '状态', 'select', statusOptions)],
    fields: [field('customer', '客户', 'select', []), field('customerLevel', '适用客户等级', 'select', customerLevelOptions), field('product', 'SKU/产品名称', 'select', [], true), field('priceType', '价格类型', 'select', ['建议零售价', '经销商价格', '指定客户协议价', '客户等级价格', '阶梯采购价格', '促销价格'].map((x) => ({ label: x, value: x })), true), field('minQuantity', '起订数量', 'number'), field('maxQuantity', '数量上限', 'number'), field('price', '客户单价', 'money', undefined, true), field('minimumPrice', '最低成交价', 'money'), field('startDate', '生效日期', 'date', undefined, true), field('endDate', '失效日期', 'date'), field('priority', '规则优先级', 'number'), field('status', '状态', 'select', statusOptions, true), field('note', '说明', 'textarea')],
    columns: [col('customer', '客户'), col('product', '产品'), col('price', '客户单价', 'money'), col('startDate', '生效日期', 'date'), col('endDate', '失效日期', 'date'), col('status', '状态', 'status')],
    stats: [{ label: '有效价格', key: '有效', tone: 'mint' }, { label: '覆盖客户', key: '客户数', tone: 'gold' }, { label: '本月新增', key: '本月' }, { label: '即将到期', key: '即将到期', tone: 'rose' }],
  },
  opportunities: {
    key: 'opportunities', path: '/opportunities', title: '销售商机', eyebrow: 'OPPORTUNITY BOARD', description: '跟踪销售阶段与预计成交，把机会推进到订单。', icon: 'TrendCharts', permission: 'crm:opportunity:list', group: '销售管理', singular: '销售商机', searchPlaceholder: '搜索商机名称或客户',
    filters: [field('stage', '商机阶段', 'select', ['需求发现', '产品推荐', '试用/样品', '报价', '议价', '待确认', '赢单', '输单', '需求确认', '方案评估', '商务谈判'].map((x) => ({ label: x, value: x }))), field('owner', '负责人', 'select', [])],
    fields: [field('name', '商机名称', 'text', undefined, true), field('customer', '客户', 'select', [], true), field('storeName', '采购门店'), field('purchaseCategories', '采购产品品类'), field('amount', '预计金额', 'money', undefined, true), field('stage', '商机阶段', 'select', ['需求发现', '产品推荐', '试用/样品', '报价', '议价', '待确认', '赢单', '输单'].map((x) => ({ label: x, value: x })), true), field('probability', '成交概率 %', 'number'), field('owner', '负责人', 'select', [], true), field('closingDate', '预计成交日期', 'date'), field('nextAction', '下一步动作'), field('lossReason', '丢单原因', 'select', ['价格过高', '已有固定供应商', '产品不匹配', '暂无预算', '样品效果不理想', '交付周期不接受', '联系人变更', '客户停止经营', '竞争对手低价', '其他'].map((x) => ({ label: x, value: x }))), field('note', '推进计划', 'textarea')],
    columns: [col('name', '商机名称'), col('customer', '客户'), col('amount', '预计金额', 'money'), col('stage', '销售阶段', 'status'), col('probability', '成交概率', 'progress'), col('owner', '负责人'), col('closingDate', '预计成交', 'date')],
    stats: [{ label: '商机总额', key: '总额', tone: 'mint', prefix: '¥' }, { label: '进行中商机', key: '进行中', tone: 'gold' }, { label: '累计赢单', key: '赢单' }, { label: '平均成交率', key: '成交率', prefix: '%' }],
  },
  quotes: {
    key: 'quotes', path: '/quotes', title: '报价单', eyebrow: 'QUOTATION DESK', description: '整理客户报价和产品明细，清楚跟踪有效期与审核状态。', icon: 'Tickets', permission: 'sales:quote:list', group: '销售管理', singular: '报价单', searchPlaceholder: '搜索报价单号或客户',
    filters: [field('status', '报价状态', 'select', detailedQuoteStatusOptions), field('customer', '客户', 'select', [])],
    fields: [field('quoteNo', '报价单号', 'text', undefined, true), field('customer', '客户', 'select', [], true), field('storeName', '采购门店'), field('opportunity', '关联商机', 'select', []), field('items', '产品明细', 'lines', [], true), field('amount', '报价金额', 'money', undefined, true), field('tax', '税费', 'money'), field('freight', '运费', 'money'), field('validUntil', '有效期至', 'date', undefined, true), field('paymentTerms', '付款条件'), field('deliveryNote', '交付说明', 'textarea'), field('owner', '报价人', 'select', [], true), field('status', '报价状态', 'select', detailedQuoteStatusOptions, true), field('note', '报价说明', 'textarea')],
    columns: [col('quoteNo', '报价单号'), col('customer', '客户'), col('opportunity', '关联商机'), col('amount', '报价金额', 'money'), col('owner', '报价人'), col('validUntil', '有效期至', 'date'), col('status', '状态', 'status')],
    stats: [{ label: '报价总数', key: '总数', tone: 'mint' }, { label: '待审核', key: '待处理', tone: 'gold' }, { label: '本月金额', key: '本月金额' }, { label: '即将到期', key: '即将到期', tone: 'rose' }],
  },
  orders: {
    key: 'orders', path: '/orders', title: '销售订单', eyebrow: 'ORDER MANAGEMENT', description: '从签约到交付统一跟进订单进度、金额与回款状态。', icon: 'ShoppingCart', permission: 'sales:order:list', group: '销售管理', singular: '销售订单', searchPlaceholder: '搜索订单编号或客户',
    filters: [field('status', '订单状态', 'select', orderStatusOptions), field('deliveryStatus', '履约状态', 'select', shipmentStatusOptions), field('customer', '客户', 'select', []), field('paymentStatus', '回款状态', 'select', ['未回款', '部分回款', '已结清', '逾期'].map((x) => ({ label: x, value: x })))],
    fields: [field('orderNo', '订单编号', 'text', undefined, true), field('customer', '客户', 'select', [], true), field('storeName', '采购门店'), field('contact', '联系人'), field('shippingAddress', '收货地址'), field('quoteNo', '来源报价', 'select', []), field('opportunity', '关联商机', 'select', []), field('items', '产品明细', 'lines', [], true), field('discount', '订单折扣 %', 'number'), field('tax', '税费', 'money'), field('freight', '运费', 'money'), field('amount', '订单金额', 'money', undefined, true), field('orderDate', '签订日期', 'date', undefined, true), field('estimatedDeliveryDate', '预计交付日期', 'date'), field('deliveryStatus', '履约状态', 'select', shipmentStatusOptions, true), field('paymentTerms', '付款条件'), field('paymentStatus', '回款状态', 'select', ['未回款', '部分回款', '已结清', '逾期'].map((x) => ({ label: x, value: x })), true), field('owner', '负责人', 'select', []), field('note', '订单备注', 'textarea'), field('status', '订单状态', 'select', orderStatusOptions, true)],
    columns: [col('orderNo', '订单编号'), col('customer', '客户'), col('amount', '订单金额', 'money'), col('orderDate', '签订日期', 'date'), col('deliveryStatus', '履约状态', 'status'), col('paymentStatus', '回款状态', 'status'), col('status', '订单状态', 'status')],
    stats: [{ label: '订单总额', key: '总额', tone: 'mint', prefix: '¥' }, { label: '待履约', key: '待处理', tone: 'gold' }, { label: '待回款', key: '待回款', tone: 'rose' }, { label: '本月新单', key: '本月' }],
  },
  returns: {
    key: 'returns', path: '/returns', title: '退换货单', eyebrow: 'RETURNS & EXCHANGES', description: '记录退换货申请、原因和处理结果，闭环售后履约。', icon: 'RefreshLeft', permission: 'sales:return:list', group: '售后管理', singular: '退换货单', searchPlaceholder: '搜索申请编号或订单',
    filters: [field('type', '申请类型', 'select', ['退货', '换货', '补发'].map((x) => ({ label: x, value: x }))), field('status', '处理状态', 'select', returnStatusOptions)],
    fields: [field('returnNo', '申请编号', 'text', undefined, true), field('orderNo', '关联订单', 'select', [], true), field('ticketNo', '关联售后工单'), field('customer', '客户', 'select', [], true), field('storeName', '采购门店'), field('product', '涉及 SKU', 'select', [], true), field('quantity', '数量', 'number', undefined, true), field('type', '业务类型', 'select', ['退货', '换货', '补发'].map((x) => ({ label: x, value: x })), true), field('amount', '涉及金额', 'money'), field('reason', '申请原因', 'textarea', undefined, true), field('processingMethod', '处理方式'), field('carrier', '物流公司'), field('trackingNo', '物流单号'), field('auditor', '审核人员'), field('refundAmount', '退款金额', 'money'), field('replacementQuantity', '补发数量', 'number'), field('completedAt', '完成时间', 'date'), field('status', '处理状态', 'select', returnStatusOptions, true)],
    columns: [col('returnNo', '申请编号'), col('orderNo', '关联订单'), col('customer', '客户'), col('type', '类型'), col('amount', '涉及金额', 'money'), col('createdAt', '申请日期', 'date'), col('status', '处理状态', 'status')],
    stats: [{ label: '申请总数', key: '总数', tone: 'mint' }, { label: '待处理', key: '待处理', tone: 'gold' }, { label: '本月退货金额', key: '本月金额' }, { label: '已完成', key: '已完成' }],
  },
  shipments: {
    key: 'shipments', path: '/shipments', title: '发货记录', eyebrow: 'FULFILLMENT TRACKER', description: '掌握发货批次与物流轨迹，及时同步客户交付进展。', icon: 'Van', permission: 'delivery:shipment:list', group: '交付与回款', singular: '发货记录', searchPlaceholder: '搜索发货单号或客户',
    filters: [field('status', '发货状态', 'select', shipmentStatusOptions), field('customer', '客户', 'select', [])],
    fields: [field('shipmentNo', '发货单号', 'text', undefined, true), field('orderNo', '关联订单', 'select', [], true), field('customer', '客户', 'select', [], true), field('storeName', '收货门店'), field('items', '发货产品明细', 'lines', []), field('carrier', '物流公司'), field('deliveryMethod', '配送方式'), field('trackingNo', '物流单号'), field('shipmentDate', '发货日期', 'date', undefined, true), field('shipper', '发货人员'), field('signStatus', '签收状态', 'select', ['未签收', '已签收', '异常'].map((x) => ({ label: x, value: x }))), field('signDate', '签收日期', 'date'), field('status', '发货状态', 'select', shipmentStatusOptions, true), field('exceptionNote', '异常说明', 'textarea'), field('note', '备注', 'textarea')],
    columns: [col('shipmentNo', '发货单号'), col('orderNo', '订单编号'), col('customer', '客户'), col('carrier', '物流公司'), col('trackingNo', '物流单号'), col('shipmentDate', '发货时间', 'date'), col('status', '状态', 'status')],
    stats: [{ label: '发货批次', key: '总数', tone: 'mint' }, { label: '待发货', key: '待处理', tone: 'gold' }, { label: '运输中', key: '运输中' }, { label: '已签收', key: '已完成' }],
  },
  payments: {
    key: 'payments', path: '/payments', title: '收款记录', eyebrow: 'CASH COLLECTION', description: '归集每一笔到账，关联订单并快速完成回款核销。', icon: 'Money', permission: 'delivery:payment:list', group: '交付与回款', singular: '收款记录', searchPlaceholder: '搜索收款编号或客户',
    filters: [field('customer', '客户', 'select', []), field('method', '收款方式', 'select', ['银行转账', '线上支付', '支票', '其他'].map((x) => ({ label: x, value: x }))), field('status', '核销状态', 'select', statusOptions)],
    fields: [field('receiptNo', '收款编号', 'text', undefined, true), field('customer', '客户', 'select', [], true), field('orderNo', '关联订单', 'select', []), field('amount', '到账金额', 'money', undefined, true), field('method', '收款方式', 'select', ['银行转账', '线上支付', '支票', '其他'].map((x) => ({ label: x, value: x })), true), field('account', '收款账户或渠道'), field('date', '到账日期', 'date', undefined, true), field('voucher', '付款凭证'), field('registrar', '登记人员'), field('status', '核销状态', 'select', statusOptions, true), field('note', '备注', 'textarea')],
    columns: [col('receiptNo', '收款编号'), col('customer', '客户'), col('orderNo', '关联订单'), col('amount', '到账金额', 'money'), col('method', '收款方式'), col('date', '到账日期', 'date'), col('status', '核销状态', 'status')],
    stats: [{ label: '本月到账', key: '本月金额', tone: 'mint', prefix: '¥' }, { label: '待核销', key: '待处理', tone: 'gold' }, { label: '收款笔数', key: '总数' }, { label: '涉及客户', key: '客户数' }],
  },
  receivables: {
    key: 'receivables', path: '/receivables', title: '应收账款', eyebrow: 'ACCOUNTS RECEIVABLE', description: '透明掌握应收余额与账龄风险，合理安排回款跟进。', icon: 'Wallet', permission: 'delivery:receivable:list', group: '交付与回款', singular: '应收账款', searchPlaceholder: '搜索应收编号或客户',
    filters: [field('status', '回款状态', 'select', ['未到期', '逾期', '部分回款', '已结清'].map((x) => ({ label: x, value: x }))), field('customer', '客户', 'select', [])],
    fields: [field('invoiceNo', '应收编号', 'text', undefined, true), field('customer', '客户', 'select', [], true), field('orderNo', '关联订单', 'select', [], true), field('amount', '应收金额', 'money', undefined, true), field('received', '已收金额', 'money'), field('outstanding', '未收金额', 'money'), field('dueDate', '到期日期', 'date', undefined, true), field('agingDays', '逾期天数', 'number'), field('status', '回款状态', 'select', ['未到期', '逾期', '部分回款', '已结清'].map((x) => ({ label: x, value: x })), true), field('owner', '跟进负责人', 'select', [])],
    columns: [col('invoiceNo', '应收编号'), col('customer', '客户'), col('orderNo', '订单编号'), col('amount', '应收金额', 'money'), col('received', '已收金额', 'money'), col('dueDate', '到期日期', 'date'), col('status', '回款状态', 'status')],
    stats: [{ label: '应收余额', key: '余额', tone: 'mint', prefix: '¥' }, { label: '本月到期', key: '本月到期', tone: 'gold' }, { label: '逾期账款', key: '逾期', tone: 'rose' }, { label: '回款率', key: '回款率', prefix: '%' }],
  },
  'service-tickets': {
    key: 'service-tickets', path: '/service-tickets', title: '售后工单', eyebrow: 'SERVICE DESK', description: '集中受理客户问题，分配处理人并追踪解决时效。', icon: 'Service', permission: 'service:ticket:list', group: '售后管理', singular: '售后工单', searchPlaceholder: '搜索工单编号或客户',
    filters: [field('status', '工单状态', 'select', aftersalesStatusOptions), field('priority', '优先级', 'select', ['紧急', '高', '普通'].map((x) => ({ label: x, value: x }))), field('assignee', '处理人', 'select', [])],
    fields: [field('ticketNo', '工单编号', 'text', undefined, true), field('customer', '客户', 'select', [], true), field('storeName', '问题门店'), field('contact', '联系人'), field('orderNo', '关联订单', 'select', []), field('product', '关联 SKU', 'select', []), field('type', '问题类型', 'select', ['产品破损', '产品质量问题', '使用效果不佳', '发错/少发/漏发', '退换货申请', '客户投诉', '使用方法不清楚'].map((x) => ({ label: x, value: x })), true), field('priority', '优先级', 'select', ['紧急', '高', '普通'].map((x) => ({ label: x, value: x })), true), field('assignee', '处理人', 'select', [], true), field('responsibility', '责任归属'), field('createdAt', '创建时间', 'date'), field('dueDate', '处理时限', 'date'), field('status', '工单状态', 'select', aftersalesStatusOptions, true), field('description', '问题描述', 'textarea', undefined, true), field('evidence', '图片/视频凭证'), field('resolution', '处理方案/记录', 'textarea'), field('satisfaction', '客户满意度', 'select', ['非常满意', '满意', '一般', '待改善'].map((x) => ({ label: x, value: x })))],
    columns: [col('ticketNo', '工单编号'), col('customer', '客户'), col('type', '问题类型'), col('priority', '优先级'), col('assignee', '处理人'), col('createdAt', '创建时间', 'date'), col('status', '工单状态', 'status')],
    stats: [{ label: '工单总数', key: '总数', tone: 'mint' }, { label: '待受理', key: '待处理', tone: 'gold' }, { label: '紧急工单', key: '紧急', tone: 'rose' }, { label: '已解决', key: '已完成' }],
  },
  visits: {
    key: 'visits', path: '/visits', title: '客户回访', eyebrow: 'CUSTOMER SUCCESS', description: '持续倾听客户反馈，记录满意度并推动服务改进。', icon: 'Phone', permission: 'service:visit:list', group: '售后管理', singular: '客户回访', searchPlaceholder: '搜索客户或回访内容',
    filters: [field('customer', '客户', 'select', []), field('method', '回访方式', 'select', ['电话', '上门', '视频会议', '问卷'].map((x) => ({ label: x, value: x }))), field('owner', '负责人', 'select', []), field('status', '状态', 'select', statusOptions)],
    fields: [field('customer', '客户', 'select', [], true), field('storeName', '回访门店'), field('orderNo', '关联订单', 'select', []), field('ticketNo', '关联售后工单'), field('date', '回访时间', 'date', undefined, true), field('method', '回访方式', 'select', ['电话', '上门', '视频会议', '问卷'].map((x) => ({ label: x, value: x })), true), field('satisfaction', '满意度', 'select', ['非常满意', '满意', '一般', '待改善'].map((x) => ({ label: x, value: x })), true), field('newNeed', '是否产生新需求', 'select', [{ label: '是', value: '是' }, { label: '否', value: '否' }]), field('owner', '回访人', 'select', [], true), field('status', '状态', 'select', statusOptions), field('result', '客户反馈/回访结论', 'textarea', undefined, true), field('nextAction', '后续处理计划', 'textarea'), field('nextDate', '下次回访时间', 'date')],
    columns: [col('customer', '客户'), col('date', '回访时间', 'date'), col('method', '方式'), col('satisfaction', '满意度'), col('owner', '回访人'), col('status', '状态', 'status'), col('result', '回访结论')],
    stats: [{ label: '本月回访', key: '本月', tone: 'mint' }, { label: '满意客户', key: '满意', tone: 'gold' }, { label: '待回访', key: '待处理', tone: 'rose' }, { label: '覆盖客户', key: '客户数' }],
  },
  'reports-sales': {
    key: 'reports-sales', path: '/reports/sales', title: '销售统计', eyebrow: 'SALES PERFORMANCE', description: '从签约金额、转化效率和团队贡献观察经营进展。', icon: 'DataAnalysis', permission: 'report:sales:list', group: '数据统计', singular: '销售记录', searchPlaceholder: '搜索月份', filters: [], fields: [],
    columns: [col('month', '月份'), col('amount', '签约金额', 'money'), col('orders', '订单数'), col('newCustomers', '新增客户'), col('conversion', '线索转化率', 'progress')],
    stats: [{ label: '年度签约', key: '年度签约', tone: 'mint', prefix: '¥' }, { label: '本月目标达成', key: '目标达成', tone: 'gold', prefix: '%' }, { label: '新增商机', key: '新增商机' }, { label: '平均客单价', key: '客单价' }], primaryAction: '导出报表',
  },
  'reports-customer-purchase': {
    key: 'reports-customer-purchase', path: '/reports/customer-purchase', title: '客户采购分析', eyebrow: 'CUSTOMER INSIGHTS', description: '识别高价值客户、采购趋势与复购机会。', icon: 'PieChart', permission: 'report:customer:list', group: '数据统计', singular: '客户分析', searchPlaceholder: '搜索客户', filters: [], fields: [],
    columns: [col('customer', '客户'), col('industry', '行业'), col('amount', '采购金额', 'money'), col('orders', '订单数'), col('growth', '同比增长', 'progress'), col('lastPurchase', '最近采购', 'date')],
    stats: [{ label: '分析客户', key: '客户数', tone: 'mint' }, { label: '采购总额', key: '采购总额', tone: 'gold', prefix: '¥' }, { label: '复购率', key: '复购率', prefix: '%' }, { label: '高价值客户', key: '重点客户', tone: 'rose' }], primaryAction: '导出分析',
  },
  'reports-product-sales': {
    key: 'reports-product-sales', path: '/reports/product-sales', title: '产品销售分析', eyebrow: 'PRODUCT PERFORMANCE', description: '洞察畅销品与产品结构，辅助定价和备货决策。', icon: 'Goods', permission: 'report:product:list', group: '数据统计', singular: '产品分析', searchPlaceholder: '搜索产品', filters: [], fields: [],
    columns: [col('product', '产品名称'), col('category', '产品分类'), col('quantity', '销售数量'), col('amount', '销售金额', 'money'), col('margin', '毛利率', 'progress'), col('growth', '环比增长', 'progress')],
    stats: [{ label: '销售产品', key: '产品数', tone: 'mint' }, { label: '产品销售额', key: '销售额', tone: 'gold', prefix: '¥' }, { label: '畅销品', key: '畅销品' }, { label: '平均毛利率', key: '毛利率', prefix: '%' }], primaryAction: '导出分析',
  },
}

export const moduleList = Object.values(moduleDefinitions)
export const moduleByPath = Object.fromEntries(moduleList.map((item) => [item.path, item]))
