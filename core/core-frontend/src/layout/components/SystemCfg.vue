<script lang="ts" setup>
import iconSetting from '@/assets/svg/icon_organization_outlined.svg'
import { useRouter } from 'vue-router_2'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { computed } from 'vue'

const appearanceStore = useAppearanceStoreWithOut()
const navigateBg = computed(() => appearanceStore.getNavigateBg)
const showDoc = computed(() => appearanceStore.getShowDoc)
const { push, resolve } = useRouter()
const targetMenu = computed(() => {
  const organizationMenu = resolve('/system')
  if (organizationMenu.matched.length) {
    return organizationMenu
  }
  return resolve('/sys-setting')
})
const isSystemSetting = computed(() => targetMenu.value.path === '/sys-setting')
const redirectUser = () => {
  const sysMenu = targetMenu.value
  const kidPath = sysMenu.matched[0]?.children[0]?.path
  if (kidPath) {
    push(`${sysMenu.path}/${kidPath}`)
  }
}
</script>

<template>
  <el-tooltip
    offset="10"
    class="box-item"
    effect="dark"
    :content="isSystemSetting ? $t('commons.system_setting') : $t('toolbox.org_center')"
    placement="top"
  >
    <div
      class="sys-setting"
      :class="{
        'is-light-setting': navigateBg && navigateBg === 'light',
        'in-iframe-setting': !showDoc
      }"
    >
      <el-icon @click="redirectUser">
        <Icon class="icon-setting" name="icon-setting"
          ><iconSetting class="svg-icon icon-setting"
        /></Icon>
      </el-icon>
    </div>
  </el-tooltip>
</template>

<style lang="less" scoped>
.sys-setting {
  margin: 0 10px 0 0;
  padding: 5px;
  height: 28px;
  width: 28px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  &:hover {
    background-color: #1e2738;
  }
}
.in-iframe-setting {
  margin-left: 10px !important;
}
.is-light-setting {
  &:hover {
    background-color: #1f23291a !important;
  }
}
</style>
