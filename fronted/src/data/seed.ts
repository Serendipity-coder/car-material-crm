import type { CrmRecord } from '../api/types'

const rows: Record<string, CrmRecord[]> = {
  todos: [
    { id: 't1', title: '跟进安驰汽修年度机油采购', taskType: '销售跟进', relatedType: '客户', relatedId: 'c1', related: '安驰汽修连锁 · 机油与滤芯', dueDate: '2026-11-14', priority: '高', owner: '周子墨', status: '待处理', note: '确认本月保养车辆数量和到货时间。' },
    { id: 't2', title: '确认洁途洗车耗材补货', taskType: '补货提醒', relatedType: '客户', relatedId: 'c2', related: '洁途洗车中心 · 洗车液与毛巾', dueDate: '2026-11-15', priority: '中', owner: '陈嘉宁', status: '待处理', note: '' },
    { id: 't3', title: '回访迅达轮胎到货情况', taskType: '客户回访', relatedType: '订单', relatedId: 'so2', related: '迅达轮胎服务 · 轮胎与补胎工具', dueDate: '2026-11-16', priority: '中', owner: '许知夏', status: '待处理', note: '' },
    { id: 't4', title: '跟进鼎盛汽配账期回款', taskType: '回款提醒', relatedType: '应收账款', relatedId: 'ar2', related: '鼎盛汽配门店 · 账期核销', dueDate: '2026-11-13', priority: '高', owner: '林予安', status: '进行中', note: '客户承诺本周完成第二笔回款。' },
  ],
  leads: [
    { id: 'l1', createdAt: '2026-11-05', name: '安驰汽修机油采购', company: '安驰汽修连锁', source: '汽配城拜访', stage: '持续跟进', owner: '周子墨', score: 92, nextDate: '2026-11-14', status: '进行中', phone: '13800001120', storeName: '安驰汽修龙华店', region: '深圳龙华', purchaseCategories: ['润滑养护', '滤清器'], customerType: '汽修厂' },
    { id: 'l2', createdAt: '2026-10-28', name: '洁途洗车耗材补货', company: '洁途洗车中心', source: '客户转介绍', stage: '高意向', owner: '陈嘉宁', score: 88, nextDate: '2026-11-15', status: '进行中', phone: '13800001121', storeName: '洁途洗车南山店', region: '深圳南山', purchaseCategories: ['清洗美容'], customerType: '洗车店' },
    { id: 'l3', createdAt: '2026-11-08', name: '迅达轮胎工具采购', company: '迅达轮胎服务', source: '行业展会', stage: '首次联系', owner: '许知夏', score: 76, nextDate: '2026-11-17', status: '待处理', phone: '13800001122', storeName: '迅达轮胎宝安店', region: '深圳宝安', purchaseCategories: ['轮胎', '工具设备'], customerType: '轮胎店' },
    { id: 'l4', createdAt: '2026-10-15', name: '鑫源美容耗材合作', company: '鑫源汽车美容', source: '微信公众号', stage: '已分配', owner: '林予安', score: 71, nextDate: '2026-11-18', status: '进行中', phone: '13800001123', storeName: '鑫源汽车美容店', region: '广州番禺', purchaseCategories: ['清洗美容', '车身外观'], customerType: '汽车美容店' },
  ],
  pool: [
    { id: 'p1', name: '鼎盛汽配门店', industry: '汽车用品经销', level: 'A级｜重点客户', source: '汽配城活动', lastContact: '2026-10-29', status: '待认领', phone: '0755-66123001', storeName: '鼎盛汽配龙岗店', address: '深圳龙岗' },
    { id: 'p2', name: '华驰连锁维修中心', industry: '汽修服务', level: 'B级｜稳定客户', source: '市场活动', lastContact: '2026-10-25', status: '待认领', phone: '020-88002201', storeName: '华驰维修白云店', address: '广州白云' },
    { id: 'p3', name: '清风洗护连锁', industry: '洗车美容', level: 'C级｜普通客户', source: '官网咨询', lastContact: '2026-10-22', status: '待分配', phone: '020-87004100', storeName: '清风洗护天河店', address: '广州天河' },
  ],
  customers: [
    { id: 'c1', createdAt: '2026-09-12', name: '安驰汽修连锁', customerType: '汽修厂', industry: '汽修服务', level: 'A级｜重点客户', owner: '周子墨', annualValue: 1280000, purchaseCount: 8, lastContact: '2026-11-10', status: '正常合作', phone: '0755-60005210', email: 'purchase@anchi.example', source: '汽配城拜访', storeCount: 6, storeName: '安驰汽修龙华店', address: '深圳龙华', purchaseCategories: ['润滑养护', '滤清器'] },
    { id: 'c2', createdAt: '2026-10-08', name: '洁途洗车中心', customerType: '洗车店', industry: '洗车美容', level: 'B级｜稳定客户', owner: '陈嘉宁', annualValue: 862000, purchaseCount: 6, lastContact: '2026-11-08', status: '正常合作', phone: '020-88003020', email: 'ops@jietu.example', source: '客户转介绍', storeCount: 4, storeName: '洁途洗车南山店', address: '深圳南山', purchaseCategories: ['清洗美容'] },
    { id: 'c3', createdAt: '2026-11-02', name: '迅达轮胎服务', customerType: '轮胎店', industry: '汽修服务', level: 'A级｜重点客户', owner: '许知夏', annualValue: 734000, purchaseCount: 5, lastContact: '2026-11-06', status: '正常合作', phone: '0755-66109931', email: 'service@xunda.example', source: '行业展会', storeCount: 3, storeName: '迅达轮胎宝安店', address: '深圳宝安', purchaseCategories: ['轮胎', '工具设备'] },
    { id: 'c4', createdAt: '2026-08-20', name: '鑫源汽车美容', customerType: '汽车美容店', industry: '汽车用品经销', level: 'C级｜普通客户', owner: '林予安', annualValue: 286000, purchaseCount: 3, lastContact: '2026-10-30', status: '需要关注', phone: '020-88004530', email: 'admin@xinyuan.example', source: '微信公众号', storeCount: 2, storeName: '鑫源汽车美容店', address: '广州番禺', purchaseCategories: ['清洗美容', '车身外观'] },
    { id: 'c5', createdAt: '2026-11-06', name: '粤顺汽配服务站', customerType: '汽配店', industry: '汽车用品经销', level: 'B级｜稳定客户', owner: '周子墨', annualValue: 918000, purchaseCount: 7, lastContact: '2026-11-11', status: '正常合作', phone: '020-87881080', email: 'buy@yueshun.example', source: '汽配城活动', storeCount: 5, storeName: '粤顺汽配番禺店', address: '广州番禺', purchaseCategories: ['底盘制动', '电气照明'] },
  ],
  contacts: [
    { id: 'co1', createdAt: '2026-09-12', name: '梁思远', customer: '安驰汽修连锁', position: '采购负责人', role: '采购负责人', phone: '13900002101', email: 'siyuan@anchi.example', status: '进行中' },
    { id: 'co2', createdAt: '2026-10-08', name: '苏婉宁', customer: '洁途洗车中心', position: '门店经理', role: '店长', phone: '13900002102', email: 'wanning@jietu.example', status: '进行中' },
    { id: 'co3', createdAt: '2026-11-02', name: '陈启明', customer: '迅达轮胎服务', position: '技术负责人', role: '技术负责人', phone: '13900002103', email: 'qiming@xunda.example', status: '进行中' },
    { id: 'co4', createdAt: '2026-08-20', name: '唐婉清', customer: '鑫源汽车美容', position: '老板', role: '老板', phone: '13900002104', email: 'wanqing@xinyuan.example', status: '进行中' },
  ],
  followups: [
    { id: 'f1', customer: '安驰汽修连锁', contact: '梁思远', date: '2026-11-11', type: '需求确认', owner: '周子墨', outcome: '确认六家门店本月保养耗材需求。', nextDate: '2026-11-14', status: '进行中' },
    { id: 'f2', customer: '洁途洗车中心', contact: '苏婉宁', date: '2026-11-10', type: '补货需求沟通', owner: '陈嘉宁', outcome: '确认洗车液和擦车毛巾的补货周期。', nextDate: '2026-11-15', status: '待处理' },
    { id: 'f3', customer: '迅达轮胎服务', contact: '陈启明', date: '2026-11-08', type: '到店拜访', owner: '许知夏', outcome: '现场核对轮胎工具和补胎耗材清单。', nextDate: '2026-11-17', status: '进行中' },
  ],
  'product-categories': [
    { id: 'pc1', createdAt: '2026-01-05', name: '清洗美容', parent: '—', productCount: 8, sort: 1, status: '启用' },
    { id: 'pc2', createdAt: '2026-01-06', name: '润滑养护', parent: '—', productCount: 6, sort: 2, status: '启用' },
    { id: 'pc3', createdAt: '2026-11-02', name: '底盘制动', parent: '—', productCount: 5, sort: 3, status: '启用' },
    { id: 'pc4', createdAt: '2026-11-10', name: '滤清器', parent: '—', productCount: 4, sort: 4, status: '启用' },
    { id: 'pc5', createdAt: '2026-11-10', name: '电气照明', parent: '—', productCount: 3, sort: 5, status: '启用' },
    { id: 'pc6', createdAt: '2026-11-10', name: '工具设备', parent: '—', productCount: 4, sort: 6, status: '启用' },
  ],
  products: [
    { id: 'pr1', sku: 'OIL-5W30-4L', name: '全合成机油 5W-30 4L', category: '润滑养护', spec: 'SN Plus / 4L', unit: '桶', price: 238, stock: 186, reorderPoint: 60, repurchaseCycle: 45, status: '进行中' },
    { id: 'pr2', sku: 'OIL-GL5-1L', name: '齿轮油 GL-5 1L', category: '润滑养护', spec: 'GL-5 / 1L', unit: '瓶', price: 42, stock: 240, reorderPoint: 80, repurchaseCycle: 90, status: '进行中' },
    { id: 'pr3', sku: 'FILTER-OIL-01', name: '机油滤清器通用款', category: '滤清器', spec: '适配主流轿车', unit: '个', price: 28, stock: 420, reorderPoint: 100, repurchaseCycle: 45, status: '进行中' },
    { id: 'pr4', sku: 'FILTER-AIR-02', name: '空气滤清器标准款', category: '滤清器', spec: '纸质滤芯', unit: '个', price: 36, stock: 315, reorderPoint: 80, repurchaseCycle: 90, status: '进行中' },
    { id: 'pr5', sku: 'WASH-FOAM-20L', name: '高泡洗车液 20L', category: '清洗美容', spec: '浓缩型 / 20L', unit: '桶', price: 168, stock: 75, reorderPoint: 30, repurchaseCycle: 30, status: '进行中' },
    { id: 'pr6', sku: 'WAX-COAT-1L', name: '快速镀膜养护剂 1L', category: '清洗美容', spec: '喷涂型 / 1L', unit: '瓶', price: 86, stock: 42, reorderPoint: 20, repurchaseCycle: 60, status: '进行中' },
    { id: 'pr7', sku: 'TIRE-2055516', name: '舒适型轮胎 205/55 R16', category: '底盘制动', spec: '轿车胎 / 四季胎', unit: '条', price: 468, stock: 34, reorderPoint: 12, repurchaseCycle: 180, status: '进行中' },
    { id: 'pr8', sku: 'BRAKE-PAD-F01', name: '前轮刹车片套装', category: '底盘制动', spec: '低粉尘配方', unit: '套', price: 186, stock: 28, reorderPoint: 10, repurchaseCycle: 120, status: '进行中' },
    { id: 'pr9', sku: 'LED-H7-01', name: 'LED 近光灯 H7', category: '电气照明', spec: '6000K / 对装', unit: '对', price: 128, stock: 56, reorderPoint: 18, repurchaseCycle: 120, status: '进行中' },
    { id: 'pr10', sku: 'TOOL-TORQUE-01', name: '数显扭力扳手 20-200N·m', category: '工具设备', spec: '含校准证书', unit: '把', price: 688, stock: 9, reorderPoint: 3, repurchaseCycle: 365, status: '进行中' },
  ],
  'customer-prices': [
    { id: 'cp1', createdAt: '2026-01-01', customer: '安驰汽修连锁', product: '全合成机油 5W-30 4L', price: 218, minPrice: 205, minQuantity: 20, startDate: '2026-01-01', endDate: '2026-12-31', status: '进行中' },
    { id: 'cp2', createdAt: '2026-06-01', customer: '洁途洗车中心', product: '高泡洗车液 20L', price: 152, minPrice: 140, minQuantity: 10, startDate: '2026-06-01', endDate: '2027-05-31', status: '进行中' },
    { id: 'cp3', createdAt: '2026-07-01', customer: '迅达轮胎服务', product: '舒适型轮胎 205/55 R16', price: 438, minPrice: 420, minQuantity: 8, startDate: '2026-07-01', endDate: '2026-12-31', status: '进行中' },
  ],
  opportunities: [
    { id: 'o1', name: '安驰汽修连锁年度耗材采购', customer: '安驰汽修连锁', amount: 420000, stage: '议价', probability: 78, owner: '周子墨', closingDate: '2026-12-10', status: '进行中' },
    { id: 'o2', name: '洁途洗车连锁补货合作', customer: '洁途洗车中心', amount: 186000, stage: '产品推荐', probability: 52, owner: '陈嘉宁', closingDate: '2027-01-15', status: '进行中' },
    { id: 'o3', name: '迅达轮胎工具年度采购', customer: '迅达轮胎服务', amount: 198000, stage: '需求发现', probability: 35, owner: '许知夏', closingDate: '2027-02-01', status: '进行中' },
    { id: 'o4', name: '粤顺汽配门店区域供货', customer: '粤顺汽配服务站', amount: 356000, stage: '赢单', probability: 100, owner: '林予安', closingDate: '2026-10-28', status: '已完成' },
  ],
  quotes: [
    { id: 'q1', quoteNo: 'QT-2026-0118', customer: '安驰汽修连锁', opportunity: '安驰汽修连锁年度耗材采购', items: [{ product: '全合成机油 5W-30 4L', sku: 'OIL-5W30-4L', quantity: 1200, unitPrice: 218, discount: 100 }, { product: '机油滤清器通用款', sku: 'FILTER-OIL-01', quantity: 1200, unitPrice: 26, discount: 100 }], amount: 292800, owner: '周子墨', validUntil: '2026-11-30', status: '已通过' },
    { id: 'q2', quoteNo: 'QT-2026-0117', customer: '洁途洗车中心', opportunity: '洁途洗车连锁补货合作', items: [{ product: '高泡洗车液 20L', sku: 'WASH-FOAM-20L', quantity: 600, unitPrice: 152, discount: 100 }, { product: '快速镀膜养护剂 1L', sku: 'WAX-COAT-1L', quantity: 240, unitPrice: 82, discount: 95 }], amount: 110808, owner: '陈嘉宁', validUntil: '2026-12-15', status: '待处理' },
    { id: 'q3', quoteNo: 'QT-2026-0116', customer: '粤顺汽配服务站', opportunity: '粤顺汽配门店区域供货', items: [{ product: '前轮刹车片套装', sku: 'BRAKE-PAD-F01', quantity: 400, unitPrice: 178, discount: 100 }], amount: 71200, owner: '林予安', validUntil: '2026-11-20', status: '已接受' },
  ],
  orders: [
    { id: 'so1', orderNo: 'SO-2026-0926', customer: '粤顺汽配服务站', quoteNo: 'QT-2026-0116', opportunity: '粤顺汽配门店区域供货', items: [{ product: '前轮刹车片套装', sku: 'BRAKE-PAD-F01', quantity: 400, unitPrice: 178, discount: 100 }], amount: 71200, orderDate: '2026-10-28', deliveryStatus: '运输中', paymentStatus: '部分回款', status: '进行中', owner: '林予安' },
    { id: 'so2', orderNo: 'SO-2026-0918', customer: '迅达轮胎服务', items: [{ product: '舒适型轮胎 205/55 R16', sku: 'TIRE-2055516', quantity: 80, unitPrice: 438, discount: 100 }], amount: 35040, orderDate: '2026-10-21', deliveryStatus: '已签收', paymentStatus: '已结清', status: '已完成', owner: '许知夏' },
    { id: 'so3', orderNo: 'SO-2026-0902', customer: '安驰汽修连锁', items: [{ product: '全合成机油 5W-30 4L', sku: 'OIL-5W30-4L', quantity: 600, unitPrice: 218, discount: 100 }], amount: 130800, orderDate: '2026-10-02', deliveryStatus: '未发货', paymentStatus: '未回款', status: '待确认', owner: '周子墨' },
    { id: 'so4', orderNo: 'SO-2026-0870', customer: '洁途洗车中心', items: [{ product: '高泡洗车液 20L', sku: 'WASH-FOAM-20L', quantity: 240, unitPrice: 152, discount: 100 }], amount: 36480, orderDate: '2026-09-14', deliveryStatus: '已签收', paymentStatus: '部分回款', status: '已完成', owner: '陈嘉宁' },
  ],
  returns: [
    { id: 'rtn1', returnNo: 'RT-2026-0042', orderNo: 'SO-2026-0918', customer: '迅达轮胎服务', product: '舒适型轮胎 205/55 R16', quantity: 2, type: '换货', amount: 876, reason: '轮胎运输过程中胎侧划伤，申请换货。', createdAt: '2026-11-10', status: '处理中' },
    { id: 'rtn2', returnNo: 'RT-2026-0039', orderNo: 'SO-2026-0870', customer: '洁途洗车中心', product: '高泡洗车液 20L', quantity: 4, type: '退货', amount: 608, reason: '门店活动调整，未开封耗材退回。', createdAt: '2026-11-06', status: '待审核' },
  ],
  shipments: [
    { id: 'sh1', shipmentNo: 'SH-2026-0229', orderNo: 'SO-2026-0918', customer: '迅达轮胎服务', carrier: '顺丰速运', trackingNo: 'SF1300285102', shipmentDate: '2026-10-23', status: '已签收', signStatus: '已签收', signDate: '2026-10-25' },
    { id: 'sh2', shipmentNo: 'SH-2026-0234', orderNo: 'SO-2026-0926', customer: '粤顺汽配服务站', carrier: '京东物流', trackingNo: 'JDVA01983478', shipmentDate: '2026-11-10', status: '运输中' },
    { id: 'sh3', shipmentNo: 'SH-2026-0238', orderNo: 'SO-2026-0902', customer: '安驰汽修连锁', carrier: '德邦物流', trackingNo: 'DBL2026111201', shipmentDate: '2026-11-12', status: '未发货' },
  ],
  payments: [
    { id: 'pay1', receiptNo: 'RC-2026-0783', customer: '粤顺汽配服务站', orderNo: 'SO-2026-0926', amount: 36000, method: '银行转账', date: '2026-11-08', status: '已完成', account: '招商银行对公账户' },
    { id: 'pay2', receiptNo: 'RC-2026-0766', customer: '迅达轮胎服务', orderNo: 'SO-2026-0918', amount: 35040, method: '线上支付', date: '2026-11-02', status: '已完成', account: '微信商户号' },
    { id: 'pay3', receiptNo: 'RC-2026-0741', customer: '洁途洗车中心', orderNo: 'SO-2026-0870', amount: 18000, method: '银行转账', date: '2026-10-29', status: '待处理', account: '工商银行对公账户' },
  ],
  receivables: [
    { id: 'ar1', invoiceNo: 'AR-2026-1028', customer: '粤顺汽配服务站', orderNo: 'SO-2026-0926', amount: 71200, received: 36000, outstanding: 35200, dueDate: '2026-11-28', status: '部分回款', owner: '林予安' },
    { id: 'ar2', invoiceNo: 'AR-2026-1012', customer: '安驰汽修连锁', orderNo: 'SO-2026-0902', amount: 130800, received: 0, outstanding: 130800, dueDate: '2026-11-20', status: '未到期', owner: '周子墨' },
    { id: 'ar3', invoiceNo: 'AR-2026-0920', customer: '洁途洗车中心', orderNo: 'SO-2026-0870', amount: 36480, received: 18000, outstanding: 18480, dueDate: '2026-11-01', status: '逾期', owner: '陈嘉宁' },
  ],
  'service-tickets': [
    { id: 'st1', ticketNo: 'CS-2026-0418', customer: '安驰汽修连锁', type: '产品质量问题', priority: '高', assignee: '许知夏', createdAt: '2026-11-11', status: '处理中', description: '部分机油滤清器包装破损，需要核实批次。', product: '机油滤清器通用款' },
    { id: 'st2', ticketNo: 'CS-2026-0417', customer: '迅达轮胎服务', type: '产品破损', priority: '紧急', assignee: '陈嘉宁', createdAt: '2026-11-10', status: '待处理', description: '轮胎到货后发现胎侧划伤。', product: '舒适型轮胎 205/55 R16' },
    { id: 'st3', ticketNo: 'CS-2026-0412', customer: '洁途洗车中心', type: '使用效果不佳', priority: '普通', assignee: '周子墨', createdAt: '2026-11-07', status: '已解决', description: '客户反馈泡沫浓度不足，已提供稀释比例指导。', product: '高泡洗车液 20L' },
  ],
  visits: [
    { id: 'v1', customer: '粤顺汽配服务站', date: '2026-11-11', method: '电话', satisfaction: '非常满意', owner: '林予安', result: '确认刹车片首批到货和门店陈列安排。', status: '已完成' },
    { id: 'v2', customer: '安驰汽修连锁', date: '2026-11-09', method: '上门', satisfaction: '满意', owner: '周子墨', result: '完成六家门店耗材盘点，确认下次采购周期。', status: '已完成' },
    { id: 'v3', customer: '洁途洗车中心', date: '2026-11-14', method: '电话', satisfaction: '一般', owner: '陈嘉宁', result: '待确认洗车液补货数量和到货时间。', status: '待处理' },
  ],
  'reports-sales': [
    { id: 'rs1', month: '2026年1月', amount: 268000, targetAmount: 360000, orders: 6, newCustomers: 3, conversion: 18 },
    { id: 'rs2', month: '2026年2月', amount: 294000, targetAmount: 360000, orders: 7, newCustomers: 4, conversion: 19 },
    { id: 'rs3', month: '2026年3月', amount: 325000, targetAmount: 380000, orders: 7, newCustomers: 4, conversion: 20 },
    { id: 'rs4', month: '2026年4月', amount: 341000, targetAmount: 400000, orders: 8, newCustomers: 5, conversion: 20 },
    { id: 'rs5', month: '2026年5月', amount: 365000, targetAmount: 420000, orders: 8, newCustomers: 5, conversion: 21 },
    { id: 'rs6', month: '2026年6月', amount: 382000, targetAmount: 440000, orders: 8, newCustomers: 5, conversion: 21 },
    { id: 'rs7', month: '2026年7月', amount: 416000, targetAmount: 450000, orders: 10, newCustomers: 7, conversion: 24 },
    { id: 'rs8', month: '2026年8月', amount: 398000, targetAmount: 460000, orders: 9, newCustomers: 6, conversion: 22 },
    { id: 'rs9', month: '2026年9月', amount: 472000, targetAmount: 500000, orders: 11, newCustomers: 8, conversion: 26 },
    { id: 'rs10', month: '2026年10月', amount: 521000, targetAmount: 540000, orders: 13, newCustomers: 9, conversion: 29 },
    { id: 'rs11', month: '2026年11月', amount: 586000, targetAmount: 600000, orders: 15, newCustomers: 11, conversion: 32 },
  ],
  'reports-customer-purchase': [
    { id: 'rc1', customer: '安驰汽修连锁', industry: '汽修服务', amount: 1280000, orders: 8, growth: 18, lastPurchase: '2026-11-10' },
    { id: 'rc2', customer: '洁途洗车中心', industry: '洗车美容', amount: 862000, orders: 6, growth: 12, lastPurchase: '2026-11-08' },
    { id: 'rc3', customer: '迅达轮胎服务', industry: '汽修服务', amount: 734000, orders: 5, growth: 9, lastPurchase: '2026-11-06' },
    { id: 'rc4', customer: '鑫源汽车美容', industry: '汽车用品经销', amount: 286000, orders: 3, growth: -3, lastPurchase: '2026-10-30' },
    { id: 'rc5', customer: '粤顺汽配服务站', industry: '汽车用品经销', amount: 918000, orders: 7, growth: 24, lastPurchase: '2026-11-11' },
  ],
  'reports-product-sales': [
    { id: 'rp1', month: '2026-11', product: '全合成机油 5W-30 4L', category: '润滑养护', quantity: 640, amount: 139520, margin: 28, growth: 22 },
    { id: 'rp2', month: '2026-11', product: '高泡洗车液 20L', category: '清洗美容', quantity: 380, amount: 57760, margin: 31, growth: 14 },
    { id: 'rp3', month: '2026-10', product: '舒适型轮胎 205/55 R16', category: '底盘制动', quantity: 160, amount: 70080, margin: 18, growth: 17 },
    { id: 'rp4', month: '2026-10', product: '机油滤清器通用款', category: '滤清器', quantity: 720, amount: 18720, margin: 34, growth: 12 },
  ],
}

