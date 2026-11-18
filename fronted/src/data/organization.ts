export interface Employee {
  id: string
  name: string
  account: string
  departmentId: string
  position: string
  roleIds: string[]
  phone: string
  email: string
  status: '在职' | '停用'
  hireDate: string
  gender: string
  remark: string
  lastLoginIp: string
}

export interface Department {
  id: string
  parentId: string | null
  name: string
  code: string
  leaderId: string
  sort: number
  phone: string
  email: string
  status: '启用' | '停用'
}

export interface Role {
  id: string
  name: string
  key: string
  sort: number
  status: '启用' | '停用'
  memberCount: number
  menuIds: string[]
  remark: string
  builtin?: boolean
}

export interface PermissionNode {
  id: string
  name: string
  type: 'M' | 'C' | 'F'
  permission: string
  children?: PermissionNode[]
}

export const departmentsSeed: Department[] = [
  { id: 'd1', parentId: null, name: '总公司', code: 'HQ', leaderId: 'e1', sort: 1, phone: '010-8000 2000', email: 'office@crm.demo', status: '启用' },
  { id: 'd2', parentId: 'd1', name: '销售中心', code: 'SALES', leaderId: 'e2', sort: 1, phone: '010-8000 2100', email: 'sales@crm.demo', status: '启用' },
  { id: 'd3', parentId: 'd2', name: '华东销售部', code: 'EAST', leaderId: 'e3', sort: 1, phone: '021-6000 1200', email: 'east@crm.demo', status: '启用' },
  { id: 'd4', parentId: 'd2', name: '华南销售部', code: 'SOUTH', leaderId: 'e4', sort: 2, phone: '020-8000 1300', email: 'south@crm.demo', status: '启用' },
  { id: 'd5', parentId: 'd1', name: '市场与客户成功', code: 'CS', leaderId: 'e5', sort: 2, phone: '010-8000 2200', email: 'success@crm.demo', status: '启用' },
  { id: 'd6', parentId: 'd1', name: '产品与交付', code: 'DELIVERY', leaderId: 'e6', sort: 3, phone: '010-8000 2300', email: 'delivery@crm.demo', status: '启用' },
  { id: 'd7', parentId: 'd2', name: '华北销售部', code: 'NORTH', leaderId: 'e9', sort: 3, phone: '010-6000 1400', email: 'north@crm.demo', status: '启用' },
  { id: 'd8', parentId: 'd2', name: '西南销售部', code: 'SOUTHWEST', leaderId: 'e11', sort: 4, phone: '028-8000 1500', email: 'southwest@crm.demo', status: '启用' },
  { id: 'd9', parentId: 'd1', name: '采购与供应链', code: 'PURCHASE', leaderId: 'e13', sort: 4, phone: '010-8000 2400', email: 'purchase@crm.demo', status: '启用' },
  { id: 'd10', parentId: 'd1', name: '仓储物流部', code: 'WAREHOUSE', leaderId: 'e15', sort: 5, phone: '010-8000 2500', email: 'warehouse@crm.demo', status: '启用' },
  { id: 'd11', parentId: 'd1', name: '财务部', code: 'FINANCE', leaderId: 'e17', sort: 6, phone: '010-8000 2600', email: 'finance@crm.demo', status: '启用' },
]

