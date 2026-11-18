export interface NavEntry {
  title: string
  path: string
  icon: string
  permission: string
}

export interface NavGroup {
  title: string
  items: NavEntry[]
}

export const navGroups: NavGroup[] = [
  {
    title: '工作台',
    items: [
      { title: '经营概况', path: '/', icon: 'DataBoard', permission: 'work:overview:list' },
      { title: '我的待办', path: '/todos', icon: 'Checked', permission: 'work:todo:list' },
    ],
  },
  {
    title: '客户管理',
    items: [
      { title: '销售线索', path: '/leads', icon: 'Aim', permission: 'crm:lead:list' },
      { title: '客户公海', path: '/pool', icon: 'Connection', permission: 'crm:pool:list' },
      { title: '客户档案', path: '/customers', icon: 'User', permission: 'crm:customer:list' },
      { title: '联系人', path: '/contacts', icon: 'Avatar', permission: 'crm:contact:list' },
      { title: '跟进记录', path: '/followups', icon: 'ChatDotRound', permission: 'crm:followup:list' },
    ],
  },
  {
    title: '产品管理',
    items: [
      { title: '产品分类', path: '/product-categories', icon: 'Grid', permission: 'product:category:list' },
      { title: '产品档案', path: '/products', icon: 'Goods', permission: 'product:product:list' },
      { title: '客户价格', path: '/customer-prices', icon: 'PriceTag', permission: 'product:price:list' },
    ],
  },
  {
    title: '销售管理',
    items: [
      { title: '销售商机', path: '/opportunities', icon: 'TrendCharts', permission: 'crm:opportunity:list' },
      { title: '报价单', path: '/quotes', icon: 'Tickets', permission: 'sales:quote:list' },
      { title: '销售订单', path: '/orders', icon: 'ShoppingCart', permission: 'sales:order:list' },
    ],
  },
  {
    title: '交付与回款',
    items: [
      { title: '发货记录', path: '/shipments', icon: 'Van', permission: 'delivery:shipment:list' },
      { title: '收款记录', path: '/payments', icon: 'Money', permission: 'delivery:payment:list' },
      { title: '应收账款', path: '/receivables', icon: 'Wallet', permission: 'delivery:receivable:list' },
    ],
  },
  {
    title: '售后管理',
    items: [
      { title: '售后工单', path: '/service-tickets', icon: 'Service', permission: 'service:ticket:list' },
      { title: '退换货单', path: '/returns', icon: 'RefreshLeft', permission: 'sales:return:list' },
      { title: '客户回访', path: '/visits', icon: 'Phone', permission: 'service:visit:list' },
    ],
  },
  {
    title: '数据统计',
    items: [
      { title: '销售统计', path: '/reports/sales', icon: 'DataAnalysis', permission: 'report:sales:list' },
      { title: '客户采购分析', path: '/reports/customer-purchase', icon: 'PieChart', permission: 'report:customer:list' },
      { title: '产品销售分析', path: '/reports/product-sales', icon: 'Histogram', permission: 'report:product:list' },
    ],
  },
  {
    title: '系统设置',
    items: [
      { title: '员工管理', path: '/employees', icon: 'Avatar', permission: 'system:user:list' },
      { title: '部门管理', path: '/departments', icon: 'OfficeBuilding', permission: 'system:dept:list' },
      { title: '角色管理', path: '/roles', icon: 'Key', permission: 'system:role:list' },
      { title: '操作日志', path: '/audit-logs', icon: 'List', permission: 'system:audit:list' },
    ],
  },
]

export const allNavEntries = navGroups.flatMap((group) => group.items)