const supplementalRows: Record<string, CrmRecord[]> = {
  todos: [
    { id: 't5', title: '核对华驰维修刹车片清单', taskType: '销售跟进', relatedType: '客户', relatedId: 'c6', related: '华驰连锁维修中心 · 底盘制动', dueDate: '2026-11-17', priority: '高', owner: '周子墨', status: '待处理', note: '' },
    { id: 't6', title: '处理迅达轮胎换货工单', taskType: '售后处理', relatedType: '售后工单', relatedId: 'st2', related: '迅达轮胎服务 · 轮胎运输破损', dueDate: '2026-11-18', priority: '高', owner: '陈嘉宁', status: '待处理', note: '' },
  ],
  leads: [
    { id: 'l5', createdAt: '2026-11-09', name: '华驰维修刹车片采购', company: '华驰连锁维修中心', source: '汽配城拜访', stage: '首次联系', owner: '周子墨', score: 84, nextDate: '2026-11-19', status: '进行中', phone: '13800001124', storeName: '华驰维修白云店', region: '广州白云', purchaseCategories: ['底盘制动'], customerType: '汽修厂' },
    { id: 'l6', createdAt: '2026-11-10', name: '清风洗护连锁供货', company: '清风洗护连锁', source: '官网咨询', stage: '已分配', owner: '陈嘉宁', score: 79, nextDate: '2026-11-20', status: '进行中', phone: '13800001125', storeName: '清风洗护天河店', region: '广州天河', purchaseCategories: ['清洗美容'], customerType: '洗车店' },
  ],
  pool: [
    { id: 'p4', name: '华驰连锁维修中心', industry: '汽修服务', level: 'A级｜重点客户', source: '行业展会', lastContact: '2026-11-03', status: '待认领', phone: '020-86003104', storeName: '华驰维修白云店', address: '广州白云' },
    { id: 'p5', name: '清风洗护连锁', industry: '洗车美容', level: 'B级｜稳定客户', source: '客户转介绍', lastContact: '2026-10-31', status: '待认领', phone: '020-86004105', storeName: '清风洗护天河店', address: '广州天河' },
  ],
  customers: [
    { id: 'c6', createdAt: '2026-09-15', name: '华驰连锁维修中心', customerType: '汽修厂', industry: '汽修服务', level: 'A级｜重点客户', owner: '周子墨', annualValue: 1220000, purchaseCount: 8, lastContact: '2026-11-10', status: '正常合作', phone: '020-86005260', email: 'purchase@huachi.example', source: '行业展会', storeCount: 4, storeName: '华驰维修白云店', address: '广州白云', purchaseCategories: ['底盘制动', '电气照明'] },
    { id: 'c7', createdAt: '2026-10-20', name: '清风洗护连锁', customerType: '洗车店', industry: '洗车美容', level: 'B级｜稳定客户', owner: '陈嘉宁', annualValue: 680000, purchaseCount: 5, lastContact: '2026-11-09', status: '正常合作', phone: '020-80005310', email: 'ops@qingfeng.example', source: '客户转介绍', storeCount: 3, storeName: '清风洗护天河店', address: '广州天河', purchaseCategories: ['清洗美容'] },
  ],
  contacts: [
    { id: 'co5', createdAt: '2026-09-15', name: '韩东辰', customer: '华驰连锁维修中心', position: '采购负责人', role: '采购负责人', phone: '13900002105', email: 'dongchen@huachi.example', status: '进行中' },
    { id: 'co6', createdAt: '2026-10-20', name: '秦思远', customer: '清风洗护连锁', position: '店长', role: '店长', phone: '13900002106', email: 'siyuan@qingfeng.example', status: '进行中' },
  ],
  followups: [
    { id: 'f4', customer: '华驰连锁维修中心', contact: '韩东辰', date: '2026-11-10', type: '报价沟通', owner: '周子墨', outcome: '客户重点比较刹车片耐用性和批量价格。', nextDate: '2026-11-19', status: '进行中' },
    { id: 'f5', customer: '清风洗护连锁', contact: '秦思远', date: '2026-11-09', type: '补货需求沟通', owner: '陈嘉宁', outcome: '确认三家门店月均洗车量和耗材消耗。', nextDate: '2026-11-20', status: '待处理' },
  ],
  'product-categories': [
    { id: 'pc7', createdAt: '2026-11-11', name: '车身外观', parent: '—', productCount: 3, sort: 7, status: '启用' },
    { id: 'pc8', createdAt: '2026-11-12', name: '轮胎', parent: '底盘制动', productCount: 2, sort: 1, status: '启用' },
  ],
  products: [
    { id: 'pr11', sku: 'WIPER-24-01', name: '无骨雨刷 24 英寸', category: '车身外观', spec: '通用接口 / 24英寸', unit: '支', price: 48, stock: 126, reorderPoint: 30, repurchaseCycle: 120, status: '进行中' },
    { id: 'pr12', sku: 'POLISH-500', name: '车身镜面抛光剂 500ml', category: '车身外观', spec: '研磨型 / 500ml', unit: '瓶', price: 58, stock: 68, reorderPoint: 20, repurchaseCycle: 90, status: '进行中' },
    { id: 'pr13', sku: 'TIRE-2156017', name: '舒适型轮胎 215/60 R17', category: '轮胎', spec: 'SUV胎 / 四季胎', unit: '条', price: 598, stock: 22, reorderPoint: 8, repurchaseCycle: 180, status: '进行中' },
    { id: 'pr14', sku: 'BRAKE-FLUID-DOT4', name: 'DOT4 制动液 1L', category: '底盘制动', spec: 'DOT4 / 1L', unit: '瓶', price: 46, stock: 154, reorderPoint: 40, repurchaseCycle: 180, status: '进行中' },
  ],
  'customer-prices': [
    { id: 'cp4', createdAt: '2026-08-01', customer: '鑫源汽车美容', product: '车身镜面抛光剂 500ml', price: 52, minPrice: 48, minQuantity: 20, startDate: '2026-08-01', endDate: '2027-07-31', status: '进行中' },
    { id: 'cp5', createdAt: '2026-09-01', customer: '华驰连锁维修中心', product: '前轮刹车片套装', price: 172, minPrice: 160, minQuantity: 30, startDate: '2026-09-01', endDate: '2027-08-31', status: '进行中' },
    { id: 'cp6', createdAt: '2026-10-15', customer: '清风洗护连锁', product: '无骨雨刷 24 英寸', price: 43, minPrice: 38, minQuantity: 20, startDate: '2026-10-15', endDate: '2027-10-14', status: '进行中' },
  ],
  opportunities: [
    { id: 'o5', name: '华驰维修刹车片年度供货', customer: '华驰连锁维修中心', amount: 268000, stage: '报价', probability: 64, owner: '周子墨', closingDate: '2026-12-20', status: '进行中' },
    { id: 'o6', name: '清风洗护门店耗材扩容', customer: '清风洗护连锁', amount: 156000, stage: '试用/样品', probability: 46, owner: '陈嘉宁', closingDate: '2027-01-08', status: '进行中' },
    { id: 'o7', name: '鑫源汽车美容抛光产品合作', customer: '鑫源汽车美容', amount: 92000, stage: '待确认', probability: 82, owner: '林予安', closingDate: '2026-12-05', status: '进行中' },
  ],
  quotes: [
    { id: 'q4', quoteNo: 'QT-2026-0121', customer: '华驰连锁维修中心', opportunity: '华驰维修刹车片年度供货', items: [{ product: '前轮刹车片套装', sku: 'BRAKE-PAD-F01', quantity: 900, unitPrice: 172, discount: 100 }, { product: 'DOT4 制动液 1L', sku: 'BRAKE-FLUID-DOT4', quantity: 300, unitPrice: 44, discount: 100 }], amount: 168000, owner: '周子墨', validUntil: '2026-12-20', status: '已提交' },
    { id: 'q5', quoteNo: 'QT-2026-0120', customer: '清风洗护连锁', opportunity: '清风洗护门店耗材扩容', items: [{ product: '高泡洗车液 20L', sku: 'WASH-FOAM-20L', quantity: 480, unitPrice: 150, discount: 100 }, { product: '无骨雨刷 24 英寸', sku: 'WIPER-24-01', quantity: 240, unitPrice: 43, discount: 100 }], amount: 82320, owner: '陈嘉宁', validUntil: '2026-12-10', status: '客户查看' },
    { id: 'q6', quoteNo: 'QT-2026-0119', customer: '鑫源汽车美容', opportunity: '鑫源汽车美容抛光产品合作', items: [{ product: '车身镜面抛光剂 500ml', sku: 'POLISH-500', quantity: 360, unitPrice: 52, discount: 100 }], amount: 18720, owner: '林予安', validUntil: '2026-12-05', status: '议价中' },
  ],
  orders: [
    { id: 'so5', orderNo: 'SO-2026-1018', customer: '华驰连锁维修中心', quoteNo: 'QT-2026-0121', opportunity: '华驰维修刹车片年度供货', items: [{ product: '前轮刹车片套装', sku: 'BRAKE-PAD-F01', quantity: 500, unitPrice: 172, discount: 100 }], amount: 86000, orderDate: '2026-11-01', deliveryStatus: '部分发货', paymentStatus: '未回款', status: '已确认', owner: '周子墨' },
    { id: 'so6', orderNo: 'SO-2026-1022', customer: '清风洗护连锁', quoteNo: 'QT-2026-0120', opportunity: '清风洗护门店耗材扩容', items: [{ product: '高泡洗车液 20L', sku: 'WASH-FOAM-20L', quantity: 300, unitPrice: 150, discount: 100 }], amount: 45000, orderDate: '2026-11-04', deliveryStatus: '运输中', paymentStatus: '部分回款', status: '进行中', owner: '陈嘉宁' },
    { id: 'so7', orderNo: 'SO-2026-1025', customer: '鑫源汽车美容', items: [{ product: '车身镜面抛光剂 500ml', sku: 'POLISH-500', quantity: 180, unitPrice: 52, discount: 100 }], amount: 9360, orderDate: '2026-11-06', deliveryStatus: '已签收', paymentStatus: '已结清', status: '已完成', owner: '林予安' },
  ],
  returns: [
    { id: 'rtn3', returnNo: 'RT-2026-0044', orderNo: 'SO-2026-1018', customer: '华驰连锁维修中心', product: '前轮刹车片套装', quantity: 10, type: '换货', amount: 1720, reason: '部分包装挤压，申请更换。', createdAt: '2026-11-12', status: '待审核' },
    { id: 'rtn4', returnNo: 'RT-2026-0045', orderNo: 'SO-2026-1022', customer: '清风洗护连锁', product: '高泡洗车液 20L', quantity: 6, type: '退货', amount: 900, reason: '活动取消，未开封库存退回。', createdAt: '2026-11-13', status: '待寄回' },
  ],
  shipments: [
    { id: 'sh4', shipmentNo: 'SH-2026-0241', orderNo: 'SO-2026-1018', customer: '华驰连锁维修中心', carrier: '德邦物流', trackingNo: 'DBL2026111003', shipmentDate: '2026-11-10', status: '部分发货' },
    { id: 'sh5', shipmentNo: 'SH-2026-0245', orderNo: 'SO-2026-1022', customer: '清风洗护连锁', carrier: '京东物流', trackingNo: 'JDVA01983602', shipmentDate: '2026-11-08', status: '运输中' },
    { id: 'sh6', shipmentNo: 'SH-2026-0248', orderNo: 'SO-2026-1025', customer: '鑫源汽车美容', carrier: '顺丰速运', trackingNo: 'SF1300291144', shipmentDate: '2026-11-08', status: '已签收', signStatus: '已签收', signDate: '2026-11-10' },
  ],
  payments: [
    { id: 'pay4', receiptNo: 'RC-2026-0796', customer: '华驰连锁维修中心', orderNo: 'SO-2026-1018', amount: 30000, method: '银行转账', date: '2026-11-11', status: '待处理', account: '建设银行对公账户' },
    { id: 'pay5', receiptNo: 'RC-2026-0797', customer: '清风洗护连锁', orderNo: 'SO-2026-1022', amount: 22000, method: '线上支付', date: '2026-11-12', status: '已完成', account: '支付宝商户号' },
    { id: 'pay6', receiptNo: 'RC-2026-0798', customer: '鑫源汽车美容', orderNo: 'SO-2026-1025', amount: 9360, method: '银行转账', date: '2026-11-10', status: '已完成', account: '工商银行对公账户' },
  ],
  receivables: [
    { id: 'ar4', invoiceNo: 'AR-2026-1018', customer: '华驰连锁维修中心', orderNo: 'SO-2026-1018', amount: 86000, received: 30000, outstanding: 56000, dueDate: '2026-12-01', status: '部分回款', owner: '周子墨' },
    { id: 'ar5', invoiceNo: 'AR-2026-1022', customer: '清风洗护连锁', orderNo: 'SO-2026-1022', amount: 45000, received: 22000, outstanding: 23000, dueDate: '2026-11-25', status: '部分回款', owner: '陈嘉宁' },
    { id: 'ar6', invoiceNo: 'AR-2026-1025', customer: '鑫源汽车美容', orderNo: 'SO-2026-1025', amount: 9360, received: 9360, outstanding: 0, dueDate: '2026-11-20', status: '已结清', owner: '林予安' },
  ],
  'service-tickets': [
    { id: 'st4', ticketNo: 'CS-2026-0421', customer: '华驰连锁维修中心', type: '产品破损', priority: '高', assignee: '许知夏', createdAt: '2026-11-12', status: '待处理', description: '刹车片外包装挤压，申请换货核验。', product: '前轮刹车片套装' },
    { id: 'st5', ticketNo: 'CS-2026-0422', customer: '清风洗护连锁', type: '使用效果不佳', priority: '普通', assignee: '陈嘉宁', createdAt: '2026-11-13', status: '处理中', description: '洗车液泡沫稳定性需要进一步指导。', product: '高泡洗车液 20L' },
  ],
  visits: [
    { id: 'v4', customer: '华驰连锁维修中心', date: '2026-11-12', method: '上门', satisfaction: '满意', owner: '周子墨', result: '确认刹车片首批验收和后续换货流程。', nextAction: '跟进换货单审核。', status: '进行中' },
    { id: 'v5', customer: '清风洗护连锁', date: '2026-11-13', method: '电话', satisfaction: '一般', owner: '陈嘉宁', result: '客户反馈洗车液使用效果，已安排配比指导。', nextAction: '下周回访使用效果。', status: '待处理' },
  ],
  'reports-sales': [
    { id: 'rs12', month: '2026年12月', amount: 612000, targetAmount: 640000, orders: 16, newCustomers: 12, conversion: 34 },
    { id: 'rs13', month: '2027年1月', amount: 638000, targetAmount: 680000, orders: 17, newCustomers: 13, conversion: 35 },
  ],
  'reports-customer-purchase': [
    { id: 'rc6', customer: '华驰连锁维修中心', industry: '汽修服务', amount: 1220000, orders: 8, growth: 16, lastPurchase: '2026-11-10' },
    { id: 'rc7', customer: '清风洗护连锁', industry: '洗车美容', amount: 680000, orders: 5, growth: 18, lastPurchase: '2026-11-09' },
  ],
  'reports-product-sales': [
    { id: 'rp5', month: '2026-11', product: '前轮刹车片套装', category: '底盘制动', quantity: 400, amount: 71200, margin: 22, growth: 26 },
    { id: 'rp6', month: '2026-11', product: '快速镀膜养护剂 1L', category: '清洗美容', quantity: 240, amount: 19680, margin: 36, growth: 18 },
    { id: 'rp7', month: '2026-11', product: 'DOT4 制动液 1L', category: '底盘制动', quantity: 300, amount: 13200, margin: 25, growth: 15 },
    { id: 'rp8', month: '2026-10', product: '无骨雨刷 24 英寸', category: '车身外观', quantity: 280, amount: 12040, margin: 32, growth: 20 },
  ],
}