export const rolesSeed: Role[] = [
  { id: 'r1', name: '超级管理员', key: 'admin', sort: 1, status: '启用', memberCount: 1, menuIds: ['all'], remark: '内置系统角色，不可停用或删除。', builtin: true },
  { id: 'r2', name: '销售专员', key: 'sales', sort: 2, status: '启用', memberCount: 7, menuIds: ['overview', 'todo', 'todo-add', 'todo-edit', 'leads', 'lead-add', 'lead-edit', 'pool', 'pool-claim', 'customers', 'customer-add', 'customer-edit', 'contacts', 'contact-add', 'contact-edit', 'followups', 'followup-add', 'followup-edit', 'opportunities', 'opportunity-add', 'opportunity-edit', 'quotes', 'quote-add', 'quote-edit', 'orders'], remark: '客户经营与销售协作权限。' },
  { id: 'r3', name: '销售经理', key: 'sales_manager', sort: 3, status: '启用', memberCount: 5, menuIds: ['overview', 'todo', 'todo-add', 'todo-edit', 'leads', 'lead-add', 'lead-edit', 'lead-remove', 'pool', 'pool-claim', 'customers', 'customer-add', 'customer-edit', 'customer-remove', 'contacts', 'contact-add', 'contact-edit', 'followups', 'followup-add', 'followup-edit', 'opportunities', 'opportunity-add', 'opportunity-edit', 'quotes', 'quote-add', 'quote-edit', 'orders', 'order-add', 'order-edit', 'sales-report', 'customer-report', 'product-report', 'report-export'], remark: '管理团队客户与销售目标。' },
  { id: 'r4', name: '客户成功', key: 'customer_success', sort: 4, status: '启用', memberCount: 5, menuIds: ['customers', 'customer-edit', 'contacts', 'contact-edit', 'followups', 'followup-add', 'followup-edit', 'tickets', 'ticket-add', 'ticket-edit', 'visits', 'visit-add', 'visit-edit'], remark: '负责客户服务与回访。' },
  { id: 'r5', name: '只读审计', key: 'auditor', sort: 5, status: '启用', memberCount: 2, menuIds: ['customers', 'sales-report', 'customer-report', 'product-report'], remark: '仅查看业务数据与统计。' },
]

