<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'

const sidebarCollapsed = ref(false)
</script>

<template>
  <div class="app-shell">
    <Sidebar :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = !sidebarCollapsed" />
    <div class="main-shell">
      <Topbar />
      <main class="main-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  height: 100%;
  display: flex;
  overflow: hidden;
}
.main-shell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.main-content {
  flex: 1;
  overflow: auto;
}
</style>