const departmentByKey: Record<string, string[]> = {
  leads: ['d3', 'd4'], pool: ['d3', 'd4'], customers: ['d3', 'd4', 'd5'], contacts: ['d3', 'd4', 'd5'], followups: ['d3', 'd4', 'd5'],
  products: ['d6'], 'product-categories': ['d6'], 'customer-prices': ['d3', 'd4'], opportunities: ['d3', 'd4', 'd5'], quotes: ['d3', 'd4', 'd5'], orders: ['d3', 'd4', 'd5'], returns: ['d5'], shipments: ['d6'], payments: ['d11'], receivables: ['d11'], 'service-tickets': ['d5'], visits: ['d5'], 'reports-customer-purchase': ['d3', 'd4', 'd5'], 'reports-product-sales': ['d6'],
}
const salesDepartmentShares = [{ id: 'd2', name: '销售中心', share: 0.25 }, { id: 'd3', name: '华东销售部', share: 0.42 }, { id: 'd4', name: '华南销售部', share: 0.33 }]

function mapSeedDepartmentIds(key: string, source: CrmRecord[]): CrmRecord[] {
  const records = structuredClone(source)
  if (key === 'reports-sales') return records.flatMap((record) => salesDepartmentShares.map((department) => ({ ...record, id: `${record.id}-${department.id}`, departmentId: department.id, departmentName: department.name, amount: Math.round(Number(record.amount ?? 0) * department.share), targetAmount: Math.round(Number(record.targetAmount ?? 0) * department.share), orders: Math.round(Number(record.orders ?? 0) * department.share), newCustomers: Math.round(Number(record.newCustomers ?? 0) * department.share) })))
  const departments = departmentByKey[key]
  if (!departments) return records
  return records.map((record, index) => ({ ...record, departmentId: record.departmentId ?? departments[index % departments.length] }))
}

export function getSeedData(key: string): CrmRecord[] { return mapSeedDepartmentIds(key, rows[key] ?? []) }
export function getSupplementalSeedData(key: string): CrmRecord[] { return mapSeedDepartmentIds(key, supplementalRows[key] ?? []) }