export const employeesSeed: Employee[] = [
  { id: 'e1', name: '顾清和', account: 'admin', departmentId: 'd1', position: '系统管理员', roleIds: ['r1'], phone: '13800001001', email: 'admin@crm.demo', status: '在职', hireDate: '2021-03-08', gender: '男', remark: '平台超级管理员', lastLoginIp: '10.0.1.18' },
  { id: 'e2', name: '林予安', account: 'linyua', departmentId: 'd2', position: '销售总监', roleIds: ['r3'], phone: '13800001002', email: 'lin.yu@crm.demo', status: '在职', hireDate: '2022-05-16', gender: '女', remark: '', lastLoginIp: '10.0.1.25' },
  { id: 'e3', name: '周子墨', account: 'sales', departmentId: 'd3', position: '客户经理', roleIds: ['r2'], phone: '13800001003', email: 'zhou.zi@crm.demo', status: '在职', hireDate: '2024-02-19', gender: '男', remark: '演示销售账号', lastLoginIp: '10.0.2.41' },
  { id: 'e4', name: '陈嘉宁', account: 'chenjn', departmentId: 'd4', position: '高级客户经理', roleIds: ['r2', 'r4'], phone: '13800001004', email: 'chen.jia@crm.demo', status: '在职', hireDate: '2023-09-11', gender: '女', remark: '', lastLoginIp: '10.0.2.58' },
  { id: 'e5', name: '许知夏', account: 'xuzx', departmentId: 'd5', position: '客户成功经理', roleIds: ['r4'], phone: '13800001005', email: 'xu.zhi@crm.demo', status: '在职', hireDate: '2025-01-07', gender: '女', remark: '', lastLoginIp: '10.0.3.12' },
  { id: 'e6', name: '沈星野', account: 'shenxy', departmentId: 'd6', position: '交付经理', roleIds: ['r3'], phone: '13800001006', email: 'shen.xing@crm.demo', status: '在职', hireDate: '2022-12-02', gender: '男', remark: '', lastLoginIp: '10.0.1.72' },
  { id: 'e7', name: '唐念一', account: 'tangny', departmentId: 'd3', position: '销售代表', roleIds: ['r2'], phone: '13800001007', email: 'tang.nian@crm.demo', status: '停用', hireDate: '2023-06-06', gender: '女', remark: '休假期间停用', lastLoginIp: '10.0.2.83' },
  { id: 'e8', name: '宋闻溪', account: 'multirole', departmentId: 'd5', position: '运营专员', roleIds: ['r2', 'r4'], phone: '13800001008', email: 'song.wen@crm.demo', status: '在职', hireDate: '2024-07-15', gender: '女', remark: '多角色演示账号', lastLoginIp: '10.0.3.26' },
  { id: 'e9', name: '陆景行', account: 'lujx', departmentId: 'd7', position: '华北销售经理', roleIds: ['r3'], phone: '13800001009', email: 'lu.jing@crm.demo', status: '在职', hireDate: '2023-03-01', gender: '男', remark: '', lastLoginIp: '10.0.4.11' },
  { id: 'e10', name: '苏婉清', account: 'suwq', departmentId: 'd7', position: '销售代表', roleIds: ['r2'], phone: '13800001010', email: 'su.wan@crm.demo', status: '在职', hireDate: '2024-05-20', gender: '女', remark: '', lastLoginIp: '10.0.4.12' },
  { id: 'e11', name: '顾云舟', account: 'guyz', departmentId: 'd8', position: '西南销售经理', roleIds: ['r3'], phone: '13800001011', email: 'gu.yun@crm.demo', status: '在职', hireDate: '2022-08-15', gender: '男', remark: '', lastLoginIp: '10.0.5.13' },
  { id: 'e12', name: '白若曦', account: 'bairx', departmentId: 'd8', position: '销售代表', roleIds: ['r2'], phone: '13800001012', email: 'bai.ruo@crm.demo', status: '在职', hireDate: '2024-11-03', gender: '女', remark: '', lastLoginIp: '10.0.5.14' },
  { id: 'e13', name: '韩东辰', account: 'handc', departmentId: 'd9', position: '采购经理', roleIds: ['r3'], phone: '13800001013', email: 'han.dong@crm.demo', status: '在职', hireDate: '2022-04-12', gender: '男', remark: '', lastLoginIp: '10.0.6.15' },
  { id: 'e14', name: '秦思远', account: 'qinsy', departmentId: 'd9', position: '采购专员', roleIds: ['r2'], phone: '13800001014', email: 'qin.si@crm.demo', status: '在职', hireDate: '2023-07-21', gender: '男', remark: '', lastLoginIp: '10.0.6.16' },
  { id: 'e15', name: '程亦航', account: 'chengyh', departmentId: 'd10', position: '仓储主管', roleIds: ['r4'], phone: '13800001015', email: 'cheng.yi@crm.demo', status: '在职', hireDate: '2022-10-09', gender: '男', remark: '', lastLoginIp: '10.0.7.17' },
  { id: 'e16', name: '温子衿', account: 'wenzj', departmentId: 'd10', position: '物流专员', roleIds: ['r4'], phone: '13800001016', email: 'wen.zi@crm.demo', status: '在职', hireDate: '2024-01-18', gender: '女', remark: '', lastLoginIp: '10.0.7.18' },
  { id: 'e17', name: '尹曼宁', account: 'yinmn', departmentId: 'd11', position: '财务主管', roleIds: ['r5'], phone: '13800001017', email: 'yin.man@crm.demo', status: '在职', hireDate: '2021-11-30', gender: '女', remark: '', lastLoginIp: '10.0.8.19' },
  { id: 'e18', name: '邵明轩', account: 'shaomx', departmentId: 'd11', position: '财务专员', roleIds: ['r5'], phone: '13800001018', email: 'shao.ming@crm.demo', status: '在职', hireDate: '2023-09-14', gender: '男', remark: '', lastLoginIp: '10.0.8.20' },
]

