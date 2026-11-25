<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Asn1Node } from '../types'

const props = withDefaults(
  defineProps<{
    node: Asn1Node
    depth?: number
    defaultExpanded?: boolean
  }>(),
  { depth: 0, defaultExpanded: true },
)

const expanded = ref(props.defaultExpanded)

const hasChildren = computed(() => props.node.children.length > 0)
const classText = computed(
  () => `${props.node.tagClass === 'UNIVERSAL' ? '' : props.node.tagClass + ' '}${props.node.tagNumber}`,
)

function toggle() {
  if (hasChildren.value) expanded.value = !expanded.value
}
</script>

<template>
  <div class="asn1-node">
    <div class="asn1-row" :style="{ paddingLeft: `${depth * 18 + 6}px` }" @click="toggle">
      <span v-if="hasChildren" :class="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" class="chevron" />
      <span v-else class="chevron placeholder">·</span>
      <span class="asn1-tag">{{ classText }}</span>
      <span v-if="node.tagName" class="asn1-name">{{ node.tagName }}</span>
      <span v-if="node.value" class="asn1-value">{{ node.value }}</span>
      <span v-else-if="!hasChildren" class="text-faint">(empty)</span>
      <span class="asn1-meta">
        <template v-if="node.offset >= 0">off {{ node.offset }}</template>
        <template v-if="node.length >= 0"> · len {{ node.length }}</template>
      </span>
    </div>
    <div v-if="hasChildren && expanded" class="asn1-children">
      <Asn1Tree
        v-for="(child, i) in node.children"
        :key="i"
        :node="child"
        :depth="depth + 1"
        :default-expanded="defaultExpanded"
      />
    </div>
  </div>
</template>

<style scoped>
.asn1-node {
  font-family: var(--dt-font-mono);
  font-size: 11px;
}

.asn1-row {
  display: flex;
  align-items: baseline;
  gap: 7px;
  padding: 2px 8px;
  cursor: default;
  white-space: nowrap;
}

.asn1-row:hover {
  background: var(--dt-bg-hover);
}

.chevron {
  font-size: 9px;
  color: var(--dt-text-muted);
  width: 11px;
  flex-shrink: 0;
  display: inline-block;
}

.chevron.placeholder {
  color: transparent;
}

.asn1-tag {
  color: var(--dt-text-faint);
  flex-shrink: 0;
}

.asn1-name {
  color: var(--dt-blue);
  font-weight: 600;
  flex-shrink: 0;
}

.asn1-value {
  color: var(--dt-green);
  overflow: hidden;
  text-overflow: ellipsis;
}

.asn1-meta {
  color: var(--dt-text-faint);
  margin-left: auto;
  flex-shrink: 0;
}

.asn1-children {
  border-left: 1px solid var(--dt-border);
  margin-left: 14px;
}
</style>
