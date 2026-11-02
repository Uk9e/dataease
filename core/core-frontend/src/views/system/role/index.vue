<template>
  <div class="role-page">
    <div class="role-list">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索角色"
          @keyup.enter="loadRoles"
        /><el-button type="primary" @click="openCreate">新建角色</el-button>
      </div>
      <el-table :data="roles" highlight-current-row @current-change="selectRole">
        <el-table-column prop="name" label="角色" min-width="160" />
        <el-table-column label="类型" width="100"
          ><template #default="scope">{{
            scope.row.root ? '系统' : '普通'
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="190"
          ><template #default="scope">
            <el-button
              link
              type="primary"
              :disabled="scope.row.readonly"
              @click.stop="openEdit(scope.row)"
              >编辑</el-button
            >
            <el-button link type="primary" @click.stop="openMembers(scope.row)">成员</el-button>
            <el-button
              link
              type="danger"
              :disabled="scope.row.readonly"
              @click.stop="removeRole(scope.row)"
              >删除</el-button
            >
          </template></el-table-column
        >
      </el-table>
    </div>

    <div class="permission-panel">
      <template v-if="selectedRole">
        <div class="panel-title">
          <span>{{ selectedRole.name }} 的菜单权限</span
          ><el-radio-group v-model="permissionWeight" :disabled="selectedRole.readonly"
            ><el-radio-button :label="1">查看</el-radio-button
            ><el-radio-button :label="2">管理</el-radio-button></el-radio-group
          >
        </div>
        <el-tree
          ref="menuTreeRef"
          :data="menus"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
          :disabled="selectedRole.readonly"
        />
        <el-button
          class="save-permission"
          type="primary"
          :disabled="selectedRole.readonly"
          @click="savePermissions"
          >保存权限</el-button
        >
      </template>
      <el-empty v-else description="请选择角色" />
    </div>

    <el-dialog
      v-model="roleDialogVisible"
      :title="roleForm.id ? '编辑角色' : '新建角色'"
      width="480px"
    >
      <el-form
        ref="roleFormRef"
        :model="roleForm"
        :rules="{ name: [{ required: true, message: '请输入角色名称' }] }"
        label-width="80px"
      >
        <el-form-item label="名称" prop="name"><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="描述"
          ><el-input v-model="roleForm.desc" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="roleDialogVisible = false">取消</el-button
        ><el-button type="primary" @click="saveRole">保存</el-button></template
      >
    </el-dialog>

    <el-dialog v-model="memberDialogVisible" title="角色成员" width="520px">
      <el-select v-model="selectedMemberIds" multiple filterable style="width: 100%">
        <el-option
          v-for="user in memberOptions"
          :key="user.id"
          :label="`${user.name} (${user.account})`"
          :value="user.id"
        />
      </el-select>
      <template #footer
        ><el-button @click="memberDialogVisible = false">取消</el-button
        ><el-button type="primary" @click="saveMembers">保存</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus-secondary'
import {
  mountUserApi,
  roleCreateApi,
  roleDelApi,
  roleDetailApi,
  roleEditApi,
  searchRoleApi,
  unMountUserApi,
  userOptionForRoleApi,
  userSelectedForRoleApi
} from '@/api/user'
import { menuPerApi, menuPerSaveApi, menuTreeApi } from '@/api/auth'

const roles = ref<any[]>([])
const menus = ref<any[]>([])
const keyword = ref('')
const selectedRole = ref<any>()
const permissionWeight = ref(1)
const menuTreeRef = ref<any>()
const roleDialogVisible = ref(false)
const roleFormRef = ref<FormInstance>()
const roleForm = reactive<any>({ id: null, name: '', desc: '' })
const memberDialogVisible = ref(false)
const memberOptions = ref<any[]>([])
const selectedMemberIds = ref<any[]>([])
const originalMemberIds = ref<any[]>([])
const memberRoleId = ref<any>()

const loadRoles = async () => {
  const res = await searchRoleApi(keyword.value)
  roles.value = res.data || []
}
const selectRole = async role => {
  if (!role) return
  selectedRole.value = role
  const [treeRes, permissionRes] = await Promise.all([menuTreeApi(), menuPerApi({ id: role.id })])
  menus.value = treeRes.data || []
  const permissions = permissionRes.data?.permissions || []
  permissionWeight.value = permissions.some(item => item.weight === 2) ? 2 : 1
  setTimeout(() => menuTreeRef.value?.setCheckedKeys(permissions.map(item => item.id)), 0)
}
const openCreate = () => {
  Object.assign(roleForm, { id: null, name: '', desc: '' })
  roleDialogVisible.value = true
}
const openEdit = async role => {
  const res = await roleDetailApi(role.id)
  Object.assign(roleForm, res.data)
  roleDialogVisible.value = true
}
const saveRole = async () => {
  await roleFormRef.value?.validate()
  if (roleForm.id) await roleEditApi(roleForm)
  else await roleCreateApi({ ...roleForm, typeCode: 0 })
  roleDialogVisible.value = false
  ElMessage.success('保存成功')
  await loadRoles()
}
const removeRole = async role => {
  await ElMessageBox.confirm(`确定删除角色“${role.name}”吗？`, '提示', { type: 'warning' })
  await roleDelApi(role.id)
  if (selectedRole.value?.id === role.id) selectedRole.value = undefined
  ElMessage.success('删除成功')
  await loadRoles()
}
const savePermissions = async () => {
  const ids = (menuTreeRef.value?.getCheckedKeys(false) || []) as Array<string | number>
  await menuPerSaveApi({
    id: selectedRole.value.id,
    permissions: ids.map(id => ({ id, weight: permissionWeight.value }))
  })
  ElMessage.success('权限已保存，受影响用户需要重新登录')
}
const openMembers = async role => {
  memberRoleId.value = role.id
  const [optionRes, selectedRes] = await Promise.all([
    userOptionForRoleApi({ rid: role.id }),
    userSelectedForRoleApi(1, 200, { rid: role.id })
  ])
  const selected = selectedRes.data?.records || []
  memberOptions.value = [...selected, ...(optionRes.data || [])]
  originalMemberIds.value = selected.map(item => item.id)
  selectedMemberIds.value = [...originalMemberIds.value]
  memberDialogVisible.value = true
}
const saveMembers = async () => {
  const added = selectedMemberIds.value.filter(id => !originalMemberIds.value.includes(id))
  const removed = originalMemberIds.value.filter(id => !selectedMemberIds.value.includes(id))
  if (added.length) await mountUserApi({ rid: memberRoleId.value, uids: added })
  await Promise.all(removed.map(uid => unMountUserApi({ rid: memberRoleId.value, uid })))
  memberDialogVisible.value = false
  ElMessage.success('成员已更新，受影响用户需要重新登录')
}

onMounted(loadRoles)
</script>

<style lang="less" scoped>
.role-page {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(360px, 1fr);
  gap: 16px;
  padding: 24px;
  height: 100%;
}
.role-list,
.permission-panel {
  background: #fff;
  padding: 20px;
  border-radius: 6px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-weight: 500;
}
.save-permission {
  margin-top: 20px;
}
</style>