export const permissionTreeSeed: PermissionNode[] = [
  { id: 'workspace', name: '工作台', type: 'M', permission: 'work:menu', children: [
    { id: 'overview', name: '经营概览', type: 'C', permission: 'work:overview:list' },
    { id: 'todo', name: '我的待办', type: 'C', permission: 'work:todo:list', children: [
      { id: 'todo-add', name: '新增', type: 'F', permission: 'work:todo:add' }, { id: 'todo-edit', name: '编辑', type: 'F', permission: 'work:todo:edit' },
    ] },
  ] },
  { id: 'customer', name: '客户经营', type: 'M', permission: 'crm:menu', children: [
    { id: 'leads', name: '销售线索', type: 'C', permission: 'crm:lead:list', children: [
      { id: 'lead-add', name: '新增', type: 'F', permission: 'crm:lead:add' }, { id: 'lead-edit', name: '编辑', type: 'F', permission: 'crm:lead:edit' }, { id: 'lead-remove', name: '删除', type: 'F', permission: 'crm:lead:remove' },
    ] },
    { id: 'pool', name: '客户公海', type: 'C', permission: 'crm:pool:list', children: [
      { id: 'pool-claim', name: '认领/分配', type: 'F', permission: 'crm:pool:edit' },
    ] },
    { id: 'customers', name: '客户档案', type: 'C', permission: 'crm:customer:list', children: [
      { id: 'customer-add', name: '新增', type: 'F', permission: 'crm:customer:add' }, { id: 'customer-edit', name: '编辑', type: 'F', permission: 'crm:customer:edit' }, { id: 'customer-remove', name: '删除', type: 'F', permission: 'crm:customer:remove' },
    ] },
    { id: 'contacts', name: '联系人', type: 'C', permission: 'crm:contact:list', children: [
      { id: 'contact-add', name: '新增', type: 'F', permission: 'crm:contact:add' }, { id: 'contact-edit', name: '编辑', type: 'F', permission: 'crm:contact:edit' }, { id: 'contact-remove', name: '删除', type: 'F', permission: 'crm:contact:remove' },
    ] },
    { id: 'followups', name: '跟进记录', type: 'C', permission: 'crm:followup:list', children: [
      { id: 'followup-add', name: '新增', type: 'F', permission: 'crm:followup:add' }, { id: 'followup-edit', name: '编辑', type: 'F', permission: 'crm:followup:edit' }, { id: 'followup-remove', name: '删除', type: 'F', permission: 'crm:followup:remove' },
    ] },
    { id: 'opportunities', name: '销售商机', type: 'C', permission: 'crm:opportunity:list', children: [
      { id: 'opportunity-add', name: '新增', type: 'F', permission: 'crm:opportunity:add' }, { id: 'opportunity-edit', name: '编辑/阶段推进', type: 'F', permission: 'crm:opportunity:edit' }, { id: 'opportunity-remove', name: '删除', type: 'F', permission: 'crm:opportunity:remove' },
    ] },
  ] },
  { id: 'products', name: '产品管理', type: 'M', permission: 'product:menu', children: [
    { id: 'categories', name: '产品分类', type: 'C', permission: 'product:category:list', children: [
      { id: 'category-add', name: '新增', type: 'F', permission: 'product:category:add' }, { id: 'category-edit', name: '编辑', type: 'F', permission: 'product:category:edit' }, { id: 'category-remove', name: '删除', type: 'F', permission: 'product:category:remove' },
    ] },
    { id: 'product-list', name: '产品档案', type: 'C', permission: 'product:product:list', children: [
      { id: 'product-add', name: '新增', type: 'F', permission: 'product:product:add' }, { id: 'product-edit', name: '编辑', type: 'F', permission: 'product:product:edit' }, { id: 'product-remove', name: '删除', type: 'F', permission: 'product:product:remove' },
    ] },
    { id: 'prices', name: '客户价格', type: 'C', permission: 'product:price:list', children: [
      { id: 'price-add', name: '新增', type: 'F', permission: 'product:price:add' }, { id: 'price-edit', name: '编辑', type: 'F', permission: 'product:price:edit' }, { id: 'price-remove', name: '删除', type: 'F', permission: 'product:price:remove' },
    ] },
  ] },
  { id: 'sales', name: '销售协作', type: 'M', permission: 'sales:menu', children: [
    { id: 'quotes', name: '报价单', type: 'C', permission: 'sales:quote:list', children: [
      { id: 'quote-add', name: '新增', type: 'F', permission: 'sales:quote:add' }, { id: 'quote-edit', name: '编辑/审核', type: 'F', permission: 'sales:quote:edit' }, { id: 'quote-remove', name: '删除', type: 'F', permission: 'sales:quote:remove' },
    ] },
    { id: 'orders', name: '销售订单', type: 'C', permission: 'sales:order:list', children: [
      { id: 'order-add', name: '新增', type: 'F', permission: 'sales:order:add' }, { id: 'order-edit', name: '编辑/履约', type: 'F', permission: 'sales:order:edit' }, { id: 'order-remove', name: '删除', type: 'F', permission: 'sales:order:remove' },
    ] },
    { id: 'returns', name: '退换货单', type: 'C', permission: 'sales:return:list', children: [
      { id: 'return-add', name: '新增', type: 'F', permission: 'sales:return:add' }, { id: 'return-edit', name: '编辑', type: 'F', permission: 'sales:return:edit' }, { id: 'return-remove', name: '删除', type: 'F', permission: 'sales:return:remove' },
    ] },
  ] },
  { id: 'delivery', name: '交付与回款', type: 'M', permission: 'delivery:menu', children: [
    { id: 'shipments', name: '发货记录', type: 'C', permission: 'delivery:shipment:list', children: [
      { id: 'shipment-add', name: '新增', type: 'F', permission: 'delivery:shipment:add' }, { id: 'shipment-edit', name: '编辑', type: 'F', permission: 'delivery:shipment:edit' },
    ] },
    { id: 'payments', name: '收款记录', type: 'C', permission: 'delivery:payment:list', children: [
      { id: 'payment-add', name: '新增', type: 'F', permission: 'delivery:payment:add' }, { id: 'payment-edit', name: '编辑/核销', type: 'F', permission: 'delivery:payment:edit' },
    ] },
    { id: 'receivables', name: '应收账款', type: 'C', permission: 'delivery:receivable:list', children: [
      { id: 'receivable-edit', name: '编辑/催收', type: 'F', permission: 'delivery:receivable:edit' },
    ] },
  ] },
  { id: 'service', name: '售后管理', type: 'M', permission: 'service:menu', children: [
    { id: 'tickets', name: '售后工单', type: 'C', permission: 'service:ticket:list', children: [
      { id: 'ticket-add', name: '新增', type: 'F', permission: 'service:ticket:add' }, { id: 'ticket-edit', name: '处理', type: 'F', permission: 'service:ticket:edit' },
    ] },
    { id: 'visits', name: '客户回访', type: 'C', permission: 'service:visit:list', children: [
      { id: 'visit-add', name: '新增', type: 'F', permission: 'service:visit:add' }, { id: 'visit-edit', name: '编辑', type: 'F', permission: 'service:visit:edit' },
    ] },
  ] },
  { id: 'reports', name: '数据统计', type: 'M', permission: 'report:menu', children: [
    { id: 'sales-report', name: '销售统计', type: 'C', permission: 'report:sales:list' }, { id: 'customer-report', name: '客户采购分析', type: 'C', permission: 'report:customer:list' }, { id: 'product-report', name: '产品销售分析', type: 'C', permission: 'report:product:list' }, { id: 'report-export', name: '导出报表', type: 'F', permission: 'report:export' },
  ] },
  { id: 'system', name: '系统管理', type: 'M', permission: 'system:menu', children: [
    { id: 'employee', name: '员工管理', type: 'C', permission: 'system:user:list', children: [
      { id: 'user-add', name: '新增', type: 'F', permission: 'system:user:add' }, { id: 'user-edit', name: '编辑', type: 'F', permission: 'system:user:edit' }, { id: 'user-remove', name: '删除', type: 'F', permission: 'system:user:remove' },
    ] },
    { id: 'department', name: '部门架构', type: 'C', permission: 'system:dept:list', children: [
      { id: 'dept-add', name: '新增', type: 'F', permission: 'system:dept:add' }, { id: 'dept-edit', name: '编辑', type: 'F', permission: 'system:dept:edit' }, { id: 'dept-remove', name: '删除', type: 'F', permission: 'system:dept:remove' },
    ] },
    { id: 'role', name: '角色权限', type: 'C', permission: 'system:role:list', children: [
      { id: 'role-add', name: '新增', type: 'F', permission: 'system:role:add' }, { id: 'role-edit', name: '编辑', type: 'F', permission: 'system:role:edit' }, { id: 'role-assign', name: '授权', type: 'F', permission: 'system:role:assign' }, { id: 'role-remove', name: '删除', type: 'F', permission: 'system:role:remove' },
    ] },
    { id: 'global-data-scope', name: '跨部门全局数据权限', type: 'F', permission: 'system:data:all' },
  ] },
]
