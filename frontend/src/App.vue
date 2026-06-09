<template>
  <el-container class="app-container">
    <el-aside width="220px" class="app-aside">
      <div class="logo-section">
        <h2>🚌 公交预报系统</h2>
      </div>
      <el-menu
        :default-active="currentRoute"
        router
        background-color="#1d1e2c"
        text-color="#a0a3bd"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <span>实时监控</span>
        </el-menu-item>
        <el-menu-item index="/vehicle">
          <el-icon><Location /></el-icon>
          <span>车辆监控</span>
        </el-menu-item>
        <el-menu-item index="/prediction">
          <el-icon><Timer /></el-icon>
          <span>到站预测</span>
        </el-menu-item>
        <el-menu-item index="/traffic">
          <el-icon><DataLine /></el-icon>
          <span>路况热力图</span>
        </el-menu-item>
        <el-sub-menu index="route-mgmt">
          <template #title>
            <el-icon><Guide /></el-icon>
            <span>线路管理</span>
          </template>
          <el-menu-item index="/line">线路管理</el-menu-item>
          <el-menu-item index="/station">站点管理</el-menu-item>
          <el-menu-item index="/schedule">排班管理</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/stopboard">
          <el-icon><Grid /></el-icon>
          <span>电子站牌</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor, Location, Timer, DataLine, Guide, Grid } from '@element-plus/icons-vue'

const route = useRoute()
const currentRoute = computed(() => route.path)
</script>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.app-container {
  height: 100vh;
}

.app-aside {
  background: #1d1e2c;
  overflow-y: auto;
}

.logo-section {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid #2d2e3f;
}

.logo-section h2 {
  color: #e0e3ff;
  font-size: 18px;
  margin: 0;
}

.app-main {
  background: #f0f2f5;
  overflow-y: auto;
  padding: 20px;
}

.el-menu {
  border-right: none !important;
}
</style>
