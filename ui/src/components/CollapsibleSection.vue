<script setup lang="ts">
import { ref } from 'vue'

withDefaults(
  defineProps<{
    title: string
    defaultOpen?: boolean
  }>(),
  { defaultOpen: false },
)

const open = ref(false)
</script>

<template>
  <section class="collapsible">
    <button class="collapse-header" @click="open = !open">
      <i :class="open ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" />
      <span class="collapse-title">{{ title }}</span>
      <span class="toolbar-spacer" />
      <slot name="badges" />
    </button>
    <div v-show="open" class="collapse-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.collapsible {
  border: 1px solid var(--dt-border);
  border-radius: 6px;
  background: var(--dt-bg-panel);
  overflow: hidden;
}

.collapse-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  background: var(--dt-bg-elevated);
  border: none;
  color: var(--dt-text);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  text-align: left;
}

.collapse-header:hover {
  background: var(--dt-bg-hover);
}

.collapse-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-spacer {
  flex: 1;
}

.collapse-body {
  padding: 4px 0;
}
</style>
