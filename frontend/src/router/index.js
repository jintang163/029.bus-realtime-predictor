import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '实时监控', icon: 'Monitor' }
  },
  {
    path: '/vehicle',
    name: 'VehicleMonitor',
    component: () => import('../views/VehicleMonitor.vue'),
    meta: { title: '车辆监控', icon: 'Location' }
  },
  {
    path: '/prediction',
    name: 'Prediction',
    component: () => import('../views/Prediction.vue'),
    meta: { title: '到站预测', icon: 'Timer' }
  },
  {
    path: '/line',
    name: 'LineManagement',
    component: () => import('../views/LineManagement.vue'),
    meta: { title: '线路管理', icon: 'Guide' }
  },
  {
    path: '/station',
    name: 'StationManagement',
    component: () => import('../views/StationManagement.vue'),
    meta: { title: '站点管理', icon: 'MapLocation' }
  },
  {
    path: '/schedule',
    name: 'ScheduleManagement',
    component: () => import('../views/ScheduleManagement.vue'),
    meta: { title: '排班管理', icon: 'Calendar' }
  },
  {
    path: '/stopboard',
    name: 'StopBoard',
    component: () => import('../views/StopBoard.vue'),
    meta: { title: '电子站牌', icon: 'Monitor' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
