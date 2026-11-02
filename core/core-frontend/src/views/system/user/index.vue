<template>
  <div class="rbac-page">
    <div class="toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索账号或姓名" @keyup.enter="loadUsers" />
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>
    <el-table :data="users" v-loading="loading">
      <el-table-column prop="account" label="账号" min-width="140" />
      <el-table-column prop="name" label="姓名" min-width="140" />
      <el-table-column label="角色" min-width="220">
        <template #default="scope">
          <el-tag v-for="role in scope.row.roleItems" :key="role.id" class="role-tag">{{
            role.name
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-switch v-model="scope.row.enable" @change="changeEnabled(scope.row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button link type="danger" @click="removeUser(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      class="pagination"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="loadUsers"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新建用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="account"><el-input v-model="form.account" /></el-form-item>
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'" :prop="form.id ? undefined : 'password'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改' : ''"
          />
        </el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="角色"
          ><el-select v-model="form.roleIds" multiple clearable style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            /> </el-select
        ></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enable" /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" @click="saveUser">保存</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus-secondary'
import {
  queryFormApi,
  searchRoleApi,
  switchEnableApi,
  userCreateApi,
  userDelApi,
  userEditApi,
  userPageApi
} from '@/api/user'
import { rsaEncryp } from '@/utils/encryption'

const users = ref<any[]>([])
const roles = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({
  id: null,
  account: '',
  name: '',
  password: '',
  email: '',
  roleIds: [],
  enable: true
})
const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await userPageApi(page.value, pageSize.value, { keyword: keyword.value })
    users.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}
const loadRoles = async () => {
  const res = await searchRoleApi('')
  roles.value = res.data || []
}
const resetForm = () =>
  Object.assign(form, {
    id: null,
    account: '',
    name: '',
    password: '',
    email: '',
    roleIds: [],
    enable: true
  })
const openCreate = async () => {
  resetForm()
  await loadRoles()
  dialogVisible.value = true
}
const openEdit = async id => {
  resetForm()
  await loadRoles()
  const res = await queryFormApi(id)
  Object.assign(form, res.data, { password: '', roleIds: res.data?.roleIds || [] })
  dialogVisible.value = true
}
const saveUser = async () => {
  await formRef.value?.validate()
  const payload = { ...form, password: form.password ? rsaEncryp(form.password) : undefined }
  if (form.id) await userEditApi(payload)
  else await userCreateApi(payload)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await loadUsers()
}
const changeEnabled = async row => {
  try {
    await switchEnableApi({ id: row.id, enable: row.enable })
    ElMessage.success('状态已更新')
  } catch (error) {
    row.enable = !row.enable
    throw error
  }
}
const removeUser = async row => {
  await ElMessageBox.confirm(`确定删除用户“${row.name}”吗？`, '提示', { type: 'warning' })
  await userDelApi(row.id)
  ElMessage.success('删除成功')
  await loadUsers()
}

onMounted(loadUsers)
</script>

<style lang="less" scoped>
.rbac-page {
  padding: 24px;
  background: #fff;
  height: 100%;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar .ed-input {
  width: 280px;
}
.role-tag {
  margin-right: 6px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
